package app.softwork.ratelimit

import app.softwork.ratelimit.MockStorage.Companion.toClock
import kotlinx.coroutines.test.*
import kotlin.test.*
import kotlin.time.*
import kotlin.time.Duration.Companion.seconds

@ExperimentalTime
class MockStorageTest {
    @Test
    fun testMock() = runTest {
        val limit = 3
        val timeout = 3.seconds
        val rateLimit = RateLimit(RateLimit.Configuration().apply {
            this.limit = limit
            this.timeout = timeout
            this.storage = MockStorage(testTimeSource.toClock())
        })
        rateLimit.test(limit = limit, timeout = timeout)
    }
}
