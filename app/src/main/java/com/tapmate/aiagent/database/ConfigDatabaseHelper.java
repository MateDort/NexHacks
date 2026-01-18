package com.tapmate.aiagent.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * SQLite database helper for TapMate configuration and history.
 * Manages persistent storage for user preferences, configuration history, and interactions.
 */
public class ConfigDatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "ConfigDatabaseHelper";
    private static final String DATABASE_NAME = "tapmate_config.db";
    private static final int DATABASE_VERSION = 1;

    // Table names
    public static final String TABLE_USER_PREFERENCES = "user_preferences";
    public static final String TABLE_CONFIG_HISTORY = "config_history";
    public static final String TABLE_INTERACTION_HISTORY = "interaction_history";

    // user_preferences columns
    public static final String COL_KEY = "key";
    public static final String COL_VALUE = "value";
    public static final String COL_UPDATED_AT = "updated_at";

    // config_history columns
    public static final String COL_ID = "id";
    public static final String COL_SETTING_KEY = "setting_key";
    public static final String COL_OLD_VALUE = "old_value";
    public static final String COL_NEW_VALUE = "new_value";
    public static final String COL_CHANGED_AT = "changed_at";

    // interaction_history columns
    public static final String COL_TIMESTAMP = "timestamp";
    public static final String COL_USER_INPUT = "user_input";
    public static final String COL_AGENT_RESPONSE = "agent_response";
    public static final String COL_CONTEXT = "context";

    public ConfigDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Create user_preferences table
        String createPreferencesTable = "CREATE TABLE " + TABLE_USER_PREFERENCES + " (" +
                COL_KEY + " TEXT PRIMARY KEY," +
                COL_VALUE + " TEXT NOT NULL," +
                COL_UPDATED_AT + " INTEGER NOT NULL" +
                ")";
        db.execSQL(createPreferencesTable);

        // Create config_history table
        String createConfigHistoryTable = "CREATE TABLE " + TABLE_CONFIG_HISTORY + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                COL_SETTING_KEY + " TEXT NOT NULL," +
                COL_OLD_VALUE + " TEXT," +
                COL_NEW_VALUE + " TEXT," +
                COL_CHANGED_AT + " INTEGER NOT NULL" +
                ")";
        db.execSQL(createConfigHistoryTable);

        // Create interaction_history table
        String createInteractionHistoryTable = "CREATE TABLE " + TABLE_INTERACTION_HISTORY + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                COL_TIMESTAMP + " INTEGER NOT NULL," +
                COL_USER_INPUT + " TEXT," +
                COL_AGENT_RESPONSE + " TEXT," +
                COL_CONTEXT + " TEXT" +
                ")";
        db.execSQL(createInteractionHistoryTable);

        Log.i(TAG, "Database created successfully");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USER_PREFERENCES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CONFIG_HISTORY);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_INTERACTION_HISTORY);
        onCreate(db);
    }

    // === User Preferences Operations ===

    public boolean savePreference(String key, String value) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_KEY, key);
        values.put(COL_VALUE, value);
        values.put(COL_UPDATED_AT, System.currentTimeMillis());

        long result = db.insertWithOnConflict(TABLE_USER_PREFERENCES, null, values,
                SQLiteDatabase.CONFLICT_REPLACE);
        return result != -1;
    }

    public String getPreference(String key, String defaultValue) {
        SQLiteDatabase db = this.getReadableDatabase();
        String value = defaultValue;

        Cursor cursor = db.query(TABLE_USER_PREFERENCES,
                new String[]{COL_VALUE},
                COL_KEY + "=?",
                new String[]{key},
                null, null, null);

        if (cursor != null && cursor.moveToFirst()) {
            value = cursor.getString(0);
            cursor.close();
        }
        return value;
    }

    // === Config History Operations ===

    public boolean recordConfigChange(String settingKey, String oldValue, String newValue) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_SETTING_KEY, settingKey);
        values.put(COL_OLD_VALUE, oldValue);
        values.put(COL_NEW_VALUE, newValue);
        values.put(COL_CHANGED_AT, System.currentTimeMillis());

        long result = db.insert(TABLE_CONFIG_HISTORY, null, values);
        return result != -1;
    }

    // === Interaction History Operations ===

    public boolean storeInteraction(String userInput, String agentResponse, String context) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_TIMESTAMP, System.currentTimeMillis());
        values.put(COL_USER_INPUT, userInput);
        values.put(COL_AGENT_RESPONSE, agentResponse);
        values.put(COL_CONTEXT, context);

        long result = db.insert(TABLE_INTERACTION_HISTORY, null, values);
        return result != -1;
    }

    public Cursor getRecentInteractions(int limit) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(TABLE_INTERACTION_HISTORY,
                null, null, null, null, null,
                COL_TIMESTAMP + " DESC",
                String.valueOf(limit));
    }

    public Cursor searchInteractions(String query, int limit) {
        SQLiteDatabase db = this.getReadableDatabase();
        String searchQuery = "%" + query + "%";

        return db.query(TABLE_INTERACTION_HISTORY,
                null,
                COL_USER_INPUT + " LIKE ? OR " + COL_AGENT_RESPONSE + " LIKE ?",
                new String[]{searchQuery, searchQuery},
                null, null,
                COL_TIMESTAMP + " DESC",
                String.valueOf(limit));
    }
}
