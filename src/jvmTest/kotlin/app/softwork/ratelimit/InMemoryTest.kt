package app.softwork.ratelimit

import kotlinx.coroutines.*
import kotlin.test.*
import kotlin.time.*

@ExperimentalTime
class InMemoryTest {
    @Test
    fun testInMemory() = runBlocking {
        val limit = 3
        val coolDown = 3.seconds
        val rateLimit = RateLimit(RateLimit.Configuration().apply {
            this.limit = limit
            this.timeout = coolDown
        })
        rateLimit.test(limit = limit, coolDown = coolDown)
    }
}
