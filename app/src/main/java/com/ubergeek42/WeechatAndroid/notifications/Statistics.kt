package com.ubergeek42.WeechatAndroid.notifications

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ubergeek42.WeechatAndroid.upload.applicationContext
import com.ubergeek42.cats.Kitty
import com.ubergeek42.cats.Root


private val COLLECT_STATISTICS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1


@Root private val kitty: Kitty = Kitty.make()


interface Statistics {
    fun reportBufferWasSharedTo(key: String)
    fun reportBufferWasManuallyFocused(key: String)
    fun save()
    fun restore()
}


val statistics = if (COLLECT_STATISTICS) {
                     StatisticsImpl(applicationContext)
                 } else {
                     object : Statistics {
                         override fun reportBufferWasSharedTo(key: String) {}
                         override fun reportBufferWasManuallyFocused(key: String) {}
                         override fun save() {}
                         override fun restore() {}
                     }
                 }



@RequiresApi(Build.VERSION_CODES.M)
class StatisticsImpl(val context: Context) : Statistics {
    private val database = ShortcutStatisticsDatabase()

    private val bufferToManuallyFocusedCount = mapSortedByValue<String, Int> { count -> -count }
    private val bufferToSharedToCount = mapSortedByValue<String, Int> { count -> -count }

    fun initialize(focused: Collection<BufferToCount>, sharedTo: Collection<BufferToCount>) {
        focused.forEach { (key, value) -> bufferToManuallyFocusedCount.put(key, value) }
        sharedTo.forEach { (key, value) -> bufferToSharedToCount.put(key, value) }
    }

    override fun save() { database.save() }
    override fun restore() { database.restore() }

    override fun reportBufferWasManuallyFocused(key: String) {
        notificationHandler.post {
            val count = bufferToManuallyFocusedCount.get(key) ?: 0
            bufferToManuallyFocusedCount.put(key, count + 1)
            shortcuts.reportBufferWasManuallyFocused(key, this)
            database.recordBufferWasManuallyFocused(key)
        }
    }

    override fun reportBufferWasSharedTo(key: String) {
        notificationHandler.post {
            val count = bufferToSharedToCount.get(key) ?: 0
            bufferToSharedToCount.put(key, count + 1)
            shortcuts.reportBufferWasSharedTo(key, this)
            database.recordBufferWasSharedTo(key)
        }
    }

    fun getMostFrequentlyManuallyFocusedBuffers(upTo: Int): List<String> {
        return bufferToManuallyFocusedCount.getSomeKeys(upTo)
    }

    fun getMostFrequentlySharedToBuffers(upTo: Int): List<String> {
        return bufferToSharedToCount.getSomeKeys(upTo)
    }
}


////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////


private const val DATABASE_NAME = "shortcut-statistics"
private const val KEEP_MANUALLY_FOCUSED_EVENTS = 100
private const val KEEP_MANUALLY_SHARED_TO_EVENTS = 50


@Entity(tableName = "manually_focused_events")
data class ManuallyFocusedEvent(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @ColumnInfo val key: String,
)


@Entity(tableName = "shared_to_events")
data class SharedToEvent(
    @PrimaryKey(autoGenerate = true) val id: Long,
    @ColumnInfo val key: String,
)


data class BufferToCount(val key: String, val count: Int)


@RequiresApi(Build.VERSION_CODES.M)
private class ShortcutStatisticsDatabase {
    @Dao
    interface Events {
        @Query("SELECT `key`, COUNT(*) AS `count` FROM manually_focused_events GROUP BY `key`")
        fun getBufferToManuallyFocusedCount(): List<BufferToCount>

        @Query("SELECT `key`, COUNT(*) AS `count` FROM shared_to_events GROUP BY `key`")
        fun getBufferToSharedToCount(): List<BufferToCount>

        @Insert(onConflict = OnConflictStrategy.IGNORE)
        fun insertAllManuallyFocusedEvents(records: Collection<ManuallyFocusedEvent>)

        @Insert(onConflict = OnConflictStrategy.IGNORE)
        fun insertAllSharedToEvents(records: Collection<SharedToEvent>)

        @Query("DELETE FROM manually_focused_events WHERE id <= (SELECT id FROM manually_focused_events ORDER BY id DESC LIMIT 1 OFFSET :count)")
        fun deleteFromManuallyFocusedEventsLeaving(count: Int): Int

        @Query("DELETE FROM shared_to_events WHERE id <= (SELECT id FROM shared_to_events ORDER BY id DESC LIMIT 1 OFFSET :count)")
        fun deleteFromSharedToEventsLeaving(count: Int): Int
    }

    @Database(entities = [ManuallyFocusedEvent::class, SharedToEvent::class], version = 1)
    abstract class RecordsDatabase : RoomDatabase() {
        abstract fun eventsDao(): Events
    }

    private val database = Room.databaseBuilder(applicationContext,
            RecordsDatabase::class.java,
            applicationContext.cacheDir.toString() + "/" + DATABASE_NAME).build()

    init {
        kitty.trace("using database at %s", database.openHelper.writableDatabase.path)
    }

    private val events get() = database.eventsDao()

    private val manuallyFocusedEventsInsertCache = mutableListOf<String>()
    private val sharedToEventsInsertCache = mutableListOf<String>()

    fun recordBufferWasManuallyFocused(key: String) {
        manuallyFocusedEventsInsertCache.add(key)
    }

    fun recordBufferWasSharedTo(key: String) {
        sharedToEventsInsertCache.add(key)
    }

    fun save() {
        val focusedSize = manuallyFocusedEventsInsertCache.size
        val sharedToSize = sharedToEventsInsertCache.size
        if (focusedSize > 0 || sharedToSize > 0) {
            notificationHandler.post {
                if (focusedSize > 0) {
                    kitty.trace("saving %s manually focused events", focusedSize)
                    events.insertAllManuallyFocusedEvents(manuallyFocusedEventsInsertCache
                            .map { ManuallyFocusedEvent(0, it) })
                    manuallyFocusedEventsInsertCache.clear()
                }

                if (sharedToSize > 0) {
                    kitty.trace("saving %s shared to events", sharedToSize)
                    events.insertAllSharedToEvents(sharedToEventsInsertCache
                            .map { SharedToEvent(0, it) })
                    sharedToEventsInsertCache.clear()
                }
            }
        }
    }

    fun restore() {
        notificationHandler.post {
            val deletedFocused = events.deleteFromManuallyFocusedEventsLeaving(KEEP_MANUALLY_FOCUSED_EVENTS)
            val focused = events.getBufferToManuallyFocusedCount()
            kitty.trace("restoring %s manually focused buffer records; deleted %s events",
                    focused.size, deletedFocused)

            val deletedSharedTo = events.deleteFromSharedToEventsLeaving(KEEP_MANUALLY_SHARED_TO_EVENTS)
            val sharedTo = events.getBufferToSharedToCount()
            kitty.trace("restoring %s shared to buffer records; deleted %s events",
                sharedTo.size, deletedSharedTo)

            (statistics as StatisticsImpl).initialize(focused, sharedTo)
        }
    }
}
