package com.nexhacks.tapmate.memory;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {MemoryItem.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    public abstract MemoryDao memoryDao();
}
