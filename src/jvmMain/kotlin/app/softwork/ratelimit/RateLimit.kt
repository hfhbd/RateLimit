package app.softwork.ratelimit

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.util.*
import kotlin.time.*

@ExperimentalTime
public class RateLimit(public val configuration: Configuration) {
    public suspend fun isAllowed(host: String): Boolean {
        if (configuration.alwaysAllow(host)) {
            return true
        }
        if (configuration.alwaysBlock(host)) {
            return false
        }

        val storage = configuration.storage
        val previous = storage.getOrNull(host)
        if (previous == null) {
            storage.set(host, Storage.Requested(trial = 1, lastRequest = storage.timeSource.markNow()))
            return true
        }
        if (previous.trial < configuration.limit) {
            storage.set(host, previous.copy(trial = previous.trial + 1, lastRequest = storage.timeSource.markNow()))
            return true
        }
        val lastRequest = previous.lastRequest
        if (lastRequest.elapsedNow() > configuration.timeout) {
            storage.remove(host)
            return true
        }
        return false
    }

    public class Configuration {
        public var host: (ApplicationCall) -> String = { it.request.local.remoteHost }
        public var alwaysAllow: (String) -> Boolean = { false }
        public var alwaysBlock: (String) -> Boolean = { false }
        public var storage: Storage = InMemory()
        public var limit: Int = 1000
        public var timeout: Duration = Duration.hours(1)
    }

    public companion object Feature : ApplicationFeature<Application, Configuration, RateLimit> {
        override val key: AttributeKey<RateLimit> = AttributeKey("RateLimit")

        override fun install(pipeline: Application, configure: Configuration.() -> Unit): RateLimit {
            val feature = RateLimit(Configuration().apply(configure))

            pipeline.intercept(ApplicationCallPipeline.Features) {
                val host = feature.configuration.host(call)
                if (feature.isAllowed(host)) {
                    proceed()
                } else {
                    finish()
                    call.respond(HttpStatusCode.TooManyRequests)
                }
            }
            return feature
        }
    }
}
