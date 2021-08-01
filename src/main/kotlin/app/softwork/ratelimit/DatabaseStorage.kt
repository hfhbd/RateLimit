package app.softwork.ratelimit

import app.softwork.ratelimit.DatabaseStorage.DatabaseTimeSource.durationUnit
import kotlin.time.*

/**
 * This [Storage] uses an own [TimeSource] implementation to get the actual [TimeMark] as a [Long] to persist/restore it in/from a database.
 */
@ExperimentalTime
public interface DatabaseStorage : Storage {
    public override val timeSource: DatabaseTimeSource get() = DatabaseTimeSource

    /**
     * This [TimeSource] uses a [Long] and [System.currentTimeMillis] as internal implementation. The [durationUnit] of the [Long] is milliseconds.
     */
    @ExperimentalTime
    public object DatabaseTimeSource : AbstractLongTimeSource(DurationUnit.MILLISECONDS) {
        public val durationUnit: DurationUnit = DurationUnit.MILLISECONDS
        public override fun read(): Long = System.currentTimeMillis()
        public override fun markNow(): TimeMark = DatabaseTimeMark(read())

        /**
         * A persistable [TimeMark] implementation.
         * @param mark the difference in milliseconds between the [TimeMark] and 1970-01-01T00:00+UTC.
         */
        @ExperimentalTime
        public class DatabaseTimeMark(public val mark: Long) : TimeMark() {
            override fun elapsedNow(): Duration = (read() - mark).toDuration(durationUnit)
        }
    }
}
