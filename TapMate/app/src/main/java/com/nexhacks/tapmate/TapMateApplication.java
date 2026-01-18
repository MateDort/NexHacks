package com.nexhacks.tapmate;

import android.app.Application;
import com.nexhacks.tapmate.memory.AppDatabase;
import com.nexhacks.tapmate.utils.Config;

public class TapMateApplication extends Application {
    private static AppDatabase database;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Config.loadEnv(this);
        database = AppDatabase.getDatabase(this);
    }
    
    public static AppDatabase getDatabase() {
        return database;
    }
}
