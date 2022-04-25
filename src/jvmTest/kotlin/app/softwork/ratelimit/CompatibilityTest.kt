package app.softwork.ratelimit

import app.softwork.ratelimit.MockStorage.Companion.toClock
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.cors.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*
import kotlin.time.*

@ExperimentalTime
class CompatibilityTest {
    @Test
    fun authentication() = testApplication {
        val clock = TestTimeSource().toClock()
        install(CORS)
        install(Authentication) {
            basic {
                validate {
                    UserIdPrincipal(it.name)
                }
            }
        }

        routing {
            route("/foo") {
                install(RateLimit(MockStorage(clock))) {
                    limit = 5
                }
                authenticate {
                    get {
                        val user = call.principal<UserIdPrincipal>()!!
                        call.respondText { "Hello ${user.name}" }
                    }
                }
            }
        }
        repeat(5) {
            assertEquals(actual = client.get("/foo") {
                basicAuth("foo", "bar")
            }.status, expected = HttpStatusCode.OK)
        }
        assertEquals(actual = client.get("/foo") {
            basicAuth("foo", "bar")
        }.status, expected = HttpStatusCode.TooManyRequests)
    }

    @Test
    fun cors() = testApplication {
        install(CORS)

        routing {
            install(RateLimit(MockStorage()))
        }
    }

    @Test
    fun corsMissing() {
        assertFailsWith<IllegalStateException> {
            testApplication {
                install(RateLimit(MockStorage()))
            }
        }
    }
}
