package app.softwork.ratelimit

import app.softwork.ratelimit.RateLimit.RequestResult.*
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.util.*
import kotlin.time.*

@ExperimentalTime
public class RateLimit(public val configuration: Configuration) {
    public sealed class RequestResult {
        public object Allow : RequestResult()
        public data class Forbid(public val retryAfter: Duration) : RequestResult()
    }

    public suspend fun isAllowed(host: String): RequestResult {
        if (configuration.alwaysAllow(host)) {
            return Allow
        }
        if (configuration.alwaysBlock(host)) {
            return Allow
        }

        val storage = configuration.storage
        val previous = storage.getOrNull(host)
        if (previous == null) {
            storage.set(host, Storage.Requested(trial = 1, lastRequest = storage.timeSource.markNow()))
            return Allow
        }
        if (previous.trial < configuration.limit) {
            storage.set(host, previous.copy(trial = previous.trial + 1, lastRequest = storage.timeSource.markNow()))
            return Allow
        }
        val currentTimeout = previous.lastRequest.elapsedNow()
        if (currentTimeout > configuration.timeout) {
            storage.remove(host)
            return Allow
        }
        val retryAfter = configuration.timeout - currentTimeout
        return Forbid(retryAfter)
    }

    public class Configuration {
        public fun host(block: (ApplicationCall) -> String) {
            host = block
        }

        public var host: (ApplicationCall) -> String = { it.request.local.remoteHost }

        public fun alwaysAllow(block: (String) -> Boolean) {
            alwaysAllow = block
        }

        public var alwaysAllow: (String) -> Boolean = { false }

        public fun alwaysBlock(block: (String) -> Boolean) {
            alwaysBlock = block
        }

        public var alwaysBlock: (String) -> Boolean = { false }

        public var storage: Storage = InMemory()
        public var limit: Int = 1000
        public var timeout: Duration = Duration.hours(1)

        public fun skip(block: (ApplicationCall) -> Boolean) {
            skip = block
        }

        public var skip: (ApplicationCall) -> Boolean = { false }

        public var sendRetryAfterHeader: Boolean = true
    }

    public companion object Feature : ApplicationFeature<Application, Configuration, RateLimit> {
        override val key: AttributeKey<RateLimit> = AttributeKey("RateLimit")

        override fun install(pipeline: Application, configure: Configuration.() -> Unit): RateLimit {
            val feature = RateLimit(Configuration().apply(configure))

            pipeline.intercept(ApplicationCallPipeline.Features) {
                val host = feature.configuration.host(call)
                if (feature.configuration.skip(call)) {
                    proceed()
                    return@intercept
                }
                when (val isAllowed = feature.isAllowed(host)) {
                    is Allow -> {
                        proceed()
                    }
                    is Forbid -> {
                        finish()
                        if (feature.configuration.sendRetryAfterHeader) {
                            call.response.header(HttpHeaders.RetryAfter, isAllowed.retryAfter.inSeconds.toLong())
                        }
                        call.respond(HttpStatusCode.TooManyRequests)
                    }
                }
            }

            return feature
        }
    }
}
