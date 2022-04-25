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
        val rateLimit: RateLimit = Configuration(
            storage = MockStorage(testTimeSource.toClock()),
        ).apply {
            limit = 3
            timeout = 3.seconds
        }.build()
        rateLimit.test(limit = 3, timeout = 3.seconds)
    }
}
