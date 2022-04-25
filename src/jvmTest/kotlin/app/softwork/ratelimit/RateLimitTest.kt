package app.softwork.ratelimit

import app.softwork.ratelimit.MockStorage.Companion.toClock
import app.softwork.ratelimit.RequestResult.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.HttpHeaders.RetryAfter
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import kotlin.test.*
import kotlin.time.*
import kotlin.time.Duration.Companion.seconds

@ExperimentalTime
class RateLimitTest {
    @Test
    fun installTest() = testApplication {
        testRateLimit {
            limit = 10
        }
        routing {
            get {
                call.respondText { "42" }
            }
        }

        repeat(10) { assertEquals(HttpStatusCode.OK, client.get(urlString = "/").status) }
        with(client.get("/")) {
            assertEquals(HttpStatusCode.TooManyRequests, status)
            assertTrue(headers[RetryAfter]!!.toLong() <= 60 * 60)
        }
    }

    @Test
    fun noHeader() = testApplication {
        testRateLimit {
            limit = 10
            sendRetryAfterHeader = false
        }
        routing {
            get {
                call.respondText { "42" }
            }
        }

        repeat(10) { assertEquals(HttpStatusCode.OK, client.get("/").status) }
        with(client.get("/")) {
            assertEquals(HttpStatusCode.TooManyRequests, status)
            assertNull(headers[RetryAfter])
        }
    }

    @Test
    fun rateLimitOnlyLoginEndpoint() = testApplication {
        testRateLimit {
            limit = 3
            skip { call ->
                if (call.request.local.uri == "/login") {
                    SkipResult.ExecuteRateLimit
                } else {
                    SkipResult.SkipRateLimit
                }
            }
        }
        routing {
            get {
                call.respondText { "42" }
            }
            get("/login") {
                call.respondText { "/login called" }
            }
        }

        repeat(10) { assertEquals(HttpStatusCode.OK, client.get("/").status) }
        repeat(3) { assertEquals(HttpStatusCode.OK, client.get("/login").status) }
        assertEquals(HttpStatusCode.TooManyRequests, client.get("/login").status)
        repeat(10) { assertEquals(HttpStatusCode.OK, client.get("/").status) }
    }

    @Test
    fun rateLimitOnlyLoginEndpointRouteScopePlugin() = testApplication {
        routing {
            get {
                call.respondText { "42" }
            }
            route("/login") {
                install(RateLimit(MockStorage())) {
                    limit = 3
                    ignoreCORSInstallationCheck = true
                }
                get {
                    call.respondText { "/login called" }
                }
            }
        }

        repeat(10) { assertEquals(HttpStatusCode.OK, client.get("/").status) }
        repeat(3) { assertEquals(HttpStatusCode.OK, client.get("/login").status) }
        assertEquals(HttpStatusCode.TooManyRequests, client.get("/login").status)
        repeat(10) { assertEquals(HttpStatusCode.OK, client.get("/").status) }
    }

    @Test
    fun blockAllowTest() = testApplication {
        testRateLimit {
            limit = 3
            alwaysBlock { host ->
                host == "blockedHost"
            }
            alwaysAllow { host ->
                host == "allowedHost"
            }
            host { call ->
                call.request.local.host
            }
        }

        routing {
            get {
                call.respondText { "Hello" }
            }
        }
        repeat(5) {
            assertEquals(HttpStatusCode.OK, client.get("/") {
                header(HttpHeaders.Host, "allowedHost")
            }.status)
        }
        assertEquals(HttpStatusCode.TooManyRequests, client.get("/") {
            header(HttpHeaders.Host, "blockedHost")
        }.status)
    }
}

internal suspend fun RateLimit.test(limit: Int, timeout: Duration) {
    repeat(limit) {
        assertEquals(Allow, isAllowed("a"))
    }

    repeat(limit * 2) {
        assertTrue(isAllowed("a") is Block)
    }
    delay(1.seconds)
    repeat(limit) {
        assertTrue(isAllowed("a") is Block)
    }
    delay(timeout)
    assertEquals(Allow, isAllowed("a"))

    repeat(limit) {
        assertEquals(Allow, isAllowed("a"))
    }

    repeat(limit * 2) {
        val result = isAllowed("a")
        assertTrue(result is Block)
        assertEquals(timeout, result.retryAfter)
    }

    repeat(limit * 2) {
        delay(timeout)
        assertEquals(Allow, isAllowed("a"))
    }
}

@ExperimentalTime
fun TestApplicationBuilder.testRateLimit(
    storage: Storage = MockStorage(clock = TestTimeSource().toClock()),
    block: Configuration.() -> Unit
) {
    application {
        testRateLimit(storage, block)
    }
}

@ExperimentalTime
fun Application.testRateLimit(
    storage: Storage = MockStorage(clock = TestTimeSource().toClock()),
    block: Configuration.() -> Unit
) {
    install(RateLimit(storage)) {
        ignoreCORSInstallationCheck = true
        block()
    }
}
