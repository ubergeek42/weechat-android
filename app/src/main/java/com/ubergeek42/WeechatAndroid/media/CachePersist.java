package com.ubergeek42.WeechatAndroid.media;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Dao;
import androidx.room.Database;
import androidx.room.Entity;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.PrimaryKey;
import androidx.room.Query;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.ubergeek42.WeechatAndroid.Weechat;
import com.ubergeek42.cats.Kitty;
import com.ubergeek42.cats.Root;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static com.ubergeek42.WeechatAndroid.utils.Utils.runInBackground;

public class CachePersist {
    final private static @Root Kitty kitty = Kitty.make();

    final private static Object LOCK = new Object();
    final private static String DATABASE_NAME = "image-fetch-attempts";
    final private static int NO_OF_ITEMS_TO_PERSIST = 2000;

    @Entity(tableName = "attempts")
    static class Attempt {
        @ColumnInfo(name = "key") @PrimaryKey
        final public @NonNull String key;

        @ColumnInfo(name = "code")
        final public int code;

        @ColumnInfo(name = "timestamp")
        final public long timestamp;

        Attempt(@NonNull String key, int code, long timestamp) {
            this.key = key;
            this.code = code;
            this.timestamp = timestamp;
        }
    }

    @Dao
    public interface AttemptsDao {
        @Query("SELECT * FROM attempts ORDER BY timestamp DESC LIMIT :count")
        List<Attempt> getLast(int count);

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        void insertAll(Collection<Attempt> attempts);

        @Query("DELETE FROM attempts WHERE timestamp <= (SELECT timestamp FROM attempts ORDER BY timestamp DESC LIMIT 1 OFFSET :count)")
        int deleteLeaving(int count);
    }

    @Database(entities = {Attempt.class}, version = 1)
    static abstract class AttemptDatabase extends RoomDatabase {
        abstract AttemptsDao attemptsDao();
    }

    private static volatile AttemptDatabase database;
    private static AttemptDatabase getDatabase() {
        if (database == null) {
            synchronized (LOCK) {
                if (database == null) {
                    Context context = Weechat.applicationContext;
                    database = Room.databaseBuilder(context,
                            AttemptDatabase.class,
                            context.getCacheDir().toString() +  "/" + DATABASE_NAME).build();
                    String path = database.getOpenHelper().getWritableDatabase().getPath();
                    int deleted = database.attemptsDao().deleteLeaving(NO_OF_ITEMS_TO_PERSIST);
                    kitty.trace("using database at %s", path);
                    kitty.trace("deleted %s entries, leaving at most %s", deleted, NO_OF_ITEMS_TO_PERSIST);
                }
            }
        }
        return database;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    final private static ConcurrentHashMap<String, Attempt> insertCache = new ConcurrentHashMap<>();

    static void record(String key, Cache.Attempt attempt) {
        insertCache.put(key, new Attempt(key, attempt.code, attempt.timestamp));
    }

    public static void save() {
        if (insertCache.isEmpty()) return;
        runInBackground(() -> {
            kitty.trace("saving %s items", insertCache.size());
            getDatabase().attemptsDao().insertAll(insertCache.values());
            insertCache.clear();
        });
    }

    public static void restore() {
        runInBackground(() -> {
            Collection<Attempt> attempts = getDatabase().attemptsDao().getLast(NO_OF_ITEMS_TO_PERSIST);
            kitty.trace("restoring %s items", attempts.size());
            for (Attempt attempt : attempts)
                Cache.cache.putIfAbsent(attempt.key, new Cache.Attempt(attempt.code, attempt.timestamp));
        });
    }
}
