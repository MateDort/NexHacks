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
            "Search Google for information. Use this for weather, general searches, or any web queries.",
            new String[]{"query"}, new String[]{"query"}));
        
        return funcs;
    }
    
    @Override
    public boolean handleFunction(String functionName, JSONObject args, String callId) {
        // #region agent log
        try {
            java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
            fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " SearchAgent.handleFunction:ENTRY " + 
                "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"H4\",\"location\":\"SearchAgent.java:handleFunction\",\"message\":\"SearchAgent handling function\",\"data\":{\"functionName\":\"" + 
                functionName + "\",\"callId\":\"" + (callId != null ? callId : "null") + "\"},\"timestamp\":" + System.currentTimeMillis() + "}\n");
            fw.close();
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error logging", e);
        }
        // #endregion
        switch (functionName) {
            case "google_search":
                handleGoogleSearch(args, callId);
                return true;
            default:
                return false;
        }
    }
    
    @Override
    public String[] getHandledFunctions() {
        return new String[]{"google_search"};
    }
    
    private void handleGoogleSearch(JSONObject args, String callId) {
        // #region agent log
        try {
            java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
            fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " SearchAgent.handleGoogleSearch:ENTRY " + 
                "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"H4\",\"location\":\"SearchAgent.java:handleGoogleSearch\",\"message\":\"Handling Google search\",\"data\":{\"callId\":\"" + 
                (callId != null ? callId : "null") + "\",\"query\":\"" + args.optString("query", "") + "\"},\"timestamp\":" + System.currentTimeMillis() + "}\n");
            fw.close();
        } catch (Exception e) {}
        // #endregion
        String query = args.optString("query", "");
        if (query.isEmpty()) {
            callback.onError("google_search", "No search query provided", callId);
            return;
        }
        
        executorService.execute(() -> {
            try {
                String result = performGoogleSearch(query);
                // #region agent log
                try {
                    java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                    fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " SearchAgent.handleGoogleSearch:RESULT " + 
                        "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"H4\",\"location\":\"SearchAgent.java:handleGoogleSearch\",\"message\":\"Search result ready\",\"data\":{\"callId\":\"" + 
                        (callId != null ? callId : "null") + "\",\"resultLength\":" + (result != null ? result.length() : 0) + "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                    fw.close();
                } catch (Exception e) {}
                // #endregion
                mainHandler.post(() -> {
                    try {
                        callback.onResult("google_search", result, callId);
                    } catch (Throwable t) {
                        android.util.Log.e(TAG, "Error in callback", t);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error performing search", e);
                mainHandler.post(() -> callback.onError("google_search", "Error searching: " + e.getMessage(), callId));
            }
        });
    }
    
    private String performGoogleSearch(String query) {
        try {
            // Use SerpAPI for real search results
            String apiKey = Config.getOvershootApiKey(); // Using OVERSHOOT_API_KEY for SerpAPI
            
            // If query is about weather, extract location and use weather API
            if (query.toLowerCase().contains("weather")) {
                return performWeatherSearch(query);
            }
            
            // For general searches, use SerpAPI
            String encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");
            String url = "https://serpapi.com/search.json?api_key=" + apiKey + 
                        "&q=" + encodedQuery + 
                        "&num=3&engine=google";
            
            Request request = new Request.Builder()
                .url(url)
                .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String body = response.body().string();
                    JSONObject json = new JSONObject(body);
                    
                    // Check for answer box first (for quick answers)
                    if (json.has("answer_box")) {
                        JSONObject answerBox = json.getJSONObject("answer_box");
                        if (answerBox.has("answer")) {
                            return answerBox.getString("answer");
                        }
                        if (answerBox.has("snippet")) {
                            return answerBox.getString("snippet");
                        }
                    }
                    
                    // Get organic results
                    JSONArray results = json.optJSONArray("organic_results");
                    if (results != null && results.length() > 0) {
                        StringBuilder output = new StringBuilder();
                        output.append("Search results for '").append(query).append("':\n\n");
                        
                        for (int i = 0; i < Math.min(3, results.length()); i++) {
                            JSONObject result = results.getJSONObject(i);
                            output.append(i + 1).append(". ");
                            output.append(result.getString("title")).append("\n");
                            if (result.has("snippet")) {
                                output.append(result.getString("snippet")).append("\n");
                            }
                            output.append("\n");
                        }
                        return output.toString();
                    }
                }
            }
            
            // Fallback: Use Gemini to answer the question
            return useGeminiForSearch(query);
            
        } catch (Exception e) {
            Log.e(TAG, "Error in Google Search", e);
            // Fallback to Gemini
            try {
                return useGeminiForSearch(query);
            } catch (Exception e2) {
                return "I couldn't search for that right now. Error: " + e.getMessage();
            }
        }
    }
    
    private String performWeatherSearch(String query) {
        try {
            // Extract location from query
            String location = query.toLowerCase()
                .replace("weather", "")
                .replace("in", "")
                .replace("what's", "")
                .replace("the", "")
                .replace("?", "")
                .trim();
            
            if (location.isEmpty()) {
                location = "current location";
            }
            
            // Use OpenWeatherMap API (free tier) or wttr.in (no key needed)
            String url = "https://wttr.in/" + java.net.URLEncoder.encode(location, "UTF-8") + "?format=j1";
            
            Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "curl/7.64.1")
                .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String body = response.body().string();
                    JSONObject json = new JSONObject(body);
                    
                    JSONObject current = json.getJSONArray("current_condition").getJSONObject(0);
                    String temp = current.getString("temp_F") + "°F (" + current.getString("temp_C") + "°C)";
                    String condition = current.getJSONArray("weatherDesc").getJSONObject(0).getString("value");
                    String humidity = current.getString("humidity") + "%";
                    String windSpeed = current.getString("windspeedMiles") + " mph";
                    
                    return String.format("Weather in %s:\nTemperature: %s\nCondition: %s\nHumidity: %s\nWind: %s",
                        location, temp, condition, humidity, windSpeed);
                }
            }
            
            // Fallback to Gemini
            return useGeminiForSearch(query);
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting weather", e);
            return useGeminiForSearch(query);
        }
    }
    
    private String useGeminiForSearch(String query) {
        try {
            // Use Gemini API to answer the question
            String apiKey = Config.getGeminiApiKey();
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=" + apiKey;
            
            JSONObject requestBody = new JSONObject();
            JSONArray contents = new JSONArray();
            JSONObject content = new JSONObject();
            JSONArray parts = new JSONArray();
            JSONObject part = new JSONObject();
            part.put("text", "Answer this question concisely in 2-3 sentences: " + query);
            parts.put(part);
            content.put("parts", parts);
            contents.put(content);
            requestBody.put("contents", contents);
            
            Request request = new Request.Builder()
                .url(url)
                .post(okhttp3.RequestBody.create(
                    requestBody.toString(),
                    okhttp3.MediaType.parse("application/json")))
                .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String body = response.body().string();
                    JSONObject json = new JSONObject(body);
                    JSONArray candidates = json.getJSONArray("candidates");
                    if (candidates.length() > 0) {
                        JSONObject candidate = candidates.getJSONObject(0);
                        JSONObject contentObj = candidate.getJSONObject("content");
                        JSONArray partsArray = contentObj.getJSONArray("parts");
                        if (partsArray.length() > 0) {
                            return partsArray.getJSONObject(0).getString("text");
                        }
                    }
                }
            }
            
            return "I couldn't find information about: " + query;
            
        } catch (Exception e) {
            Log.e(TAG, "Error using Gemini", e);
            return "Search error: " + e.getMessage();
        }
    }
}
