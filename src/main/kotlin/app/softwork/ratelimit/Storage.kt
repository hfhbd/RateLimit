package app.softwork.ratelimit

import kotlin.time.*

/**
 * [RateLimit] uses a defined storage provider implementing this interface to persist the request information used for rate limiting.
 */
@ExperimentalTime
public interface Storage {
    /**
     * Return the information about previous requests for this [host], or null, if not found.
     */
    public suspend fun getOrNull(host: String): Requested?

    /**
     * Set the current [requested] request information for this [host].
     */
    public suspend fun set(host: String, requested: Requested)

    /**
     * Remove all request information for this [host].
     */
    public suspend fun remove(host: String)

    /**
     * The timeSource for creating a [TimeMark]. See [kotlin.time] for more information.
     */
    public val timeSource: TimeSource

    /**
     * Holder for the previous stored request information.
     * @param trial counts the previous trials.
     * @param lastRequest the time of the last request.
     */
    public data class Requested(public val trial: Int, public val lastRequest: TimeMark)
}
