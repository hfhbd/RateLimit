package app.softwork.ratelimit

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import kotlin.test.*
import kotlin.time.*

@ExperimentalTime
class KtorImplementationTest {
    @Test
    fun liveTest() = withTestApplication({
        install(RateLimit) {
            this.limit = 10
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
}
