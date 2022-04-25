package app.softwork.ratelimit

import app.softwork.ratelimit.MockStorage.Companion.toClock
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.cors.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.datetime.*
import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.dao.id.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.kotlin.datetime.*
import org.jetbrains.exposed.sql.transactions.*
import org.jetbrains.exposed.sql.transactions.experimental.*
import kotlin.test.*
import kotlin.time.*
import kotlin.time.Duration.Companion.seconds
import io.ktor.client.engine.cio.CIO as ClientCIO

@ExperimentalTime
class DatabaseStorageTest {
    object Locks : IntIdTable() {
        val host = varchar("host", 256).uniqueIndex()
        val trials = integer("trials")
        val lastRequest = timestamp("lastRequest")
    }

    class Lock(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<Lock>(Locks)

        var host by Locks.host
        var trials by Locks.trials
        var lastRequest by Locks.lastRequest
    }

    class DBStorage(private val db: Database, override val clock: Clock) : Storage {
        override suspend fun getOrNull(host: String): Storage.Requested? = newSuspendedTransaction(db = db) {
            val found = Lock.find { Locks.host eq host }.firstOrNull()
            if (found != null) {
                Requested(trial = found.trials, lastRequest = found.lastRequest)
            } else {
                null
            }
        }

        data class Requested(override val trial: Int, override val lastRequest: Instant) : Storage.Requested

        override suspend fun set(host: String, trial: Int, lastRequest: Instant) {
            newSuspendedTransaction(db = db) {
                val entry = Lock.find { Locks.host eq host }.firstOrNull()
                entry?.apply {
                    this.trials = trial
                    this.lastRequest = lastRequest
                } ?: Lock.new {
                    this.host = host
                    this.trials = trial
                    this.lastRequest = lastRequest
                }
            }
        }

        override suspend fun remove(host: String) = newSuspendedTransaction(db = db) {
            val entry = Lock.find { Locks.host eq host }.first()
            entry.delete()
        }
    }

    @Test
    fun dbBasedRateLimit() = dbTest(setup = {
        SchemaUtils.create(Locks)
    }) { db ->
        val rateLimit = Configuration(storage = DBStorage(db = db, testTimeSource.toClock())).apply {
            limit = 3
            timeout = 3.seconds
        }.build()

        rateLimit.test(limit = 3, timeout = 3.seconds)
    }

    @Test
    fun integrationTest() = dbTest(setup = {
        SchemaUtils.create(Locks)
    }) {db ->
        val server = embeddedServer(CIO, port = 0) {
            install(CORS)
            routing {
                install(RateLimit(storage = DBStorage(db = db, Clock.System))) {
                    limit = 10

                    this@routing.get {
                        call.respondText { "Success" }
                    }
                }
            }
        }.start(wait = false)
        val port = runBlocking { server.resolvedConnectors().first().port }

        val client = HttpClient(ClientCIO) {
            install(DefaultRequest) {
                url {
                    this.port = port
                }
            }
        }
        repeat(10) { assertEquals(HttpStatusCode.OK, client.get(urlString = "/").status) }
        with(client.get("/")) {
            assertEquals(HttpStatusCode.TooManyRequests, status)
            assertTrue(headers[HttpHeaders.RetryAfter]!!.toLong() <= 60 * 60)
        }
        server.stop()
    }

    private fun dbTest(
        name: String = "test",
        setup: suspend TestScope.(Database) -> Unit = { },
        test: suspend TestScope.(Database) -> Unit
    ) {
        val db = Database.connect("jdbc:h2:mem:$name;DB_CLOSE_DELAY=-1;", "org.h2.Driver")
        transaction(db) {
            runTest {
                setup(db)
            }
        }
        runTest {
            test(db)
        }
        TransactionManager.closeAndUnregister(db)
    }
}
