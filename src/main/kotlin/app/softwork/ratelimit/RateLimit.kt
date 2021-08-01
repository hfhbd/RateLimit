package app.softwork.ratelimit

import app.softwork.ratelimit.RateLimit.RequestResult.*
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import kotlin.time.*

/**
 * Rate limit feature to block a request if a host has requested the server too often.
 *
 * @param configuration configure the behavior how and if a call should be allowed or blocked.
 */
@ExperimentalTime
public class RateLimit(public val configuration: Configuration) {
    /**
     * The result if a call should be allowed or blocked.
     */
    public sealed class RequestResult {
        /**
         * The request was allowed.
         */
        public object Allow : RequestResult()

        /**
         * The request was blocked.
         * @param retryAfter if [Configuration.sendRetryAfterHeader] is true, this duration is sent back as in the header `RetryAfter`.
         */
        public data class Block(public val retryAfter: Duration) : RequestResult()
    }

    /**
     * Result of [Configuration.skip] to skip the rate limit for this host on [SkipRateLimit] or
     * execute [RateLimit.isAllowed] on [ExecuteRateLimit]
     */
    public enum class SkipResult {
        SkipRateLimit, ExecuteRateLimit
    }

    /**
     * Check using the [configuration] if a [host] is allowed to request the requested resource.
     */
    public suspend fun isAllowed(host: String): RequestResult {
        if (configuration.alwaysAllow(host)) {
            return Allow
        }
        if (configuration.alwaysBlock(host)) {
            return Block(configuration.timeout)
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
        return Block(retryAfter)
    }

    /**
     * Configuration holder for the [RateLimit] feature.
     */
    public class Configuration {
        /**
         * Override the handler to return the unique host information from a [ApplicationCall].
         * By default, the [RequestConnectionPoint.remoteHost] is used.
         */
        public fun host(block: (ApplicationCall) -> String) {
            host = block
        }

        internal var host: (ApplicationCall) -> String = { it.request.local.remoteHost }

        /**
         * Override the handler to always allow a [host]. Returns true, if the [host] should be allowed.
         * Default value is false.
         */
        public fun alwaysAllow(block: (String) -> Boolean) {
            alwaysAllow = block
        }

        internal var alwaysAllow: (String) -> Boolean = { false }

        /**
         * Override the handler to always block a [host]. Returns true, if the [host] should be blocked.
         * Default value is false.
         */
        public fun alwaysBlock(block: (String) -> Boolean) {
            alwaysBlock = block
        }

        internal var alwaysBlock: (String) -> Boolean = { false }

        /**
         * The storage provider to persist the request information.
         * By default, an [InMemory] implementation is used.
         */
        public var storage: Storage = InMemory()

        /**
         * The number of allowed requests until the request will be blocked.
         * Default value is 1000.
         */
        public var limit: Int = 1000

        /**
         * The duration until a blocked request will be allowed.
         * Default value is 1 hour.
         */
        public var timeout: Duration = Duration.hours(1)

        /**
         * Overrides the handler to skip a [call] from the rate limit check. Return [SkipResult.SkipRateLimit], if the [call] should be skipped.
         * Default value is [SkipResult.ExecuteRateLimit], every [call] will be checked.
         */
        public fun skip(block: (ApplicationCall) -> SkipResult) {
            skip = block
        }

        internal var skip: (ApplicationCall) -> SkipResult = { SkipResult.ExecuteRateLimit }

        /**
         * Add the [HttpHeaders.RetryAfter] header to the response, if the [host] is blocked by the rate limit check or by the [alwaysBlock] function.
         * Default value is true.
         */
        public var sendRetryAfterHeader: Boolean = true
    }

    public companion object Feature : ApplicationFeature<Application, Configuration, RateLimit> {
        override val key: AttributeKey<RateLimit> = AttributeKey("RateLimit")

        override fun install(pipeline: Application, configure: Configuration.() -> Unit): RateLimit {
            val feature = RateLimit(Configuration().apply(configure))

            pipeline.intercept(ApplicationCallPipeline.Features) {
                intercept(feature)
            }

            return feature
        }

        private suspend fun PipelineContext<Unit, ApplicationCall>.intercept(feature: RateLimit) {
            val host = feature.configuration.host(call)
            if (feature.configuration.skip(call) == SkipResult.SkipRateLimit) {
                proceed()
                return
            }
            when (val isAllowed = feature.isAllowed(host)) {
                is Allow -> {
                    proceed()
                }
                is Block -> {
                    finish()
                    if (feature.configuration.sendRetryAfterHeader) {
                        call.response.header(HttpHeaders.RetryAfter, isAllowed.retryAfter.inWholeSeconds)
                    }
                    call.respond(HttpStatusCode.TooManyRequests)
                }
            }
        }
    }
}
