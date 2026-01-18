package com.tapmate.aiagent.core;

import android.content.Context;
import android.util.Log;

import com.tapmate.aiagent.config.ConfigManager;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * TapMateOrchestrator - Main agent coordinator
 * Manages Gemini session, sub-agents, and function routing
 */
public class TapMateOrchestrator {

    private static final String TAG = "TapMateOrchestrator";
    private static TapMateOrchestrator instance;

    private final Context context;
    private final GeminiLiveClient geminiClient;
    private final ConfigManager configManager;
    private final Map<String, SubAgent> subAgents = new HashMap<>();

    private boolean isSessionActive = false;

    private TapMateOrchestrator(Context context) {
        this.context = context.getApplicationContext();
        this.geminiClient = new GeminiLiveClient();
        this.configManager = ConfigManager.getInstance(context);
    }

    public static synchronized TapMateOrchestrator getInstance(Context context) {
        if (instance == null) {
            instance = new TapMateOrchestrator(context);
        }
        return instance;
    }

    public void initialize() {
        Log.i(TAG, "Initializing TapMate Orchestrator");
    }

    public void registerSubAgent(SubAgent agent) {
        subAgents.put(agent.getName(), agent);
        agent.initialize();
        
        // Register function with Gemini client
        geminiClient.registerFunction(
            agent.getFunctionDeclaration(),
            args -> agent.execute(args).thenApply(FunctionResponse::toJSON)
        );
        
        Log.i(TAG, "Registered sub-agent: " + agent.getName());
    }

    public void startSession() {
        if (isSessionActive) {
            Log.w(TAG, "Session already active");
            return;
        }

        // Build system instruction with current config
        String systemInstruction = configManager.buildSystemInstruction();

        // Collect all function declarations
        List<JSONObject> functionDeclarations = new ArrayList<>();
        for (SubAgent agent : subAgents.values()) {
            functionDeclarations.add(agent.getFunctionDeclaration());
        }

        // Start Gemini session
        geminiClient.startSession(systemInstruction, functionDeclarations);
        
        isSessionActive = true;
        Log.i(TAG, "Session started with " + subAgents.size() + " sub-agents");
    }

    public void stopSession() {
        if (!isSessionActive) {
            Log.w(TAG, "No active session");
            return;
        }

        geminiClient.stopSession();
        isSessionActive = false;
        Log.i(TAG, "Session stopped");
    }

    public boolean isSessionActive() {
        return isSessionActive;
    }

    public CompletableFuture<String> sendMessage(String message) {
        if (!isSessionActive) {
            return CompletableFuture.completedFuture("No active session");
        }
        return geminiClient.sendMessage(message);
    }

    private CompletableFuture<FunctionResponse> handleFunctionCall(String functionName, JSONObject args) {
        Log.i(TAG, "Handling function call: " + functionName);
        
        SubAgent agent = subAgents.get(functionName);
        if (agent == null) {
            Log.e(TAG, "Unknown function: " + functionName);
            return CompletableFuture.completedFuture(
                FunctionResponse.error(functionName, "Unknown function")
            );
        }

        return agent.execute(args);
    }

    public void updateSystemInstruction() {
        if (isSessionActive) {
            Log.i(TAG, "Config changed - restart session for changes to take effect");
        }
    }

    public void shutdown() {
        stopSession();
        for (SubAgent agent : subAgents.values()) {
            agent.shutdown();
        }
        subAgents.clear();
        Log.i(TAG, "TapMate Orchestrator shutdown");
    }
}
