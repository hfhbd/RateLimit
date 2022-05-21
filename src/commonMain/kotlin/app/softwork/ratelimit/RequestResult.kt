package app.softwork.ratelimit

import kotlin.time.*

/**
 * The result if a call should be allowed or blocked.
 */
public sealed interface RequestResult {
    /**
     * The request was allowed.
     */
    public object Allow : RequestResult

    /**
     * The request was blocked.
     *
     * If [Configuration.sendRetryAfterHeader] is true,
     * the duration in [retryAfter] is sent back in the header `RetryAfter`.
     */
    public data class Block(public val retryAfter: Duration) : RequestResult
}
