package app.softwork.ratelimit

/**
 * Result of [Configuration.skip] to skip the rate limit for this host on [SkipRateLimit] or
 * to execute it on [ExecuteRateLimit]
 */
public enum class SkipResult {
    SkipRateLimit, ExecuteRateLimit
}
