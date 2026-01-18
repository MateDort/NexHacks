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
            // Use Google Custom Search API (if configured) or fallback
            // For now, return a simple response - can be enhanced with actual API
            return "Search results for \"" + query + "\": [This is a placeholder. Configure Google Custom Search API for actual results.]";
        } catch (Exception e) {
            Log.e(TAG, "Error in Google Search", e);
            return "Error performing search: " + e.getMessage();
        }
    }
}
