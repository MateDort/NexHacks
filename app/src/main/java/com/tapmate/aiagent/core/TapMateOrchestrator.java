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
 * Main orchestrator for the TapMate agentic system.
 * Manages Gemini Live session, function routing, and sub-agent coordination.
 */
public class TapMateOrchestrator {

    private static final String TAG = "TapMateOrchestrator";

    private static TapMateOrchestrator instance;
    private final Context context;
    private final ConfigManager configManager;
    private final Map<String, SubAgent> subAgents;
    private GeminiLiveClient geminiClient;
    private boolean sessionActive = false;

    private TapMateOrchestrator(Context context) {
        this.context = context.getApplicationContext();
        this.configManager = ConfigManager.getInstance(context);
        this.subAgents = new HashMap<>();
        Log.i(TAG, "TapMateOrchestrator initialized");
    }

    public static synchronized TapMateOrchestrator getInstance(Context context) {
        if (instance == null) {
            instance = new TapMateOrchestrator(context);
        }
        return instance;
    }

    /**
     * Initialize the orchestrator with all sub-agents.
     */
    public void initialize() {
        Log.i(TAG, "Initializing TapMate system...");

        // Initialize Gemini client
        String apiKey = getApiKey();
        geminiClient = new GeminiLiveClient(context, apiKey);

        // Sub-agents will be registered as they're created
        Log.i(TAG, "TapMate system initialized with " + subAgents.size() + " sub-agents");
    }

    /**
     * Register a sub-agent with the orchestrator.
     * The sub-agent's function will be made available to Gemini.
     */
    public void registerSubAgent(SubAgent agent) {
        String functionName = agent.getName();
        subAgents.put(functionName, agent);

        // Initialize the agent
        agent.initialize();

        // Register function with Gemini client
        geminiClient.registerFunction(agent.getFunctionDeclaration(),
            args -> handleFunctionCall(functionName, args));

        Log.i(TAG, "Registered sub-agent: " + functionName);
    }

    /**
     * Start a new TapMate session.
     * Loads current configuration and builds system instruction.
     */
    public void startSession() {
        if (sessionActive) {
            Log.w(TAG, "Session already active");
            return;
        }

        Log.i(TAG, "Starting TapMate session...");

        // Build system instruction with current config
        String systemInstruction = configManager.buildSystemInstruction();

        // Get all function declarations
        List<JSONObject> functions = new ArrayList<>();
        for (SubAgent agent : subAgents.values()) {
            functions.add(agent.getFunctionDeclaration());
        }

        // Initialize Gemini session
        geminiClient.startSession(systemInstruction, functions);

        sessionActive = true;
        Log.i(TAG, "Session started with " + functions.size() + " functions");
    }

    /**
     * Stop the current session.
     */
    public void stopSession() {
        if (!sessionActive) {
            Log.w(TAG, "No active session to stop");
            return;
        }

        Log.i(TAG, "Stopping session...");
        geminiClient.stopSession();
        sessionActive = false;
        Log.i(TAG, "Session stopped");
    }

    /**
     * Send user input to Gemini.
     */
    public CompletableFuture<String> sendUserInput(String input) {
        if (!sessionActive) {
            return CompletableFuture.completedFuture("Error: No active session");
        }

        return geminiClient.sendMessage(input);
    }

    /**
     * Handle a function call from Gemini.
     * Routes to the appropriate sub-agent and returns the result.
     */
    private CompletableFuture<FunctionResponse> handleFunctionCall(String functionName, JSONObject args) {
        Log.d(TAG, "Function called: " + functionName);
        Log.d(TAG, "Arguments: " + args.toString());

        SubAgent agent = subAgents.get(functionName);
        if (agent == null) {
            Log.e(TAG, "Unknown function: " + functionName);
            return CompletableFuture.completedFuture(
                FunctionResponse.error(functionName, "Unknown function: " + functionName)
            );
        }

        // Execute the sub-agent's function
        return agent.execute(args)
            .exceptionally(error -> {
                Log.e(TAG, "Error executing " + functionName + ": " + error.getMessage());
                return FunctionResponse.error(functionName, "Execution error: " + error.getMessage());
            });
    }

    /**
     * Update system instruction when configuration changes.
     */
    public void updateSystemInstruction() {
        if (!sessionActive) {
            Log.d(TAG, "No active session, system instruction will be updated on next start");
            return;
        }

        Log.i(TAG, "Updating system instruction...");
        String newInstruction = configManager.buildSystemInstruction();
        geminiClient.updateSystemInstruction(newInstruction);
        Log.i(TAG, "System instruction updated");
    }

    /**
     * Check if session is active.
     */
    public boolean isSessionActive() {
        return sessionActive;
    }

    /**
     * Get configuration manager.
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * Shutdown the orchestrator and cleanup resources.
     */
    public void shutdown() {
        Log.i(TAG, "Shutting down TapMate...");

        if (sessionActive) {
            stopSession();
        }

        // Shutdown all sub-agents
        for (SubAgent agent : subAgents.values()) {
            try {
                agent.shutdown();
            } catch (Exception e) {
                Log.e(TAG, "Error shutting down agent: " + e.getMessage());
            }
        }

        subAgents.clear();
        Log.i(TAG, "TapMate shutdown complete");
    }

    /**
     * Get Gemini API key from BuildConfig.
     */
    private String getApiKey() {
        try {
            // API key is loaded from .env via build.gradle
            return com.tapmate.aiagent.BuildConfig.GEMINI_API_KEY;
        } catch (Exception e) {
            Log.e(TAG, "Failed to load API key from BuildConfig");
            return "";
        }
    }
}
