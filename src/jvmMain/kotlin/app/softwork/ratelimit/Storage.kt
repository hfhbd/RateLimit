package app.softwork.ratelimit

import kotlin.time.*

@ExperimentalTime
public interface Storage {
    public suspend fun getOrNull(host: String): Requested?
    public suspend fun set(host: String, requested: Requested)
    public suspend fun remove(host: String)

    public val timeSource: TimeSource

    public data class Requested(public val trial: Int, public val lastRequest: TimeMark)
}
