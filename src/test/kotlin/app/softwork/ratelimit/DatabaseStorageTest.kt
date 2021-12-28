package app.softwork.ratelimit

import app.softwork.ratelimit.MockStorage.Companion.toClock
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

        data class Requested(override val trial: Int, override val lastRequest: Instant): Storage.Requested

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
        val rateLimit = RateLimit(RateLimit.Configuration().apply {
            limit = 3
            timeout = 3.seconds
            storage = DBStorage(db = db, testTimeSource.toClock())
        })

        rateLimit.test(limit = 3, timeout = 3.seconds)
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
