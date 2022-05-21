package app.softwork.ratelimit

import kotlinx.datetime.*
import kotlin.time.*

@ExperimentalTime
class MockStorage(
    override val clock: Clock = TestTimeSource().toClock()
) : Storage {
    data class Requested(override val trial: Int, override val lastRequest: Instant) : Storage.Requested

    private val storage: MutableMap<String, Storage.Requested> = mutableMapOf()

    override suspend fun getOrNull(host: String): Storage.Requested? = storage[host]

    override suspend fun set(host: String, trial: Int, lastRequest: Instant) {
        storage[host] = Requested(trial, lastRequest)
    }

    override suspend fun remove(host: String) {
        storage.remove(host)
    }

    companion object {
        @ExperimentalTime
        fun TimeSource.toClock(offset: Instant = Instant.fromEpochSeconds(0)): Clock = object : Clock {
            private val startMark: TimeMark = markNow()
            override fun now() = offset + startMark.elapsedNow()
        }
    }
}
