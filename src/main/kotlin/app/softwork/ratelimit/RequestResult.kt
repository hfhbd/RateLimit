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
     * @param retryAfter if [Configuration.sendRetryAfterHeader] is true, this duration is sent back as in the header `RetryAfter`.
     */
    public data class Block(public val retryAfter: Duration) : RequestResult
}
