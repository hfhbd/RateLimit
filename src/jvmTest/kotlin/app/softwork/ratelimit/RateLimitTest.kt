package app.softwork.ratelimit

import app.softwork.ratelimit.RateLimit.RequestResult.*
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.HttpHeaders.RetryAfter
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import kotlin.test.*
import kotlin.time.*

@ExperimentalTime
class RateLimitTest {
    @Test
    fun installTest() = withTestApplication({
        install(RateLimit) {
            limit = 10
        }
        routing {
            get {
                call.respondText { "42" }
            }
        }
    }) {
        repeat(10) { assertEquals(HttpStatusCode.OK, handleRequest(HttpMethod.Get, "/").response.status()) }
        with(handleRequest(HttpMethod.Get, "/").response) {
            assertEquals(HttpStatusCode.TooManyRequests, status())
            assertTrue(headers[RetryAfter]!!.toLong() <= 60 * 60)
        }
    }

    @Test
    fun noHeader() = withTestApplication({
        install(RateLimit) {
            limit = 10
            sendRetryAfterHeader = false
        }
        routing {
            get {
                call.respondText { "42" }
            }
        }
    }) {
        repeat(10) { assertEquals(HttpStatusCode.OK, handleRequest(HttpMethod.Get, "/").response.status()) }
        with(handleRequest(HttpMethod.Get, "/").response) {
            assertEquals(HttpStatusCode.TooManyRequests, status())
            assertNull(headers[RetryAfter])
        }
    }

    @Test
    fun rateLimitOnlyLoginEndpoint() = withTestApplication({
        install(RateLimit) {
            limit = 3
            skip { call ->
                call.request.local.uri != "/login"
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
    }) {
        repeat(10) { assertEquals(HttpStatusCode.OK, handleRequest(HttpMethod.Get, "/").response.status()) }
        repeat(3) { assertEquals(HttpStatusCode.OK, handleRequest(HttpMethod.Get, "/login").response.status()) }
        assertEquals(HttpStatusCode.TooManyRequests, handleRequest(HttpMethod.Get, "/login").response.status())
        repeat(10) { assertEquals(HttpStatusCode.OK, handleRequest(HttpMethod.Get, "/").response.status()) }
    }

    @Test
    fun blockAllowTest() = withTestApplication({
        install(RateLimit) {
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
    }) {
        repeat(5) {
            assertEquals(HttpStatusCode.OK, handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Host, "allowedHost")
            }.response.status())
        }
        assertEquals(HttpStatusCode.TooManyRequests, handleRequest(HttpMethod.Get, "/") {
            addHeader(HttpHeaders.Host, "blockedHost")
        }.response.status())
    }
}

@ExperimentalTime
suspend fun RateLimit.test(limit: Int, timeout: Duration) {
    repeat(limit) {
        assertEquals(Allow, isAllowed("a"))
    }

    repeat(limit * 2) {
        assertTrue(isAllowed("a") is Block)
    }
    delay(Duration.seconds(1))
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
        assertTrue(result.retryAfter < timeout)
    }

    repeat(limit * 2) {
        delay(timeout)
        assertEquals(Allow, isAllowed("a"))
    }
}
