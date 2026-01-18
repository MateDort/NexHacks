package com.nexhacks.tapmate.memory;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface MemoryDao {
    @Insert
    void insert(MemoryItem item);

    @Query("SELECT * FROM smart_memory WHERE type = :type ORDER BY timestamp DESC LIMIT 1")
    MemoryItem getLastItemByType(String type);

    @Query("SELECT * FROM smart_memory WHERE triggerTime > :currentTime ORDER BY triggerTime ASC")
    List<MemoryItem> getUpcomingItems(long currentTime);
}
