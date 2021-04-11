package app.softwork.ratelimit

import app.softwork.ratelimit.RateLimit.RequestResult.*
import kotlinx.coroutines.*
import kotlin.test.*
import kotlin.time.*

@ExperimentalTime
suspend fun RateLimit.test(limit: Int, timeout: Duration) {
    repeat(limit) {
        assertEquals(Allow, isAllowed("a"))
    }

    repeat(limit * 2) {
        assertTrue(isAllowed("a") is Forbid)
    }
    delay(Duration.seconds(1))
    repeat(limit) {
        assertTrue(isAllowed("a") is Forbid)
    }
    delay(timeout)
    assertEquals(Allow, isAllowed("a"))

    repeat(limit) {
        assertEquals(Allow, isAllowed("a"))
    }

    repeat(limit * 2) {
        val result = isAllowed("a")
        assertTrue(result is Forbid)
        assertTrue(result.retryAfter < timeout)
    }

    repeat(limit * 2) {
        delay(timeout)
        assertEquals(Allow, isAllowed("a"))
    }
}
