package app.softwork.ratelimit

import kotlinx.coroutines.*
import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.dao.id.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.*
import org.jetbrains.exposed.sql.transactions.experimental.*
import kotlin.contracts.*
import kotlin.test.*
import kotlin.time.*

@ExperimentalTime
class DatabaseStorageTest {
    object Locks : IntIdTable() {
        val host = varchar("host", 256).uniqueIndex()
        val trials = integer("trials")
        val lastRequest = long("lastRequest")
    }

    @ExperimentalTime
    class Lock(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<Lock>(Locks)

        var host by Locks.host
        var trials by Locks.trials
        private var _lastRequest by Locks.lastRequest
        var lastRequest: DatabaseStorage.DatabaseTimeSource.DatabaseTimeMark
            get() = DatabaseStorage.DatabaseTimeSource.DatabaseTimeMark(mark = _lastRequest)
            set(value) {
                _lastRequest = value.mark
            }
    }

    @ExperimentalTime
    class DBStorage(private val db: Database) : DatabaseStorage {
        override suspend fun getOrNull(host: String): Storage.Requested? = newSuspendedTransaction(db = db) {
            val found = Lock.find { Locks.host eq host }.firstOrNull()
            if (found != null) {
                Storage.Requested(trial = found.trials, lastRequest = found.lastRequest)
            } else {
                null
            }
        }

        override suspend fun set(host: String, requested: Storage.Requested) {
            newSuspendedTransaction(db = db) {
                val entry = Lock.find { Locks.host eq host }.firstOrNull()
                entry?.apply {
                    this.trials = requested.trial
                    this.lastRequest = requested.lastRequest as DatabaseStorage.DatabaseTimeSource.DatabaseTimeMark
                } ?: Lock.new {
                        this.host = host
                        this.trials = requested.trial
                        this.lastRequest = requested.lastRequest as DatabaseStorage.DatabaseTimeSource.DatabaseTimeMark
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
        val limit = 3
        val timeout = Duration.seconds(3)
        val rateLimit = RateLimit(RateLimit.Configuration().apply {
            this.limit = limit
            this.timeout = timeout
            storage = DBStorage(db = db)
        })

        rateLimit.test(limit = limit, timeout = timeout)
    }

    private fun dbTest(
        name: String = "test",
        setup: suspend (Database) -> Unit = { },
        test: suspend (Database) -> Unit
    ) {
        val db = Database.connect("jdbc:h2:mem:$name;DB_CLOSE_DELAY=-1;", "org.h2.Driver")
        transaction(db) {
            runBlocking {
                setup(db)
            }
        }
        runBlocking {
            test(db)
        }
        TransactionManager.closeAndUnregister(db)
    }
}
