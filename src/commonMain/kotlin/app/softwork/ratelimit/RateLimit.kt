package app.softwork.ratelimit

import app.softwork.ratelimit.SkipResult.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.*
import io.ktor.server.response.*
import io.ktor.util.*
import kotlin.time.*

/**
 * Rate limit feature to block a request if a host has requested the server too often.
 *
 * @param storage containing the persistence rate limit hits.
 */
@KtorDsl
public fun RateLimit(storage: Storage): RouteScopedPlugin<Configuration> = createRouteScopedPlugin(
    name = "RateLimit",
    createConfiguration = { Configuration(storage) }
) {
    val rateLimit = pluginConfig.build()

    if (!rateLimit.ignoreCORSCheck) {
        checkNotNull(application.pluginOrNull(CORS)) {
            "Please install CORS before this plugin to prevent limiting CORS request " +
                    "or suppress this check with ignoreCORSInstallationCheck = true."
        }
    }

    onCall { call ->
        val host = rateLimit.host(call)
        if (rateLimit.skip(call) == SkipRateLimit) {
            return@onCall
        }
        when (val isAllowed = rateLimit.isAllowed(host)) {
            is RequestResult.Allow -> {
                return@onCall
            }
            is RequestResult.Block -> {
                if (rateLimit.sendRetryAfterHeader) {
                    call.response.header(HttpHeaders.RetryAfter, isAllowed.retryAfter.inWholeSeconds)
                }
                call.respond(HttpStatusCode.TooManyRequests)
            }
        }
    }
}

/**
 * Non mutating config
 */
internal data class RateLimit(
    val storage: Storage,
    val host: (ApplicationCall) -> String,
    val alwaysAllow: (String) -> Boolean,
    val alwaysBlock: (String) -> Boolean,
    val limit: Int,
    val timeout: Duration,
    val skip: (ApplicationCall) -> SkipResult,
    val sendRetryAfterHeader: Boolean,
    val ignoreCORSCheck: Boolean
) {
    /**
     * Check if a [host] is allowed to request the requested resource.
     */
    internal suspend fun isAllowed(host: String): RequestResult {
        if (alwaysAllow(host)) {
            return RequestResult.Allow
        }
        if (alwaysBlock(host)) {
            return RequestResult.Block(timeout)
        }

        val previous = storage.getOrNull(host)
        if (previous == null) {
            storage.set(host, trial = 1, lastRequest = storage.clock.now())
            return RequestResult.Allow
        }
        if (previous.trial < limit) {
            storage.set(host, trial = previous.trial + 1, lastRequest = storage.clock.now())
            return RequestResult.Allow
        }
        val now = storage.clock.now()
        val allowedAfter = previous.lastRequest + timeout
        if (now >= allowedAfter) {
            storage.remove(host)
            return RequestResult.Allow
        }
        val retryAfter = allowedAfter - now
        return RequestResult.Block(retryAfter)
    }
}
