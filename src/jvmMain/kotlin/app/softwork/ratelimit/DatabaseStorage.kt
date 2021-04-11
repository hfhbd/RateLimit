package app.softwork.ratelimit

import kotlin.time.*

@ExperimentalTime
public interface DatabaseStorage : Storage {
    public override val timeSource: DatabaseTimeSource get() = DatabaseTimeSource

    @ExperimentalTime
    public object DatabaseTimeSource : AbstractLongTimeSource(DurationUnit.MILLISECONDS) {
        public val durationUnit: DurationUnit = DurationUnit.MILLISECONDS
        public override fun read(): Long = System.currentTimeMillis()
        public override fun markNow(): TimeMark = DatabaseTimeMark(read())


        @ExperimentalTime
        public class DatabaseTimeMark(public val mark: Long) : TimeMark() {
            override fun elapsedNow(): Duration = (read() - mark).toDuration(durationUnit)
            override fun plus(duration: Duration): TimeMark =
                DatabaseTimeMark(mark + duration.toLong(durationUnit))
        }
    }
}
