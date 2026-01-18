package com.nexhacks.tapmate.memory;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "smart_memory")
public class MemoryItem {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String type;       // "UBER_RIDE", "LOCATION", "REMINDER"
    public String rawText;    // "Red Toyota Prius, Plate ABC-123"
    public String structuredData; // JSON string: {"color":"red", "plate":"ABC"}
    public long timestamp;    // When it was saved
    public long triggerTime;  // When to recall (ETA)

    public MemoryItem(String type, String rawText, String structuredData, long timestamp, long triggerTime) {
        this.type = type;
        this.rawText = rawText;
        this.structuredData = structuredData;
        this.timestamp = timestamp;
        this.triggerTime = triggerTime;
    }
}
