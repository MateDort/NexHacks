package com.nexhacks.tapmate.gemini;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class GeminiLiveClient extends WebSocketListener {
    private static final String TAG = "GeminiLiveClient";
    // API key loaded dynamically (not static to avoid initialization before loadEnv)
    private String getApiKey() {
        return com.nexhacks.tapmate.utils.Config.GEMINI_API_KEY;
    }
    
    // Build WebSocket URL dynamically with current API key
    // Correct Gemini Live API endpoint format
    private String buildWebSocketUrl() {
        String apiKey = getApiKey();
        // #region agent log
        try {
            java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
            fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " GeminiLiveClient.buildWebSocketUrl:API_KEY_CHECK " + 
                "{\"sessionId\":\"debug-session\",\"runId\":\"run2\",\"hypothesisId\":\"A\",\"location\":\"GeminiLiveClient.java:buildWebSocketUrl\",\"message\":\"API key check\",\"data\":{\"apiKeyLength\":" + 
                (apiKey != null ? apiKey.length() : 0) + ",\"apiKeyEmpty\":" + (apiKey == null || apiKey.isEmpty()) + "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
            fw.close();
        } catch (Exception e) {}
        // #endregion
        // Correct Gemini Live API WebSocket endpoint format
        // Based on official docs: wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent
        // API key can be passed as query parameter or in Authorization header
        String endpoint = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=" + apiKey;
        // #region agent log
        try {
            java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
            fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " GeminiLiveClient.buildWebSocketUrl:ENDPOINT_BUILT " + 
                "{\"sessionId\":\"debug-session\",\"runId\":\"run4\",\"hypothesisId\":\"B\",\"location\":\"GeminiLiveClient.java:buildWebSocketUrl\",\"message\":\"WebSocket endpoint built\",\"data\":{\"endpoint\":\"" + 
                endpoint.replace(apiKey, "REDACTED") + "\"},\"timestamp\":" + System.currentTimeMillis() + "}\n");
            fw.close();
        } catch (Exception e) {}
        // #endregion
        return endpoint;
    }
    
    private WebSocket webSocket;
    private final OkHttpClient client;
    private GeminiLiveCallback callback;
    private boolean isConnected = false;
    private String sessionId = null;
    private com.nexhacks.tapmate.agents.AgentRegistry agentRegistry;
    
    public interface GeminiLiveCallback {
        void onAudioChunk(byte[] audioData);
        void onTextResponse(String text);
        void onFunctionCall(String functionName, JSONObject args, String callId);
        void onError(Exception e);
        void onConnected();
        void onDisconnected();
    }
    
    public GeminiLiveClient() {
        this(null);
    }
    
    public GeminiLiveClient(com.nexhacks.tapmate.agents.AgentRegistry agentRegistry) {
        this.agentRegistry = agentRegistry;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS) // No timeout for streaming
                .writeTimeout(0, TimeUnit.SECONDS)
                .build();
    }
    
    public void startSession(GeminiLiveCallback callback, String screenStateJson) {
        this.callback = callback;
        this.currentScreenState = screenStateJson != null ? screenStateJson : "[]";
        this.setupSent = false;
        
        String wsUrl = buildWebSocketUrl();
        // #region agent log
        try {
            java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
            fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " GeminiLiveClient.startSession:WS_URL_BUILT " + 
                "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"B\",\"location\":\"GeminiLiveClient.java:startSession\",\"message\":\"WebSocket URL built\",\"data\":{\"urlLength\":" + 
                wsUrl.length() + ",\"urlPrefix\":\"" + (wsUrl.length() > 50 ? wsUrl.substring(0, 50) : wsUrl) + "...\"},\"timestamp\":" + System.currentTimeMillis() + "}\n");
            fw.close();
        } catch (Exception e) {}
        // #endregion
        
        Request request = new Request.Builder()
                .url(wsUrl)
                .build();
        
        // #region agent log
        try {
            java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
            fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " GeminiLiveClient.startSession:WS_CONNECT_ATTEMPT " + 
                "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"C\",\"location\":\"GeminiLiveClient.java:startSession\",\"message\":\"WebSocket connection attempt\",\"data\":{},\"timestamp\":" + System.currentTimeMillis() + "}\n");
            fw.close();
        } catch (Exception e) {}
        // #endregion
        
        webSocket = client.newWebSocket(request, this);
    }
    
    private String currentScreenState = "[]";
    private boolean setupSent = false;
    
    public void sendFunctionResponse(String functionName, JSONObject response, String callId) {
        if (webSocket == null || !isConnected) {
            Log.w(TAG, "WebSocket not connected, cannot send function response");
            return;
        }
        
        try {
            JSONObject realtimeInputWrapper = new JSONObject();
            JSONObject realtimeInput = new JSONObject();
            JSONObject functionResponse = new JSONObject();
            functionResponse.put("name", functionName);
            if (callId != null && !callId.isEmpty()) {
                functionResponse.put("id", callId);
            }
            functionResponse.put("response", response);
            realtimeInput.put("functionResponse", functionResponse);
            realtimeInputWrapper.put("realtimeInput", realtimeInput);
            
            String messageJson = realtimeInputWrapper.toString();
            webSocket.send(messageJson);
            Log.d(TAG, "Sent function response for: " + functionName + (callId != null ? " (id: " + callId + ")" : ""));
            // #region agent log
            try {
                android.util.Log.d("GeminiLiveClient", "FUNCTION_RESPONSE_SENT: " + functionName + " id:" + callId + " -> " + response.toString());
            } catch (Exception e) {}
            // #endregion
        } catch (Exception e) {
            Log.e(TAG, "Error sending function response", e);
        }
    }
    
    public void sendAudioChunk(byte[] audioData) {
        if (webSocket == null || !isConnected) {
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " GeminiLiveClient.sendAudioChunk:WS_NOT_READY " + 
                    "{\"sessionId\":\"debug-session\",\"runId\":\"run6\",\"hypothesisId\":\"Q\",\"location\":\"GeminiLiveClient.java:sendAudioChunk\",\"message\":\"WebSocket not ready\",\"data\":{\"webSocketNull\":" + 
                    (webSocket == null) + ",\"isConnected\":" + isConnected + "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                fw.close();
            } catch (Exception e) {}
            // #endregion
            Log.w(TAG, "WebSocket not connected, cannot send audio");
            return;
        }
        
        try {
            // Send setup message on first audio chunk
            if (!setupSent) {
                // #region agent log
                try {
                    java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                    fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " GeminiLiveClient.sendAudioChunk:SENDING_SETUP " + 
                        "{\"sessionId\":\"debug-session\",\"runId\":\"run6\",\"hypothesisId\":\"R\",\"location\":\"GeminiLiveClient.java:sendAudioChunk\",\"message\":\"Sending setup message\",\"data\":{},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                    fw.close();
                } catch (Exception e) {}
                // #endregion
                JSONObject setup = new JSONObject();
                JSONObject setupContent = new JSONObject();
                // Use Live-enabled model name
                setupContent.put("model", "models/gemini-2.0-flash-exp"); // Try with live model if available: "models/gemini-live-2.5-flash"
                
                JSONObject genConfig = new JSONObject();
                // Use response_modalities (plural) as array
                genConfig.put("response_modalities", new JSONArray().put("AUDIO"));
                JSONObject speechConfig = new JSONObject();
                JSONObject voiceConfig = new JSONObject();
                JSONObject prebuiltVoice = new JSONObject();
                prebuiltVoice.put("voice_name", "Aoede");
                voiceConfig.put("prebuilt_voice_config", prebuiltVoice);
                speechConfig.put("voice_config", voiceConfig);
                genConfig.put("speech_config", speechConfig);
                setupContent.put("generation_config", genConfig);
                
                JSONArray tools = createTools();
                JSONObject systemInstruction = createSystemInstruction(currentScreenState);
                setupContent.put("tools", tools);
                setupContent.put("system_instruction", systemInstruction);
                setup.put("setup", setupContent);
                
                String setupJson = setup.toString();
                webSocket.send(setupJson);
                setupSent = true;
                Log.d(TAG, "Setup message sent");
                // #region agent log
                try {
                    java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                    fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " GeminiLiveClient.sendAudioChunk:SETUP_SENT " + 
                        "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"H3\",\"location\":\"GeminiLiveClient.java:sendAudioChunk\",\"message\":\"Setup message sent\",\"data\":{\"toolsCount\":" + 
                        tools.length() + ",\"hasSystemInstruction\":" + (systemInstruction != null) + ",\"setupPreview\":\"" + setupJson.substring(0, Math.min(500, setupJson.length())).replace("\"", "\\\"") + "...\"},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                    fw.close();
                } catch (Exception e) {}
                // #endregion
            }
            
            // Send audio data using realtimeInput format (correct format for Gemini Live API)
            String base64Audio = android.util.Base64.encodeToString(audioData, android.util.Base64.NO_WRAP);
            JSONObject realtimeInputWrapper = new JSONObject();
            JSONObject realtimeInput = new JSONObject();
            JSONObject audioObj = new JSONObject();
            audioObj.put("data", base64Audio);
            audioObj.put("mimeType", "audio/pcm;rate=16000");
            realtimeInput.put("audio", audioObj);
            realtimeInputWrapper.put("realtimeInput", realtimeInput);
            
            String messageJson = realtimeInputWrapper.toString();
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                // Check if audio data has non-zero bytes
                boolean hasNonZero = false;
                for (int i = 0; i < Math.min(audioData.length, 100); i++) {
                    if (audioData[i] != 0) {
                        hasNonZero = true;
                        break;
                    }
                }
                fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " GeminiLiveClient.sendAudioChunk:AUDIO_PREP " + 
                    "{\"sessionId\":\"debug-session\",\"runId\":\"run9\",\"hypothesisId\":\"H2\",\"location\":\"GeminiLiveClient.java:sendAudioChunk\",\"message\":\"Audio chunk prepared\",\"data\":{\"audioSize\":" + 
                    audioData.length + ",\"hasNonZeroBytes\":" + hasNonZero + ",\"messageStructure\":\"" + messageJson.substring(0, Math.min(200, messageJson.length())).replace("\"", "\\\"") + "...\"},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                fw.close();
            } catch (Exception e) {}
            // #endregion
            
            webSocket.send(messageJson);
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " GeminiLiveClient.sendAudioChunk:AUDIO_SENT " + 
                    "{\"sessionId\":\"debug-session\",\"runId\":\"run9\",\"hypothesisId\":\"H4\",\"location\":\"GeminiLiveClient.java:sendAudioChunk\",\"message\":\"Audio chunk sent via WebSocket\",\"data\":{\"audioSize\":" + 
                    audioData.length + ",\"base64Size\":" + base64Audio.length() + "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                fw.close();
            } catch (Exception e) {}
            // #endregion
            
        } catch (Exception e) {
            Log.e(TAG, "Error sending audio chunk", e);
            if (callback != null) {
                callback.onError(e);
            }
        }
    }
    
    private JSONArray createTools() {
        try {
            JSONArray tools = new JSONArray();
            JSONObject functionDeclarations = new JSONObject();
            JSONArray funcs;
            
            // Use AgentRegistry if available, otherwise fall back to manual definitions
            if (agentRegistry != null) {
                funcs = agentRegistry.getAllFunctionDeclarations();
                Log.d(TAG, "Using AgentRegistry: " + funcs.length() + " functions");
            } else {
                funcs = createToolsManually();
                Log.d(TAG, "Using manual tool definitions: " + funcs.length() + " functions");
            }
            
            functionDeclarations.put("function_declarations", funcs);
            tools.put(functionDeclarations);
            return tools;
        } catch (org.json.JSONException e) {
            Log.e(TAG, "Error creating tools", e);
            return new JSONArray();
        }
    }
    
    private JSONArray createToolsManually() {
        try {
            JSONArray funcs = new JSONArray();
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " GeminiLiveClient.createTools:START " + 
                    "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"H3\",\"location\":\"GeminiLiveClient.java:createTools\",\"message\":\"Creating tools array\",\"data\":{},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                fw.close();
            } catch (Exception e) {}
            // #endregion
            
            // Tool: Click
            JSONObject clickTool = new JSONObject();
            clickTool.put("name", "gui_click");
            clickTool.put("description", "Click an element on the screen given its ID or text.");
            JSONObject clickParams = new JSONObject();
            clickParams.put("type", "OBJECT");
            JSONObject clickProps = new JSONObject();
            JSONObject nodeIdProp = new JSONObject();
            nodeIdProp.put("type", "STRING");
            nodeIdProp.put("description", "The resource ID or text of the node to click.");
            clickProps.put("node_id", nodeIdProp);
            clickParams.put("properties", clickProps);
            clickParams.put("required", new JSONArray().put("node_id"));
            clickTool.put("parameters", clickParams);
            funcs.put(clickTool);
            
            // Tool: Type
            JSONObject typeTool = new JSONObject();
            typeTool.put("name", "gui_type");
            typeTool.put("description", "Type text into an editable field.");
            JSONObject typeParams = new JSONObject();
            typeParams.put("type", "OBJECT");
            JSONObject typeProps = new JSONObject();
            typeProps.put("node_id", new JSONObject().put("type", "STRING"));
            typeProps.put("text", new JSONObject().put("type", "STRING"));
            typeParams.put("properties", typeProps);
            typeParams.put("required", new JSONArray().put("node_id").put("text"));
            typeTool.put("parameters", typeParams);
            funcs.put(typeTool);
            
            // Tool: Scroll
            JSONObject scrollTool = new JSONObject();
            scrollTool.put("name", "gui_scroll");
            scrollTool.put("description", "Scroll the screen up or down.");
            JSONObject scrollParams = new JSONObject();
            scrollParams.put("type", "OBJECT");
            JSONObject scrollProps = new JSONObject();
            JSONObject directionProp = new JSONObject();
            directionProp.put("type", "STRING");
            directionProp.put("enum", new JSONArray().put("UP").put("DOWN"));
            scrollProps.put("direction", directionProp);
            scrollParams.put("properties", scrollProps);
            scrollParams.put("required", new JSONArray().put("direction"));
            scrollTool.put("parameters", scrollParams);
            funcs.put(scrollTool);
            
            // Tool: Memory Save
            JSONObject memoryTool = new JSONObject();
            memoryTool.put("name", "memory_save");
            memoryTool.put("description", "Save important details like car info or ETA to memory.");
            JSONObject memoryParams = new JSONObject();
            memoryParams.put("type", "OBJECT");
            JSONObject memoryProps = new JSONObject();
            memoryProps.put("key", new JSONObject().put("type", "STRING"));
            memoryProps.put("value", new JSONObject().put("type", "STRING"));
            memoryProps.put("type", new JSONObject().put("type", "STRING").put("description", "Type of memory: UBER_RIDE, LOCATION, REMINDER, etc."));
            memoryProps.put("trigger_time", new JSONObject().put("type", "NUMBER").put("description", "Unix timestamp when to recall this memory (optional)"));
            memoryParams.put("properties", memoryProps);
            memoryParams.put("required", new JSONArray().put("key").put("value"));
            memoryTool.put("parameters", memoryParams);
            funcs.put(memoryTool);
            
            // Tool: Memory Recall
            JSONObject recallTool = new JSONObject();
            recallTool.put("name", "memory_recall");
            recallTool.put("description", "Recall saved information from memory by type.");
            JSONObject recallParams = new JSONObject();
            recallParams.put("type", "OBJECT");
            JSONObject recallProps = new JSONObject();
            recallProps.put("type", new JSONObject().put("type", "STRING").put("description", "Type of memory to recall: UBER_RIDE, LOCATION, REMINDER, etc."));
            recallParams.put("properties", recallProps);
            recallParams.put("required", new JSONArray().put("type"));
            recallTool.put("parameters", recallParams);
            funcs.put(recallTool);
            
            // Tool: Google Search
            JSONObject searchTool = new JSONObject();
            searchTool.put("name", "google_search");
            searchTool.put("description", "Search Google for information.");
            JSONObject searchParams = new JSONObject();
            searchParams.put("type", "OBJECT");
            JSONObject searchProps = new JSONObject();
            searchProps.put("query", new JSONObject().put("type", "STRING").put("description", "The search query"));
            searchParams.put("properties", searchProps);
            searchParams.put("required", new JSONArray().put("query"));
            searchTool.put("parameters", searchParams);
            funcs.put(searchTool);
            
            // Tool: Maps Navigation
            JSONObject mapsTool = new JSONObject();
            mapsTool.put("name", "maps_navigation");
            mapsTool.put("description", "Get walking directions to a destination using Google Maps.");
            JSONObject mapsParams = new JSONObject();
            mapsParams.put("type", "OBJECT");
            JSONObject mapsProps = new JSONObject();
            mapsProps.put("destination", new JSONObject().put("type", "STRING").put("description", "Destination address or place name"));
            mapsParams.put("properties", mapsProps);
            mapsParams.put("required", new JSONArray().put("destination"));
            mapsTool.put("parameters", mapsParams);
            funcs.put(mapsTool);
            
            // Tool: Get Location
            JSONObject locationTool = new JSONObject();
            locationTool.put("name", "get_location");
            locationTool.put("description", "Get the user's current GPS location coordinates.");
            locationTool.put("parameters", new JSONObject().put("type", "OBJECT").put("properties", new JSONObject()));
            funcs.put(locationTool);
            
            // Tool: Weather (uses google_search internally)
            JSONObject weatherTool = new JSONObject();
            weatherTool.put("name", "weather");
            weatherTool.put("description", "Get weather information for a specific location. This uses Google Search to find current weather data.");
            JSONObject weatherParams = new JSONObject();
            weatherParams.put("type", "OBJECT");
            JSONObject weatherProps = new JSONObject();
            weatherProps.put("location", new JSONObject().put("type", "STRING").put("description", "City name or location (e.g., 'Atlanta, GA' or 'New York')"));
            weatherParams.put("properties", weatherProps);
            weatherParams.put("required", new JSONArray().put("location"));
            weatherTool.put("parameters", weatherParams);
            funcs.put(weatherTool);
            
            // Tool: Open App
            JSONObject openAppTool = new JSONObject();
            openAppTool.put("name", "gui_open_app");
            openAppTool.put("description", "Open an app on the phone by name. Use this when the user asks to open an app that's not currently visible on screen.");
            JSONObject openAppParams = new JSONObject();
            openAppParams.put("type", "OBJECT");
            JSONObject openAppProps = new JSONObject();
            openAppProps.put("app_name", new JSONObject().put("type", "STRING").put("description", "Name of the app to open (e.g., 'Messenger', 'Settings', 'Chrome')"));
            openAppParams.put("properties", openAppProps);
            openAppParams.put("required", new JSONArray().put("app_name"));
            openAppTool.put("parameters", openAppParams);
            funcs.put(openAppTool);
            
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " GeminiLiveClient.createTools:COMPLETE " + 
                    "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"H3\",\"location\":\"GeminiLiveClient.java:createTools\",\"message\":\"Tools created\",\"data\":{\"functionsCount\":" + 
                    funcs.length() + "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                fw.close();
            } catch (Exception e) {}
            // #endregion
            return funcs;
        } catch (org.json.JSONException e) {
            Log.e(TAG, "Error creating tools manually", e);
            return new JSONArray();
        }
    }
    
    private JSONObject createSystemInstruction(String screenStateJson) {
        try {
            JSONObject instruction = new JSONObject();
            JSONArray parts = new JSONArray();
            JSONObject textPart = new JSONObject();
            textPart.put("text", "You are TapMate, an Android Accessibility Agent that helps users control their phone through voice commands.\n\n" +
                "Current Screen State (JSON): " + screenStateJson + "\n\n" +
                "Instructions:\n" +
                "- Analyze the screen state to understand what's currently visible\n" +
                "- If the user wants to interact with the screen (click, type, scroll), use the appropriate GUI function\n" +
                "- When clicking, use the 'id' field from screen state nodes. If no ID, try using text or description\n" +
                "- If the user asks to open an app that's NOT on the current screen, use gui_open_app to launch it\n" +
                "- Always provide helpful feedback in your responses\n" +
                "- If you need to save important information (like car details, ETAs), use memory_save\n" +
                "- Use memory_recall to retrieve saved information when needed\n" +
                "- Use google_search to find information on the web\n" +
                "- Use maps_navigation to get directions to a location\n" +
                "- Use get_location to find out where the user is\n" +
                "- Use weather to get weather information for any location (it uses Google Search internally)\n" +
                "- Alternatively, you can use google_search directly for weather queries\n" +
                "- If the user asks to open an app that's NOT on the current screen, use gui_open_app to launch it");
            parts.put(textPart);
            instruction.put("parts", parts);
            return instruction;
        } catch (org.json.JSONException e) {
            Log.e(TAG, "Error creating system instruction", e);
            return new JSONObject();
        }
    }
    
    public void stopSession() {
        if (webSocket != null) {
            webSocket.close(1000, "Session ended");
            webSocket = null;
        }
        isConnected = false;
        sessionId = null;
        setupSent = false;
    }
    
    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        Log.d(TAG, "WebSocket connected");
        // #region agent log
        try {
            java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
            int responseCode = response != null ? response.code() : -1;
            fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " GeminiLiveClient.onOpen:WS_CONNECTED " + 
                "{\"sessionId\":\"debug-session\",\"runId\":\"run6\",\"hypothesisId\":\"E\",\"location\":\"GeminiLiveClient.java:onOpen\",\"message\":\"WebSocket connected\",\"data\":{\"responseCode\":" + 
                responseCode + "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
            fw.close();
        } catch (Exception e) {}
        // #endregion
        isConnected = true;
        if (callback != null) {
            callback.onConnected();
        }
    }
    
    @Override
    public void onMessage(WebSocket webSocket, String text) {
        // #region agent log
        try {
            java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
            String preview = text != null && text.length() > 0 ? text.substring(0, Math.min(500, text.length())).replace("\"", "\\\"") : "null";
            boolean hasAudio = text != null && (text.contains("audio") || text.contains("data") || text.contains("base64"));
            fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " GeminiLiveClient.onMessage:TEXT_RECEIVED " + 
                "{\"sessionId\":\"debug-session\",\"runId\":\"run12\",\"hypothesisId\":\"AUDIO_FORMAT\",\"location\":\"GeminiLiveClient.java:onMessage\",\"message\":\"Text message from Gemini\",\"data\":{\"messageLength\":" + 
                (text != null ? text.length() : 0) + ",\"hasAudioFields\":" + hasAudio + ",\"preview\":\"" + preview + "...\"},\"timestamp\":" + System.currentTimeMillis() + "}\n");
            fw.close();
        } catch (Exception ex) {}
        // #endregion
        try {
            JSONObject message = new JSONObject(text);
            // Check if message contains audio data in JSON format
            if (message.has("serverContent") || message.has("modelTurn")) {
                JSONObject serverContent = message.optJSONObject("serverContent");
                if (serverContent == null && message.has("modelTurn")) {
                    JSONObject modelTurn = message.getJSONObject("modelTurn");
                    if (modelTurn.has("parts")) {
                        JSONArray parts = modelTurn.getJSONArray("parts");
                        for (int i = 0; i < parts.length(); i++) {
                            JSONObject part = parts.getJSONObject(i);
                            if (part.has("inlineData")) {
                                JSONObject inlineData = part.getJSONObject("inlineData");
                                if (inlineData.has("data") && inlineData.optString("mimeType", "").startsWith("audio/")) {
                                    // Audio data in base64 format
                                    String base64Audio = inlineData.getString("data");
                                    byte[] audioBytes = android.util.Base64.decode(base64Audio, android.util.Base64.NO_WRAP);
                                    // #region agent log
                                    try {
                                        java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                                        fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " GeminiLiveClient.onMessage:AUDIO_IN_JSON " + 
                                            "{\"sessionId\":\"debug-session\",\"runId\":\"run12\",\"hypothesisId\":\"AUDIO_FORMAT\",\"location\":\"GeminiLiveClient.java:onMessage\",\"message\":\"Found audio in JSON message\",\"data\":{\"audioSize\":" + 
                                            audioBytes.length + ",\"mimeType\":\"" + inlineData.optString("mimeType", "unknown") + "\"},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                                        fw.close();
                                    } catch (Exception ex) {}
                                    // #endregion
                                    if (callback != null) {
                                        callback.onAudioChunk(audioBytes);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // Log full message structure to see function calls
            if (message.toString().contains("functionCalls") || message.toString().contains("functionCall")) {
                Log.d(TAG, "=== MESSAGE WITH FUNCTION CALLS ===");
                Log.d(TAG, "Full message: " + message.toString());
            }
            handleServerMessage(message);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing message", e);
            Log.e(TAG, "Message that failed: " + text);
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " GeminiLiveClient.onMessage:PARSE_ERROR " + 
                    "{\"sessionId\":\"debug-session\",\"runId\":\"run12\",\"hypothesisId\":\"AUDIO_FORMAT\",\"location\":\"GeminiLiveClient.java:onMessage\",\"message\":\"Error parsing message\",\"data\":{\"error\":\"" + 
                    e.getMessage() + "\"},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                fw.close();
            } catch (Exception ex) {}
            // #endregion
        }
    }
    
    @Override
    public void onMessage(WebSocket webSocket, ByteString bytes) {
        // Handle binary audio data - but check if it's actually JSON text first
        byte[] dataBytes = bytes != null ? bytes.toByteArray() : new byte[0];
        Log.d(TAG, "Received binary message: " + dataBytes.length + " bytes");
        
        // Check if this is actually JSON text (starts with '{' or '[')
        if (dataBytes.length > 0 && (dataBytes[0] == '{' || dataBytes[0] == '[')) {
            // This is JSON text, not binary audio - parse it
            try {
                String jsonText = new String(dataBytes, java.nio.charset.StandardCharsets.UTF_8);
                Log.d(TAG, "Binary message is actually JSON, parsing: " + jsonText.substring(0, Math.min(200, jsonText.length())));
                // Log if contains function calls
                if (jsonText.contains("functionCalls") || jsonText.contains("functionCall")) {
                    Log.d(TAG, "=== BINARY JSON WITH FUNCTION CALLS ===");
                    Log.d(TAG, "Full JSON: " + jsonText);
                }
                JSONObject message = new JSONObject(jsonText);
                handleServerMessage(message);
            } catch (Exception e) {
                Log.e(TAG, "Error parsing JSON from binary message", e);
            }
            return;
        }
        
        // This is actual binary audio data
        byte[] audioBytes = dataBytes;
        Log.d(TAG, "Received binary audio: " + audioBytes.length + " bytes");
        // #region agent log
        try {
            java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
            // Analyze first few bytes to check if it's valid PCM
            String firstBytes = "";
            int minLen = Math.min(audioBytes.length, 20);
            for (int i = 0; i < minLen; i++) {
                firstBytes += String.format("%02X ", audioBytes[i] & 0xFF);
            }
            // Check if data looks like PCM (should have variation, not all zeros or all same)
            boolean allZeros = true;
            boolean allSame = true;
            if (audioBytes.length > 0) {
                byte first = audioBytes[0];
                for (int i = 0; i < Math.min(audioBytes.length, 100); i++) {
                    if (audioBytes[i] != 0) allZeros = false;
                    if (audioBytes[i] != first) allSame = false;
                }
            }
            fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " GeminiLiveClient.onMessage:BINARY_RECEIVED " + 
                "{\"sessionId\":\"debug-session\",\"runId\":\"run12\",\"hypothesisId\":\"AUDIO_FORMAT\",\"location\":\"GeminiLiveClient.java:onMessage\",\"message\":\"Binary audio message from Gemini\",\"data\":{\"bytesSize\":" + 
                audioBytes.length + ",\"firstBytes\":\"" + firstBytes.trim() + "\",\"allZeros\":" + allZeros + ",\"allSame\":" + allSame + "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
            fw.close();
        } catch (Exception ex) {}
        // #endregion
        if (callback != null && audioBytes.length > 0) {
            callback.onAudioChunk(audioBytes);
        }
    }
    
    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        Log.d(TAG, "WebSocket closing: " + reason);
        // #region agent log
        try {
            java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
            fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " GeminiLiveClient.onClosing:WS_CLOSING " + 
                "{\"sessionId\":\"debug-session\",\"runId\":\"run8\",\"hypothesisId\":\"H1\",\"location\":\"GeminiLiveClient.java:onClosing\",\"message\":\"WebSocket closing\",\"data\":{\"code\":" + 
                code + ",\"reason\":\"" + (reason != null ? reason.replace("\"", "\\\"") : "null") + "\"},\"timestamp\":" + System.currentTimeMillis() + "}\n");
            fw.close();
        } catch (Exception e) {}
        // #endregion
        isConnected = false;
    }
    
    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
        Log.d(TAG, "WebSocket closed");
        // #region agent log
        try {
            java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
            fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " GeminiLiveClient.onClosed:WS_CLOSED " + 
                "{\"sessionId\":\"debug-session\",\"runId\":\"run8\",\"hypothesisId\":\"H1\",\"location\":\"GeminiLiveClient.java:onClosed\",\"message\":\"WebSocket closed\",\"data\":{\"code\":" + 
                code + ",\"reason\":\"" + (reason != null ? reason.replace("\"", "\\\"") : "null") + "\"},\"timestamp\":" + System.currentTimeMillis() + "}\n");
            fw.close();
        } catch (Exception e) {}
        // #endregion
        isConnected = false;
        if (callback != null) {
            callback.onDisconnected();
        }
    }
    
    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        Log.e(TAG, "WebSocket failure", t);
        // #region agent log
        try {
            java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
            String errorMsg = t != null ? t.getMessage() : "null";
            String errorClass = t != null ? t.getClass().getName() : "null";
            int responseCode = response != null ? response.code() : -1;
            String responseMsg = response != null ? response.message() : "null";
            fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " GeminiLiveClient.onFailure:WS_ERROR " + 
                "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"D\",\"location\":\"GeminiLiveClient.java:onFailure\",\"message\":\"WebSocket failure\",\"data\":{\"errorClass\":\"" + 
                errorClass + "\",\"errorMessage\":\"" + errorMsg + "\",\"responseCode\":" + responseCode + ",\"responseMessage\":\"" + responseMsg + "\"},\"timestamp\":" + System.currentTimeMillis() + "}\n");
            fw.close();
        } catch (Exception e) {}
        // #endregion
        isConnected = false;
        if (callback != null) {
            callback.onError(new Exception("WebSocket error", t));
        }
    }
    
    private void handleServerMessage(JSONObject message) {
        // Log message structure for debugging
        Log.d(TAG, "Handling server message. Keys: " + message.keys());
        Log.d(TAG, "Message preview: " + message.toString().substring(0, Math.min(1000, message.toString().length())));
        
        // #region agent log
        try {
            java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
            java.util.Iterator<String> keysIter = message.keys();
            java.util.ArrayList<String> keysList = new java.util.ArrayList<>();
            while (keysIter.hasNext()) {
                keysList.add(keysIter.next());
            }
            String keys = String.join(",", keysList);
            String fullMessage = message.toString().substring(0, Math.min(500, message.toString().length()));
            fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " GeminiLiveClient.handleServerMessage:ENTRY " + 
                "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"H3\",\"location\":\"GeminiLiveClient.java:handleServerMessage\",\"message\":\"Handling server message\",\"data\":{\"hasModelTurn\":" + 
                message.has("model_turn") + ",\"hasSetupComplete\":" + message.has("setupComplete") + ",\"hasServerContent\":" + message.has("serverContent") + ",\"hasFunctionCalls\":" + message.has("functionCalls") + ",\"messageKeys\":\"" + keys + "\",\"messagePreview\":\"" + fullMessage.replace("\"", "\\\"") + "...\"},\"timestamp\":" + System.currentTimeMillis() + "}\n");
            fw.close();
        } catch (Exception ex) {
            Log.e(TAG, "Error logging message entry", ex);
        }
        // #endregion
        try {
            // Check for functionCalls array at top level (new format)
            if (message.has("functionCalls")) {
                Log.d(TAG, "Found functionCalls array at top level");
                JSONArray functionCalls = message.getJSONArray("functionCalls");
                for (int i = 0; i < functionCalls.length(); i++) {
                    JSONObject fnCall = functionCalls.getJSONObject(i);
                    String name = fnCall.getString("name");
                    JSONObject args = fnCall.optJSONObject("args");
                    if (args == null) {
                        args = new JSONObject();
                    }
                    String callId = fnCall.optString("id", null);
                    if (callId == null || callId.isEmpty()) {
                        callId = fnCall.optString("callId", null);
                    }
                    Log.d(TAG, "Processing function call: " + name + " with args: " + args + " id: " + callId);
                    if (callback != null) {
                        try {
                            callback.onFunctionCall(name, args, callId);
                        } catch (Throwable t) {
                            Log.e(TAG, "Error invoking onFunctionCall from functionCalls array", t);
                        }
                    }
                }
            }
            // Check for setupComplete message
            if (message.has("setupComplete")) {
                // #region agent log
                try {
                    java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                    fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " GeminiLiveClient.handleServerMessage:SETUP_COMPLETE " + 
                        "{\"sessionId\":\"debug-session\",\"runId\":\"run10\",\"hypothesisId\":\"H3\",\"location\":\"GeminiLiveClient.java:handleServerMessage\",\"message\":\"Setup complete received\",\"data\":{},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                    fw.close();
                } catch (Exception ex) {}
                // #endregion
                Log.d(TAG, "Setup complete");
            }
            
            // Handle serverContent (contains audio data in base64)
            if (message.has("serverContent")) {
                JSONObject serverContent = message.getJSONObject("serverContent");
                if (serverContent.has("modelTurn")) {
                    JSONObject modelTurn = serverContent.getJSONObject("modelTurn");
                    if (modelTurn.has("parts")) {
                        JSONArray parts = modelTurn.getJSONArray("parts");
                        for (int i = 0; i < parts.length(); i++) {
                            JSONObject part = parts.getJSONObject(i);
                            
                            // Check for inlineData with audio
                            if (part.has("inlineData")) {
                                JSONObject inlineData = part.getJSONObject("inlineData");
                                String mimeType = inlineData.optString("mimeType", "");
                                if (mimeType.startsWith("audio/") && inlineData.has("data")) {
                                    // Extract base64 audio data
                                    String base64Audio = inlineData.getString("data");
                                    byte[] audioBytes = android.util.Base64.decode(base64Audio, android.util.Base64.NO_WRAP);
                                    Log.d(TAG, "Extracted audio from serverContent: " + audioBytes.length + " bytes, mimeType: " + mimeType);
                                    if (callback != null && audioBytes.length > 0) {
                                        callback.onAudioChunk(audioBytes);
                                    }
                                }
                            }
                            
                            // Check for text
                            if (part.has("text")) {
                                String text = part.getString("text");
                                if (callback != null) {
                                    callback.onTextResponse(text);
                                }
                            }
                            
                            // Check for function call
                            if (part.has("functionCall")) {
                                JSONObject fn = part.getJSONObject("functionCall");
                                String name = fn.getString("name");
                                JSONObject args = fn.optJSONObject("args");
                                if (args == null) {
                                    args = new JSONObject();
                                }
                                // #region agent log
                                try {
                                    java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                                    fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " GeminiLiveClient.handleServerMessage:FUNCTION_CALL_SERVERCONTENT " + 
                                        "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"H1\",\"location\":\"GeminiLiveClient.java:handleServerMessage\",\"message\":\"Function call found in serverContent\",\"data\":{\"functionName\":\"" + 
                                        name + "\",\"args\":\"" + args.toString().replace("\"", "\\\"") + "\",\"callbackNull\":" + (callback == null) + "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                                    fw.close();
                                } catch (Exception ex) {}
                                // #endregion
                                if (callback != null) {
                                    // #region agent log
                                    try {
                                        java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                                        fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " GeminiLiveClient.handleServerMessage:CALLBACK_INVOKE " + 
                                            "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"H5\",\"location\":\"GeminiLiveClient.java:handleServerMessage\",\"message\":\"Invoking onFunctionCall callback\",\"data\":{\"functionName\":\"" + 
                                            name + "\"},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                                        fw.close();
                                    } catch (Exception ex) {}
                                    // #endregion
                                    try {
                                        String callId = fn.optString("id", null);
                                        if (callId == null || callId.isEmpty()) {
                                            callId = fn.optString("callId", null);
                                        }
                                        callback.onFunctionCall(name, args, callId);
                                    } catch (Throwable t) {
                                        Log.e(TAG, "Error invoking onFunctionCall callback", t);
                                        // #region agent log
                                        try {
                                            java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                                            fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " GeminiLiveClient.handleServerMessage:CALLBACK_ERROR " + 
                                                "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"H5\",\"location\":\"GeminiLiveClient.java:handleServerMessage\",\"message\":\"Error in callback\",\"data\":{\"functionName\":\"" + 
                                                name + "\",\"error\":\"" + t.getMessage() + "\"},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                                            fw.close();
                                        } catch (Exception ex) {}
                                        // #endregion
                                    }
                                }
                            } else {
                                // #region agent log
                                try {
                                    java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                                    java.util.Iterator<String> partKeys = part.keys();
                                    java.util.ArrayList<String> partKeyList = new java.util.ArrayList<>();
                                    while (partKeys.hasNext()) {
                                        partKeyList.add(partKeys.next());
                                    }
                                    fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " GeminiLiveClient.handleServerMessage:PART_NO_FUNCTIONCALL " + 
                                        "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"H1\",\"location\":\"GeminiLiveClient.java:handleServerMessage\",\"message\":\"Part has no functionCall\",\"data\":{\"partKeys\":\"" + 
                                        String.join(",", partKeyList) + "\",\"hasText\":" + part.has("text") + "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                                    fw.close();
                                } catch (Exception ex) {}
                                // #endregion
                            }
                        }
                    }
                }
            }
            
            // Handle different message types from Gemini Live
            // Check both model_turn (snake_case) and modelTurn (camelCase)
            JSONObject modelTurn = null;
            if (message.has("model_turn")) {
                modelTurn = message.getJSONObject("model_turn");
                Log.d(TAG, "Found model_turn (snake_case)");
            } else if (message.has("modelTurn")) {
                modelTurn = message.getJSONObject("modelTurn");
                Log.d(TAG, "Found modelTurn (camelCase)");
            }
            
            if (modelTurn != null) {
                Log.d(TAG, "Processing modelTurn, keys: " + modelTurn.keys());
                // #region agent log
                try {
                    java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                    fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " GeminiLiveClient.handleServerMessage:MODEL_TURN " + 
                        "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"H3\",\"location\":\"GeminiLiveClient.java:handleServerMessage\",\"message\":\"Found model_turn/modelTurn\",\"data\":{\"hasParts\":" + 
                        modelTurn.has("parts") + ",\"hasFunctionCalls\":" + modelTurn.has("functionCalls") + "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                    fw.close();
                } catch (Exception ex) {}
                // #endregion
                
                // Check for functionCalls at modelTurn level
                if (modelTurn.has("functionCalls")) {
                    Log.d(TAG, "Found functionCalls array in modelTurn");
                    JSONArray functionCalls = modelTurn.getJSONArray("functionCalls");
                    for (int j = 0; j < functionCalls.length(); j++) {
                        JSONObject fnCall = functionCalls.getJSONObject(j);
                        String name = fnCall.getString("name");
                        JSONObject args = fnCall.optJSONObject("args");
                        if (args == null) {
                            args = new JSONObject();
                        }
                        String callId = fnCall.optString("id", null);
                        if (callId == null || callId.isEmpty()) {
                            callId = fnCall.optString("callId", null);
                        }
                        Log.d(TAG, "Processing function call from modelTurn.functionCalls: " + name + " with args: " + args + " id: " + callId);
                        if (callback != null) {
                            try {
                                callback.onFunctionCall(name, args, callId);
                            } catch (Throwable t) {
                                Log.e(TAG, "Error invoking onFunctionCall from modelTurn.functionCalls", t);
                            }
                        }
                    }
                }
                
                if (modelTurn.has("parts")) {
                    JSONArray parts = modelTurn.getJSONArray("parts");
                    // #region agent log
                    try {
                        java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                        fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " GeminiLiveClient.handleServerMessage:PARTS_FOUND " + 
                            "{\"sessionId\":\"debug-session\",\"runId\":\"run8\",\"hypothesisId\":\"H3\",\"location\":\"GeminiLiveClient.java:handleServerMessage\",\"message\":\"Processing parts\",\"data\":{\"partsCount\":" + 
                            parts.length() + "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                        fw.close();
                    } catch (Exception ex) {}
                    // #endregion
                    for (int i = 0; i < parts.length(); i++) {
                        JSONObject part = parts.getJSONObject(i);
                        
                        // Log part structure
                        Log.d(TAG, "Processing part " + i + ", keys: " + part.keys());
                        
                        // Check for functionCalls array in part
                        if (part.has("functionCalls")) {
                            Log.d(TAG, "Found functionCalls array in part");
                            JSONArray functionCalls = part.getJSONArray("functionCalls");
                            for (int j = 0; j < functionCalls.length(); j++) {
                                JSONObject fnCall = functionCalls.getJSONObject(j);
                                String name = fnCall.getString("name");
                                JSONObject args = fnCall.optJSONObject("args");
                                if (args == null) {
                                    args = new JSONObject();
                                }
                                String callId = fnCall.optString("id", null);
                                if (callId == null || callId.isEmpty()) {
                                    callId = fnCall.optString("callId", null);
                                }
                                Log.d(TAG, "Processing function call from array: " + name + " with args: " + args + " id: " + callId);
                                if (callback != null) {
                                    try {
                                        callback.onFunctionCall(name, args, callId);
                                    } catch (Throwable t) {
                                        Log.e(TAG, "Error invoking onFunctionCall from functionCalls array in part", t);
                                    }
                                }
                            }
                        }
                        
                        // Check for function call (singular)
                        if (part.has("functionCall")) {
                            Log.d(TAG, "Found functionCall (singular) in part");
                            JSONObject fn = part.getJSONObject("functionCall");
                            String name = fn.getString("name");
                            JSONObject args = fn.optJSONObject("args");
                            if (args == null) {
                                args = new JSONObject();
                            }
                            String callId = fn.optString("id", null);
                            if (callId == null || callId.isEmpty()) {
                                callId = fn.optString("callId", null);
                            }
                            Log.d(TAG, "Function call in modelTurn part: " + name + " id: " + callId);
                            // #region agent log
                            try {
                                android.util.Log.d("GeminiLiveClient", "FUNCTION_CALL_MODELTURN: " + name + " id:" + callId + " args:" + args.toString());
                            } catch (Exception ex) {}
                            // #endregion
                            if (callback != null) {
                                // #region agent log
                                try {
                                    android.util.Log.d("GeminiLiveClient", "CALLBACK_INVOKE_MODELTURN: " + name);
                                } catch (Exception ex) {}
                                // #endregion
                                try {
                                    callback.onFunctionCall(name, args, callId);
                                } catch (Throwable t) {
                                    Log.e(TAG, "Error invoking onFunctionCall callback from model_turn", t);
                                    // #region agent log
                                    try {
                                        android.util.Log.e("GeminiLiveClient", "CALLBACK_ERROR_MODELTURN: " + name + " error: " + t.getMessage());
                                    } catch (Exception ex) {}
                                    // #endregion
                                }
                            }
                        } else {
                            // #region agent log
                            try {
                                java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                                java.util.Iterator<String> partKeys = part.keys();
                                java.util.ArrayList<String> partKeyList = new java.util.ArrayList<>();
                                while (partKeys.hasNext()) {
                                    partKeyList.add(partKeys.next());
                                }
                                fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " GeminiLiveClient.handleServerMessage:PART_NO_FUNCTIONCALL_MODELTURN " + 
                                    "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"H2\",\"location\":\"GeminiLiveClient.java:handleServerMessage\",\"message\":\"Part in model_turn has no functionCall\",\"data\":{\"partKeys\":\"" + 
                                    String.join(",", partKeyList) + "\",\"hasText\":" + part.has("text") + "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                                fw.close();
                            } catch (Exception ex) {}
                            // #endregion
                        }
                        
                        // Check for text response
                        if (part.has("text")) {
                            String text = part.getString("text");
                            // #region agent log
                            try {
                                java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                                fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " GeminiLiveClient.handleServerMessage:TEXT_RESPONSE " + 
                                    "{\"sessionId\":\"debug-session\",\"runId\":\"run8\",\"hypothesisId\":\"H3\",\"location\":\"GeminiLiveClient.java:handleServerMessage\",\"message\":\"Text response found\",\"data\":{\"textLength\":" + 
                                    text.length() + ",\"textPreview\":\"" + text.substring(0, Math.min(50, text.length())).replace("\"", "\\\"") + "...\"},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                                fw.close();
                            } catch (Exception ex) {}
                            // #endregion
                            if (callback != null) {
                                callback.onTextResponse(text);
                            }
                        }
                    }
                }
            } else {
                // #region agent log
                try {
                    java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                    fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " GeminiLiveClient.handleServerMessage:NO_MODEL_TURN " + 
                        "{\"sessionId\":\"debug-session\",\"runId\":\"run8\",\"hypothesisId\":\"H3\",\"location\":\"GeminiLiveClient.java:handleServerMessage\",\"message\":\"Message has no model_turn\",\"data\":{},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                    fw.close();
                } catch (Exception ex) {}
                // #endregion
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling server message", e);
            // #region agent log
            try {
                java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " GeminiLiveClient.handleServerMessage:ERROR " + 
                    "{\"sessionId\":\"debug-session\",\"runId\":\"run8\",\"hypothesisId\":\"H3\",\"location\":\"GeminiLiveClient.java:handleServerMessage\",\"message\":\"Error handling message\",\"data\":{\"error\":\"" + 
                    e.getMessage() + "\"},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                fw.close();
            } catch (Exception ex) {}
            // #endregion
        }
    }
    
    public boolean isConnected() {
        return isConnected;
    }
}
