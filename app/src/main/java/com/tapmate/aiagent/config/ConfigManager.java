package com.tapmate.aiagent.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.tapmate.aiagent.database.ConfigDatabaseHelper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Configuration manager for TapMate.
 * Manages user preferences with hybrid storage (SharedPreferences for runtime, SQLite for persistence).
 * Handles system instruction template generation with personalization.
 */
public class ConfigManager {

    private static final String TAG = "ConfigManager";
    private static final String PREFS_NAME = "tapmate_config";

    private static ConfigManager instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final ConfigDatabaseHelper dbHelper;
    private UserConfig activeConfig;

    // Preference keys
    private static final String KEY_HUMOR = "humor_percentage";
    private static final String KEY_HONESTY = "honesty_percentage";
    private static final String KEY_COMMUNICATION_STYLE = "communication_style";
    private static final String KEY_NATIONALITY = "nationality";
    private static final String KEY_VOICE = "voice_name";
    private static final String KEY_HAPTIC = "haptic_intensity";
    private static final String KEY_VERBOSITY = "screen_reader_verbosity";
    private static final String KEY_VISION_SENSITIVITY = "vision_sensitivity";
    private static final String KEY_NAV_DETAIL = "navigation_detail_level";
    private static final String KEY_ORBIT_MODE = "orbit_mode_enabled";
    private static final String KEY_ORBIT_PATTERN = "orbit_haptic_pattern";
    private static final String KEY_COLOR_SCHEME = "color_scheme";

