package com.nexhacks.tapmate.agents;

import com.nexhacks.tapmate.memory.AppDatabase;
import com.nexhacks.tapmate.memory.MemoryItem;
import org.json.JSONArray;
import org.json.JSONObject;
import android.os.Handler;
import android.util.Log;
import java.util.concurrent.ExecutorService;

public class MemoryAgent extends BaseAgent {
    private static final String TAG = "MemoryAgent";
    private AppDatabase database;
    private ExecutorService executorService;
    
    public MemoryAgent(Handler mainHandler, AgentCallback callback,
                      AppDatabase database, ExecutorService executorService) {
        super(mainHandler, callback);
        this.database = database;
        this.executorService = executorService;
    }
    
    @Override
    public JSONArray getFunctionDeclarations() {
        try {
            JSONArray funcs = new JSONArray();
            
            // memory_save
            JSONObject saveParams = new JSONObject();
            saveParams.put("key", new JSONObject().put("type", "STRING"));
            saveParams.put("value", new JSONObject().put("type", "STRING"));
            saveParams.put("type", new JSONObject()
                .put("type", "STRING")
                .put("description", "Type of memory: UBER_RIDE, LOCATION, REMINDER, etc."));
            saveParams.put("trigger_time", new JSONObject()
                .put("type", "NUMBER")
                .put("description", "Unix timestamp when to recall this memory (optional)"));
            funcs.put(createFunctionDeclarationWithTypes("memory_save",
                "Save important details like car info or ETA to memory.",
                saveParams, new String[]{"key", "value"}));
            
            // memory_recall
            JSONObject recallParams = new JSONObject();
            recallParams.put("type", new JSONObject()
                .put("type", "STRING")
                .put("description", "Type of memory to recall: UBER_RIDE, LOCATION, REMINDER, etc."));
            funcs.put(createFunctionDeclarationWithTypes("memory_recall",
                "Recall saved information from memory by type.",
                recallParams, new String[]{"type"}));
            
            return funcs;
        } catch (org.json.JSONException e) {
            Log.e(TAG, "Error creating function declarations", e);
            return new JSONArray();
        }
    }
    
    @Override
    public boolean handleFunction(String functionName, JSONObject args, String callId) {
        switch (functionName) {
            case "memory_save":
                handleSave(args, callId);
                return true;
            case "memory_recall":
                handleRecall(args, callId);
                return true;
            default:
                return false;
        }
    }
    
    @Override
    public String[] getHandledFunctions() {
        return new String[]{"memory_save", "memory_recall"};
    }
    
    private void handleSave(JSONObject args, String callId) {
        String key = args.optString("key", "");
        String value = args.optString("value", "");
        String type = args.optString("type", "GENERAL");
        long triggerTime = args.optLong("trigger_time", 0);
        
        if (key.isEmpty() || value.isEmpty()) {
            callback.onError("memory_save", "Missing key or value for memory save", callId);
            return;
        }
        
        executorService.execute(() -> {
            try {
                MemoryItem item = new MemoryItem(
                    type,
                    value,
                    new JSONObject().put("key", key).put("value", value).toString(),
                    System.currentTimeMillis(),
                    triggerTime
                );
                database.memoryDao().insert(item);
                String result = "Saved to memory: " + key;
                mainHandler.post(() -> callback.onResult("memory_save", result, callId));
            } catch (Exception e) {
                Log.e(TAG, "Error saving memory", e);
                mainHandler.post(() -> callback.onError("memory_save", "Error saving memory: " + e.getMessage(), callId));
            }
        });
    }
    
    private void handleRecall(JSONObject args, String callId) {
        String recallType = args.optString("type", "");
        executorService.execute(() -> {
            try {
                MemoryItem item = null;
                if (!recallType.isEmpty()) {
                    item = database.memoryDao().getLastItemByType(recallType);
                }
                final MemoryItem finalItem = item;
                mainHandler.post(() -> {
                    if (finalItem != null) {
                        String result = "I recall: " + finalItem.rawText;
                        callback.onResult("memory_recall", result, callId);
                    } else {
                        String result = "No memory found for type: " + recallType;
                        callback.onResult("memory_recall", result, callId);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error recalling memory", e);
                mainHandler.post(() -> callback.onError("memory_recall", "Error recalling memory: " + e.getMessage(), callId));
            }
        });
    }
}
