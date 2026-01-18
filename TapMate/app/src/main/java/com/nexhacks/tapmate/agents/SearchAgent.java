package com.nexhacks.tapmate.agents;

import com.nexhacks.tapmate.utils.Config;
import org.json.JSONArray;
import org.json.JSONObject;
import android.os.Handler;
import android.util.Log;
import java.util.concurrent.ExecutorService;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SearchAgent extends BaseAgent {
    private static final String TAG = "SearchAgent";
    private ExecutorService executorService;
    private OkHttpClient httpClient;
    
    public SearchAgent(Handler mainHandler, AgentCallback callback,
                      ExecutorService executorService) {
        super(mainHandler, callback);
        this.executorService = executorService;
        this.httpClient = new OkHttpClient();
    }
    
    @Override
    public JSONArray getFunctionDeclarations() {
        JSONArray funcs = new JSONArray();
        
        // google_search
        funcs.put(createFunctionDeclaration("google_search",
            "Search Google for information.",
            new String[]{"query"}, new String[]{"query"}));
        
        // weather
        funcs.put(createFunctionDeclaration("weather",
            "Get weather information for a specific location. This uses Google Search to find current weather data.",
            new String[]{"location"}, new String[]{"location"}));
        
        return funcs;
    }
    
    @Override
    public boolean handleFunction(String functionName, JSONObject args, String callId) {
        switch (functionName) {
            case "google_search":
                handleGoogleSearch(args, callId);
                return true;
            case "weather":
                handleWeather(args, callId);
                return true;
            default:
                return false;
        }
    }
    
    @Override
    public String[] getHandledFunctions() {
        return new String[]{"google_search", "weather"};
    }
    
    private void handleGoogleSearch(JSONObject args, String callId) {
        String query = args.optString("query", "");
        if (query.isEmpty()) {
            callback.onError("google_search", "No search query provided", callId);
            return;
        }
        
        executorService.execute(() -> {
            try {
                String result = performGoogleSearch(query);
                mainHandler.post(() -> callback.onResult("google_search", result, callId));
            } catch (Exception e) {
                Log.e(TAG, "Error performing search", e);
                mainHandler.post(() -> callback.onError("google_search", "Error searching: " + e.getMessage(), callId));
            }
        });
    }
    
    private void handleWeather(JSONObject args, String callId) {
        String location = args.optString("location", "");
        executorService.execute(() -> {
            try {
                // Use google_search to get weather information
                String searchQuery = "weather " + location;
                String result = performGoogleSearch(searchQuery);
                mainHandler.post(() -> callback.onResult("weather", result, callId));
            } catch (Exception e) {
                Log.e(TAG, "Error getting weather", e);
                mainHandler.post(() -> {
                    String result = "I couldn't get the weather information. Please try again.";
                    callback.onResult("weather", result, callId);
                });
            }
        });
    }
    
    private String performGoogleSearch(String query) {
        try {
            // Use Google Custom Search API (if configured) or fallback
            // For now, return a simple response - can be enhanced with actual API
            return "Search results for \"" + query + "\": [This is a placeholder. Configure Google Custom Search API for actual results.]";
        } catch (Exception e) {
            Log.e(TAG, "Error in Google Search", e);
            return "Error performing search: " + e.getMessage();
        }
    }
}