    private ConfigManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.dbHelper = new ConfigDatabaseHelper(context);
        this.activeConfig = loadConfig();
        Log.i(TAG, "ConfigManager initialized: " + activeConfig);
    }

    public static synchronized ConfigManager getInstance(Context context) {
        if (instance == null) {
            instance = new ConfigManager(context);
        }
        return instance;
    }

    public UserConfig getActiveConfig() {
        return activeConfig;
    }

    public void updateSetting(String key, Object value) {
        String oldValue = getSettingAsString(key);
        String newValue = value.toString();

        updateConfigObject(key, value);
        saveToPreferences(key, value);
        dbHelper.savePreference(key, newValue);
        dbHelper.recordConfigChange(key, oldValue, newValue);

        Log.i(TAG, "Setting updated: " + key + " = " + newValue);
    }

    public void reloadConfig() {
        this.activeConfig = loadConfig();
        Log.i(TAG, "Configuration reloaded: " + activeConfig);
    }

    public String buildSystemInstruction() {
        String template = loadSystemInstructionTemplate();

        SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.US);
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US);
        Date now = new Date();

        return template
                .replace("{current_time}", timeFormat.format(now))
                .replace("{current_date}", dateFormat.format(now))
                .replace("{humor_percentage}", String.valueOf(activeConfig.getHumorPercentage()))
                .replace("{honesty_percentage}", String.valueOf(activeConfig.getHonestyPercentage()))
                .replace("{communication_style}", activeConfig.getCommunicationStyle())
                .replace("{nationality}", activeConfig.getNationality())
                .replace("{screen_reader_verbosity}", activeConfig.getScreenReaderVerbosity())
                .replace("{navigation_detail_level}", activeConfig.getNavigationDetailLevel());
    }

    private UserConfig loadConfig() {
        UserConfig config = new UserConfig();
        config.setHumorPercentage(prefs.getInt(KEY_HUMOR, 70));
        config.setHonestyPercentage(prefs.getInt(KEY_HONESTY, 95));
        config.setCommunicationStyle(prefs.getString(KEY_COMMUNICATION_STYLE, "normal"));
        config.setNationality(prefs.getString(KEY_NATIONALITY, "British"));
        config.setVoiceName(prefs.getString(KEY_VOICE, "Puck"));
        config.setHapticIntensity(prefs.getInt(KEY_HAPTIC, 70));
        config.setScreenReaderVerbosity(prefs.getString(KEY_VERBOSITY, "standard"));
        config.setVisionSensitivity(prefs.getInt(KEY_VISION_SENSITIVITY, 50));
        config.setNavigationDetailLevel(prefs.getString(KEY_NAV_DETAIL, "full"));
        config.setOrbitModeEnabled(prefs.getBoolean(KEY_ORBIT_MODE, true));
        config.setOrbitHapticPattern(prefs.getInt(KEY_ORBIT_PATTERN, 1));
        config.setColorScheme(prefs.getString(KEY_COLOR_SCHEME, "high-contrast"));
        return config;
    }

    private void updateConfigObject(String key, Object value) {
        switch (key) {
            case KEY_HUMOR: activeConfig.setHumorPercentage((Integer) value); break;
            case KEY_HONESTY: activeConfig.setHonestyPercentage((Integer) value); break;
            case KEY_COMMUNICATION_STYLE: activeConfig.setCommunicationStyle((String) value); break;
            case KEY_NATIONALITY: activeConfig.setNationality((String) value); break;
            case KEY_VOICE: activeConfig.setVoiceName((String) value); break;
            case KEY_HAPTIC: activeConfig.setHapticIntensity((Integer) value); break;
            case KEY_VERBOSITY: activeConfig.setScreenReaderVerbosity((String) value); break;
            case KEY_VISION_SENSITIVITY: activeConfig.setVisionSensitivity((Integer) value); break;
            case KEY_NAV_DETAIL: activeConfig.setNavigationDetailLevel((String) value); break;
            case KEY_ORBIT_MODE: activeConfig.setOrbitModeEnabled((Boolean) value); break;
            case KEY_ORBIT_PATTERN: activeConfig.setOrbitHapticPattern((Integer) value); break;
            case KEY_COLOR_SCHEME: activeConfig.setColorScheme((String) value); break;
        }
    }

    private void saveToPreferences(String key, Object value) {
        SharedPreferences.Editor editor = prefs.edit();
        if (value instanceof Integer) editor.putInt(key, (Integer) value);
        else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
        else if (value instanceof String) editor.putString(key, (String) value);
        editor.apply();
    }

    private String getSettingAsString(String key) {
        switch (key) {
            case KEY_HUMOR: return String.valueOf(activeConfig.getHumorPercentage());
            case KEY_HONESTY: return String.valueOf(activeConfig.getHonestyPercentage());
            case KEY_COMMUNICATION_STYLE: return activeConfig.getCommunicationStyle();
            case KEY_NATIONALITY: return activeConfig.getNationality();
            case KEY_VOICE: return activeConfig.getVoiceName();
            case KEY_HAPTIC: return String.valueOf(activeConfig.getHapticIntensity());
            case KEY_VERBOSITY: return activeConfig.getScreenReaderVerbosity();
            case KEY_VISION_SENSITIVITY: return String.valueOf(activeConfig.getVisionSensitivity());
            case KEY_NAV_DETAIL: return activeConfig.getNavigationDetailLevel();
            case KEY_ORBIT_MODE: return String.valueOf(activeConfig.isOrbitModeEnabled());
            case KEY_ORBIT_PATTERN: return String.valueOf(activeConfig.getOrbitHapticPattern());
            case KEY_COLOR_SCHEME: return activeConfig.getColorScheme();
            default: return "";
        }
    }

    private String loadSystemInstructionTemplate() {
        try {
            InputStream is = context.getResources().openRawResource(
                    context.getResources().getIdentifier("system_instruction_template",
                            "raw", context.getPackageName()));
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder template = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                template.append(line).append("\n");
            }
            reader.close();
            return template.toString();
        } catch (Exception e) {
            Log.w(TAG, "Could not load template, using default");
            return getDefaultSystemInstruction();
        }
    }

    private String getDefaultSystemInstruction() {
        return "You are TapMate, an intelligent assistant for visually impaired and blind users.\n" +
                "You are {nationality} in style and tone.\n\n" +
                "Current Context: {current_time}, {current_date}\n\n" +
                "Personality: Humor {humor_percentage}%, Honesty {honesty_percentage}%\n" +
                "Style: {communication_style}, Verbosity: {screen_reader_verbosity}\n\n" +
                "Your goal is to help users navigate their phone and surroundings using voice commands,\n" +
                "accessibility features, and vision processing. Be proactive, respectful, and safety-conscious.";
    }

    public boolean handleVoiceConfigCommand(String command) {
        command = command.toLowerCase();
        if (command.contains("brief") || command.contains("shorter")) {
            updateSetting(KEY_COMMUNICATION_STYLE, "brief");
            updateSetting(KEY_VERBOSITY, "minimal");
            return true;
        } else if (command.contains("chatty") || command.contains("more detail")) {
            updateSetting(KEY_COMMUNICATION_STYLE, "chatty");
            updateSetting(KEY_VERBOSITY, "detailed");
            return true;
        } else if (command.contains("serious") || command.contains("less humor")) {
            int newHumor = Math.max(0, activeConfig.getHumorPercentage() - 30);
            updateSetting(KEY_HUMOR, newHumor);
            return true;
        } else if (command.contains("funny") || command.contains("more humor")) {
            int newHumor = Math.min(100, activeConfig.getHumorPercentage() + 30);
            updateSetting(KEY_HUMOR, newHumor);
            return true;
        } else if (command.contains("increase haptic")) {
            int newHaptic = Math.min(100, activeConfig.getHapticIntensity() + 20);
            updateSetting(KEY_HAPTIC, newHaptic);
            return true;
        } else if (command.contains("decrease haptic")) {
            int newHaptic = Math.max(0, activeConfig.getHapticIntensity() - 20);
            updateSetting(KEY_HAPTIC, newHaptic);
            return true;
        }
        return false;
    }
}
