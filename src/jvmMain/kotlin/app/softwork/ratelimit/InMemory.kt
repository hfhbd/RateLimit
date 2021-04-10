package app.softwork.ratelimit

import kotlin.time.*

@ExperimentalTime
public class InMemory : Storage {
    private val storage = mutableMapOf<String, Storage.Requested>()

    override suspend fun getOrNull(host: String): Storage.Requested? = storage[host]
    override suspend fun set(host: String, requested: Storage.Requested) {
        storage[host] = requested
    }

    override suspend fun remove(host: String) {
        storage.remove(host)
    }

    override val timeSource: TimeSource = TimeSource.Monotonic
}
