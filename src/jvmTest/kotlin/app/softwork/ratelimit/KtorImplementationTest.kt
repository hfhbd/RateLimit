package app.softwork.ratelimit

import io.ktor.application.*
import io.ktor.client.engine.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import kotlin.test.*
import kotlin.time.*

@ExperimentalTime
class KtorImplementationTest {
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
        assertEquals(HttpStatusCode.TooManyRequests, handleRequest(HttpMethod.Get, "/").response.status())
    }

    @Test
    fun ratelimitOnlyLoginEndpoint() = withTestApplication({
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
}
