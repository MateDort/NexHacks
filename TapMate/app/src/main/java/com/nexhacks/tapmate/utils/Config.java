package com.nexhacks.tapmate.utils;

import android.content.Context;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Config {
    private static final String TAG = "TapMateConfig";

    public static String GEMINI_API_KEY = "";
    public static String OVERSHOOT_API_KEY = "";
    public static String MAPS_API_KEY = "";

    // Load keys from assets/env file
    public static void loadEnv(Context context) {
        try {
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open("env"))
            );
            
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("#")) continue;
                
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim();
                    
                    switch (key) {
                        case "GEMINI_API_KEY":
                            GEMINI_API_KEY = value;
                            break;
                        case "OVERSHOOT_API_KEY":
                            OVERSHOOT_API_KEY = value;
                            break;
                        case "MAPS_API_KEY":
                        case "GOOGLE_CLOUD_API_KEY": // Fallback for user convenience
                            MAPS_API_KEY = value;
                            break;
                    }
                }
            }
            reader.close();
            
            Log.d(TAG, "Environment loaded successfully.");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to load env file from assets", e);
        }
    }
}
