package com.nexhacks.tapmate.gemini;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GeminiClient {
    private static final String TAG = "GeminiClient";
    // Loaded from Config (sourced from .env)
    private static final String API_KEY = com.nexhacks.tapmate.utils.Config.GEMINI_API_KEY; 
    // Using Gemini 2.0 Flash Experimental
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-exp:generateContent?key=" + API_KEY;

    private final OkHttpClient client;

    public GeminiClient() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public interface GeminiCallback {
        void onResponse(String toolName, JSONObject toolArgs);
        void onError(Exception e);
    }

    // Main Agent Entry Point
    public void queryAgent(String userGoal, String screenStateJson, GeminiCallback callback) {
        try {
            JSONObject payload = constructPayload(userGoal, screenStateJson);
            
            Request request = new Request.Builder()
                    .url(BASE_URL)
                    .post(RequestBody.create(payload.toString(), MediaType.get("application/json")))
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError(e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        callback.onError(new IOException("Unexpected code " + response));
                        return;
                    }

                    try {
                        String responseBody = response.body().string();
                        JSONObject json = new JSONObject(responseBody);
                        
                        // Parse Gemini Response
                        // Structure: candidates[0].content.parts[0].functionCall or .text
                        JSONObject candidate = json.getJSONArray("candidates").getJSONObject(0);
                        JSONObject content = candidate.getJSONObject("content");
                        JSONArray parts = content.getJSONArray("parts");
                        
                        // Look for function call first
                        for (int i = 0; i < parts.length(); i++) {
                            JSONObject part = parts.getJSONObject(i);
                            if (part.has("functionCall")) {
                                JSONObject fn = part.getJSONObject("functionCall");
                                String name = fn.getString("name");
                                JSONObject args = fn.optJSONObject("args");
                                if (args == null) {
                                    args = new JSONObject();
                                }
                                callback.onResponse(name, args);
                                return;
                            }
                        }
                        
                        // If no function call, look for text response
                        StringBuilder textResponse = new StringBuilder();
                        for (int i = 0; i < parts.length(); i++) {
                            JSONObject part = parts.getJSONObject(i);
                            if (part.has("text")) {
                                textResponse.append(part.getString("text"));
                            }
                        }
                        
                        if (textResponse.length() > 0) {
                            callback.onResponse("text_response", new JSONObject().put("text", textResponse.toString()));
                        } else {
                            callback.onResponse("text_response", new JSONObject().put("text", "I understand."));
                        }

                    } catch (Exception e) {
                        callback.onError(e);
                    }
                }
            });

        } catch (Exception e) {
            callback.onError(e);
        }
    }

    // Construct the JSON payload with Tools
    private JSONObject constructPayload(String userGoal, String screenState) throws Exception {
        JSONObject payload = new JSONObject();
        
        // 1. System Instruction & User Prompt
        JSONObject userPart = new JSONObject().put("text", 
            "You are TapMate, an Android Accessibility Agent that helps users control their phone through voice commands.\n\n" +
            "User Request: " + userGoal + "\n\n" +
            "Current Screen State (JSON): " + screenState + "\n\n" +
            "Instructions:\n" +
            "- Analyze the screen state to understand what's currently visible\n" +
            "- If the user wants to interact with the screen (click, type, scroll), use the appropriate GUI function\n" +
            "- If the user is asking a question or needs information, respond with text_response\n" +
            "- When clicking, use the 'id' field from screen state nodes. If no ID, try using text or description\n" +
            "- Always provide helpful feedback in your responses\n" +
            "- If you need to save important information (like car details, ETAs), use memory_save\n\n" +
            "Now analyze the request and call the appropriate function:");
            
        JSONArray contents = new JSONArray().put(new JSONObject()
            .put("role", "user")
            .put("parts", new JSONArray().put(userPart)));

        payload.put("contents", contents);

        // 2. Tool Definitions (The "Brain" Configuration)
        JSONArray tools = new JSONArray();
        JSONObject functionDeclarations = new JSONObject();
        JSONArray funcs = new JSONArray();

        // Tool: Click
        funcs.put(new JSONObject()
            .put("name", "gui_click")
            .put("description", "Click an element on the screen given its ID.")
            .put("parameters", new JSONObject()
                .put("type", "OBJECT")
                .put("properties", new JSONObject()
                    .put("node_id", new JSONObject().put("type", "STRING").put("description", "The resource ID of the node to click."))
                )
                .put("required", new JSONArray().put("node_id"))
            ));

        // Tool: Type
        funcs.put(new JSONObject()
            .put("name", "gui_type")
            .put("description", "Type text into an editable field.")
            .put("parameters", new JSONObject()
                .put("type", "OBJECT")
                .put("properties", new JSONObject()
                    .put("node_id", new JSONObject().put("type", "STRING"))
                    .put("text", new JSONObject().put("type", "STRING"))
                )
                .put("required", new JSONArray().put("node_id").put("text"))
            ));

        // Tool: Scroll
        funcs.put(new JSONObject()
            .put("name", "gui_scroll")
            .put("description", "Scroll the screen.")
            .put("parameters", new JSONObject()
                .put("type", "OBJECT")
                .put("properties", new JSONObject()
                    .put("direction", new JSONObject().put("type", "STRING").put("enum", new JSONArray().put("UP").put("DOWN")))
                )
                .put("required", new JSONArray().put("direction"))
            ));
            
        // Tool: Memory Save
        funcs.put(new JSONObject()
            .put("name", "memory_save")
            .put("description", "Save important details like car info or ETA to memory.")
            .put("parameters", new JSONObject()
                .put("type", "OBJECT")
                .put("properties", new JSONObject()
                    .put("key", new JSONObject().put("type", "STRING"))
                    .put("value", new JSONObject().put("type", "STRING"))
                )
                .put("required", new JSONArray().put("key").put("value"))
            ));

        functionDeclarations.put("function_declarations", funcs);
        tools.put(functionDeclarations);
        payload.put("tools", tools);

        return payload;
    }
}
