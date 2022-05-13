package com.vizio.beacon;

import static androidx.room.OnConflictStrategy.REPLACE;

import android.content.Context;

import androidx.room.ColumnInfo;
import androidx.room.Delete;
import androidx.room.Entity;
import androidx.room.Insert;
import androidx.room.PrimaryKey;
import androidx.room.Query;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;

public class Storage {
    @Entity
    public static class AdvertiserConfig {
        @PrimaryKey(autoGenerate = true)
        public Long id;

        @ColumnInfo(name = "label")
        public String label;

        @ColumnInfo(name = "power")
        public int power;

        @ColumnInfo(name = "mode")
        public int mode;

        @ColumnInfo(name = "include_device_name")
        public boolean includeDeviceName;

        @ColumnInfo(name = "include_tx_power_level")
        public boolean includeTxPowerLevel;
    }

    @androidx.room.Dao
    public interface AdvertiserConfigDao {
        @Query("SELECT * FROM advertiserconfig")
        ListenableFuture<List<AdvertiserConfig>> getAll();

        @Insert(onConflict = REPLACE)
        ListenableFuture<Long> insert(AdvertiserConfig advertiserConfig);

        @Delete
        ListenableFuture<Integer> delete(AdvertiserConfig advertiserConfig);
    }

    @androidx.room.Database(entities = {AdvertiserConfig.class}, version = 1)
    public abstract static class Database extends RoomDatabase {
        public abstract AdvertiserConfigDao advertiserConfigDao();
    }

    private static Database database = null;

    public static Database getDatabase(Context context) {
        if (database == null) {
            database = Room.databaseBuilder(context, Database.class, "storage")
                .build();
        }
        return database;
    }
}
