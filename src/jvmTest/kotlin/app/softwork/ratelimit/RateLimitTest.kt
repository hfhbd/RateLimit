package app.softwork.ratelimit

import kotlinx.coroutines.*
import kotlin.test.*
import kotlin.time.*

@ExperimentalTime
suspend fun RateLimit.test(limit: Int, coolDown: Duration) {
    repeat(limit) {
        assertTrue(isAllowed("a"))
    }

    repeat(limit * 2) {
        assertFalse(isAllowed("a"))
    }
    delay(1.seconds)
    repeat(limit) {
        assertFalse(isAllowed("a"))
    }
    delay(coolDown)
    assertTrue(isAllowed("a"))

    repeat(limit) {
        assertTrue(isAllowed("a"))
    }

    repeat(limit * 2) {
        assertFalse(isAllowed("a"))
    }

    repeat(limit * 2) {
        delay(coolDown)
        assertTrue(isAllowed("a"))
    }
}
