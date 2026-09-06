@file:Suppress("ClassName")

package com.ubergeek42.WeechatAndroid.upload

import android.net.Uri
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
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import cats.trace
import com.ubergeek42.WeechatAndroid.utils.applicationContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread


@Entity(tableName = "upload_records")
data class UploadRecord(
    @PrimaryKey                     val uri: Uri,
    @ColumnInfo(name = "http_uri")  val httpUri: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long
)


private val cache = ConcurrentHashMap<Uri, UploadRecord>()


private fun filterRecords() {
    val now = System.currentTimeMillis()
    cache.values.retainAll { now - it.timestamp < Config.rememberUploadsFor }
}


object Cache {
    fun record(uri: Uri, httpUri: String) {
        val record = UploadRecord(uri, httpUri, System.currentTimeMillis())
        UploadDatabase.record(record)
        cache[uri] = record
    }

    fun retrieve(uri: Uri): String? {
        filterRecords()
        return cache[uri]?.httpUri
    }
}


////////////////////////////////////////////////////////////////////////////////////////////////////


object UploadDatabase {
    @Dao
    interface UploadRecords {
        @Query("SELECT * FROM upload_records")
        fun getAll(): List<UploadRecord>

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        fun insertAll(records: Collection<UploadRecord>)

        @Query("DELETE FROM upload_records WHERE timestamp < :timestamp")
        fun deleteRecordsOlderThan(timestamp: Long): Int
    }

    @Database(entities = [UploadRecord::class], version = 1)
    @TypeConverters(Converters::class)
    abstract class UploadRecordsDatabase : RoomDatabase() {
        abstract fun uploadRecordsDao(): UploadRecords
    }

    private const val DATABASE_NAME = "upload-records"

    private val database = Room.databaseBuilder(applicationContext,
            UploadRecordsDatabase::class.java,
            applicationContext.cacheDir.toString() + "/" + DATABASE_NAME).build()

    init {
        trace { "using database at ${database.openHelper.writableDatabase.path}" }
    }

    private val records get() = database.uploadRecordsDao()

    private val insertCache = ConcurrentHashMap<Uri, UploadRecord>()

    fun record(record: UploadRecord) {
        insertCache[record.uri] = record
    }

    @JvmStatic fun save() {
        if (insertCache.isNotEmpty()) {
            thread {
                trace { "saving ${insertCache.size} items" }
                records.insertAll(insertCache.values)
                insertCache.clear()
            }
        }
    }

    @JvmStatic fun restore() {
        thread {
            val deleted = records.deleteRecordsOlderThan(System.currentTimeMillis() - Config.rememberUploadsFor)
            val records = records.getAll()
            trace {"restoring ${records.size} items; deleted $deleted entries (remembering uploads for ${Config.rememberUploadsFor} ms)" }
            records.forEach { cache.putIfAbsent(it.uri, it) }
        }
    }

    @Suppress("unused") class Converters {
        @TypeConverter fun uriToString(uri: Uri): String = uri.toString()
        @TypeConverter fun stringToUri(string: String): Uri = Uri.parse(string)
    }
}
