package com.tapmate.aiagent.agents;

import android.content.Context;
import android.util.Log;

import com.tapmate.aiagent.config.ConfigManager;
import com.tapmate.aiagent.config.UserConfig;
import com.tapmate.aiagent.core.FunctionResponse;
import com.tapmate.aiagent.core.SubAgent;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.CompletableFuture;

/**
 * ConfigAgent - Handles dynamic adjustment of user preferences.
 * Allows users to change settings via voice commands.
 */
public class ConfigAgent implements SubAgent {

    private static final String TAG = "ConfigAgent";
    private static final String FUNCTION_NAME = "adjust_config";

    private final Context context;
    private final ConfigManager configManager;

    public ConfigAgent(Context context) {
        this.context = context;
        this.configManager = ConfigManager.getInstance(context);
    }

    @Override
    public String getName() {
        return FUNCTION_NAME;
    }

    @Override
    public String getDescription() {
        return "Adjust TapMate settings and user preferences. " +
                "Available settings: humor (0-100%), honesty (0-100%), " +
                "communication_style (chatty/normal/brief), nationality, voice, " +
                "haptic_intensity (0-100%), screen_reader_verbosity (minimal/standard/detailed), " +
                "vision_sensitivity (0-100%), navigation_detail_level (landmarks/full/minimal), " +
                "orbit_mode (true/false), color_scheme (high-contrast/standard/custom).";
    }

    @Override
    public JSONObject getFunctionDeclaration() {
        try {
            JSONObject declaration = new JSONObject();
            declaration.put("name", FUNCTION_NAME);
            declaration.put("description", getDescription());

            // Parameters
            JSONObject parameters = new JSONObject();
            parameters.put("type", "OBJECT");

            JSONObject properties = new JSONObject();

            // Action parameter
            JSONObject actionParam = new JSONObject();
            actionParam.put("type", "STRING");
            actionParam.put("description", "Action to perform: 'set' (change value) or 'get' (check current value)");
            properties.put("action", actionParam);

            // Setting parameter
            JSONObject settingParam = new JSONObject();
            settingParam.put("type", "STRING");
            settingParam.put("description", "Setting to adjust: 'humor', 'honesty', 'communication_style', " +
                    "'nationality', 'voice', 'haptic_intensity', 'screen_reader_verbosity', " +
                    "'vision_sensitivity', 'navigation_detail_level', 'orbit_mode', 'color_scheme'");
            properties.put("setting", settingParam);

            // Value parameter
            JSONObject valueParam = new JSONObject();
            valueParam.put("type", "STRING");
            valueParam.put("description", "New value for the setting. For percentages: 0-100. " +
                    "For communication_style: chatty/normal/brief. For verbosity: minimal/standard/detailed.");
            properties.put("value", valueParam);

            parameters.put("properties", properties);

            // Required parameters
            JSONObject required = new JSONObject();
            required.put("0", "action");
            required.put("1", "setting");
            parameters.put("required", required);

            declaration.put("parameters", parameters);

            return declaration;

        } catch (JSONException e) {
            Log.e(TAG, "Error creating function declaration", e);
            return new JSONObject();
        }
    }

    @Override
    public CompletableFuture<FunctionResponse> execute(JSONObject args) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String action = args.getString("action");
                String setting = args.getString("setting");

                if ("get".equals(action)) {
                    return getCurrentValue(setting);
                } else if ("set".equals(action)) {
                    String value = args.optString("value", "");
                    return setValue(setting, value);
                } else {
                    return FunctionResponse.error(FUNCTION_NAME, "Unknown action: " + action);
                }

            } catch (JSONException e) {
                Log.e(TAG, "Error parsing arguments", e);
                return FunctionResponse.error(FUNCTION_NAME, "Invalid arguments: " + e.getMessage());
            }
        });
    }

    /**
     * Get current value of a setting.
     */
    private FunctionResponse getCurrentValue(String setting) {
        try {
            UserConfig config = configManager.getActiveConfig();
            Object value = getSettingValue(config, setting);

            JSONObject response = new JSONObject();
            response.put("setting", setting);
            response.put("current_value", value.toString());
            response.put("success", true);

            return FunctionResponse.success(FUNCTION_NAME, response);

        } catch (Exception e) {
            return FunctionResponse.error(FUNCTION_NAME, "Error getting setting: " + e.getMessage());
        }
    }

    /**
     * Set a new value for a setting.
     */
    private FunctionResponse setValue(String setting, String value) {
        try {
            UserConfig config = configManager.getActiveConfig();
            Object oldValue = getSettingValue(config, setting);

            // Convert and validate value
            Object newValue = convertValue(setting, value);

            // Update the setting
            configManager.updateSetting(getConfigKey(setting), newValue);

            JSONObject response = new JSONObject();
            response.put("setting", setting);
            response.put("old_value", oldValue.toString());
            response.put("new_value", newValue.toString());
            response.put("success", true);
            response.put("message", setting + " updated to " + newValue);

            Log.i(TAG, "Updated " + setting + ": " + oldValue + " → " + newValue);

            return FunctionResponse.success(FUNCTION_NAME, response);

        } catch (Exception e) {
            Log.e(TAG, "Error setting value", e);
            return FunctionResponse.error(FUNCTION_NAME, "Error setting value: " + e.getMessage());
        }
    }

    /**
     * Get setting value from config object.
     */
    private Object getSettingValue(UserConfig config, String setting) {
        switch (setting) {
            case "humor": return config.getHumorPercentage();
            case "honesty": return config.getHonestyPercentage();
            case "communication_style": return config.getCommunicationStyle();
            case "nationality": return config.getNationality();
            case "voice": return config.getVoiceName();
            case "haptic_intensity": return config.getHapticIntensity();
            case "screen_reader_verbosity": return config.getScreenReaderVerbosity();
            case "vision_sensitivity": return config.getVisionSensitivity();
            case "navigation_detail_level": return config.getNavigationDetailLevel();
            case "orbit_mode": return config.isOrbitModeEnabled();
            case "color_scheme": return config.getColorScheme();
            default: throw new IllegalArgumentException("Unknown setting: " + setting);
        }
    }

    /**
     * Convert string value to appropriate type.
     */
    private Object convertValue(String setting, String value) {
        switch (setting) {
            case "humor":
            case "honesty":
            case "haptic_intensity":
            case "vision_sensitivity":
                return Integer.parseInt(value);

            case "orbit_mode":
                return Boolean.parseBoolean(value);

            default:
                return value; // String values
        }
    }

    /**
     * Get ConfigManager key for a setting.
     */
    private String getConfigKey(String setting) {
        switch (setting) {
            case "humor": return "humor_percentage";
            case "honesty": return "honesty_percentage";
            case "communication_style": return "communication_style";
            case "nationality": return "nationality";
            case "voice": return "voice_name";
            case "haptic_intensity": return "haptic_intensity";
            case "screen_reader_verbosity": return "screen_reader_verbosity";
            case "vision_sensitivity": return "vision_sensitivity";
            case "navigation_detail_level": return "navigation_detail_level";
            case "orbit_mode": return "orbit_mode_enabled";
            case "color_scheme": return "color_scheme";
            default: return setting;
        }
    }
}
