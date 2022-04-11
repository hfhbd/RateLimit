package app.softwork.ratelimit

import kotlinx.coroutines.*
import kotlinx.datetime.*

/**
 * [RateLimit] uses a defined storage provider implementing this interface to persist the request information used for rate limiting.
 * [If you use a blocking database](https://ktor.io/docs/custom-plugins.html#databases), you should wrap your calls inside [withContext].
 */
public interface Storage {
    /**
     * Return the information about previous requests for this [host], or null, if not found.
     */
    public suspend fun getOrNull(host: String): Requested?

    /**
     * Set the current request information for this [host]. [trial] counts the previous trials, while [lastRequest] represents the time of the last request.
     */
    public suspend fun set(host: String, trial: Int, lastRequest: Instant)

    /**
     * Remove all request information for this [host].
     */
    public suspend fun remove(host: String)

    /**
     * The clock for creating a [Instant].
     */
    public val clock: Clock

    /**
     * Holder for the previous stored request information.
     */
    public interface Requested {
        /**
         * Counts the previous trials.
         */
        public val trial: Int

        /**
         * The time of the last request.
         */
        public val lastRequest: Instant
    }
}
