package app.softwork.ratelimit

import io.ktor.server.application.*
import io.ktor.util.*
import kotlin.time.*
import kotlin.time.Duration.Companion.hours

/**
 * Configuration holder for the [RateLimit] feature.
 */
@KtorDsl
public class Configuration(public val storage: Storage) {
    /**
     * Override the handler to return the unique host information from a [ApplicationCall].
     * By default, the [RequestConnectionPoint.remoteHost][io.ktor.http.RequestConnectionPoint.remoteHost] is used.
     */
    public fun host(block: (ApplicationCall) -> String) {
        host = block
    }

    private var host: (ApplicationCall) -> String = { it.request.local.remoteHost }

    /**
     * Override the handler to always allow a [host]. Returns true, if the [host] should be allowed.
     * Default value is false.
     */
    public fun alwaysAllow(block: (String) -> Boolean) {
        alwaysAllow = block
    }

    private var alwaysAllow: (String) -> Boolean = { false }

    /**
     * Override the handler to always block a [host]. Returns true, if the [host] should be blocked.
     * Default value is false.
     */
    public fun alwaysBlock(block: (String) -> Boolean) {
        alwaysBlock = block
    }

    private var alwaysBlock: (String) -> Boolean = { false }

    /**
     * The number of allowed requests until the request will be blocked.
     * Default value is 1000.
     */
    public var limit: Int = 1000

    /**
     * The duration until a blocked request will be allowed.
     * Default value is 1 hour.
     */
    public var timeout: Duration = 1.hours

    /**
     * Overrides the handler to skip a [call] from the rate limit check. Return [SkipResult.SkipRateLimit], if the [call] should be skipped.
     * Default value is [SkipResult.ExecuteRateLimit], every [call] will be checked.
     */
    public fun skip(block: (ApplicationCall) -> SkipResult) {
        skip = block
    }

    private var skip: (ApplicationCall) -> SkipResult = { SkipResult.ExecuteRateLimit }

    /**
     * Add the [HttpHeaders.RetryAfter][io.ktor.http.HttpHeaders.RetryAfter] header to the response, if the [host] is blocked by the rate limit check or by the [alwaysBlock] function.
     * Default value is true.
     */
    public var sendRetryAfterHeader: Boolean = true

    /** 
     * When true, this plugin does not check, if CORS is appplied before this plugin to prevent limiting CORS requests.
     */
    public var ignoreCORSInstallationCheck: Boolean = false

    /**
     * Build a non mutating copy
     */
    internal fun build() = RateLimit(
        storage = storage,
        host = host,
        alwaysAllow = alwaysAllow,
        alwaysBlock = alwaysBlock,
        limit = limit,
        timeout = timeout,
        skip = skip,
        sendRetryAfterHeader = sendRetryAfterHeader,
        ignoreCORSCheck = ignoreCORSInstallationCheck
    )
}
