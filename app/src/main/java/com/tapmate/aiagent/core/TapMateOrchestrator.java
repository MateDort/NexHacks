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
 * TapMateOrchestrator - Main agent coordinator (TARS-style)
 * Manages sessions via SessionManager, sub-agents, and function routing
 */
public class TapMateOrchestrator {

    private static final String TAG = "TapMateOrchestrator";
    private static TapMateOrchestrator instance;

    private final Context context;
    private final SessionManager sessionManager;
    private final ConfigManager configManager;
    private final Map<String, SubAgent> subAgents = new HashMap<>();

    private TapMateOrchestrator(Context context) {
        this.context = context.getApplicationContext();
        this.sessionManager = new SessionManager();
        this.configManager = ConfigManager.getInstance(context);
    }

    public static synchronized TapMateOrchestrator getInstance(Context context) {
        if (instance == null) {
            instance = new TapMateOrchestrator(context);
        }
        return instance;
    }

    public void initialize() {
        Log.i(TAG, "Initializing TapMate Orchestrator with SessionManager");
    }

    public void registerSubAgent(SubAgent agent) {
        subAgents.put(agent.getName(), agent);
        agent.initialize();

        Log.i(TAG, "Registered sub-agent: " + agent.getName());
    }

    public void startSession() {
        if (sessionManager.hasActiveSession()) {
            Log.w(TAG, "Session already active");
            return;
        }

        // Create session with FULL permissions (single-user app)
        AgentSession session = sessionManager.createSession(
                "default_user",
                AgentSession.PermissionLevel.FULL);

        // Build system instruction with current config
        String systemInstruction = configManager.buildSystemInstruction();

        // Collect all function declarations
        List<JSONObject> functionDeclarations = new ArrayList<>();
        for (SubAgent agent : subAgents.values()) {
            functionDeclarations.add(agent.getFunctionDeclaration());
        }

        // Register function handlers with the session's Gemini client
        GeminiLiveClient geminiClient = session.getGeminiClient();
        for (SubAgent agent : subAgents.values()) {
            geminiClient.registerFunction(
                    agent.getFunctionDeclaration(),
                    args -> {
                        session.incrementFunctionCallCount();
                        return agent.execute(args).thenApply(FunctionResponse::toJSON);
                    });
        }

        // Start Gemini session
        geminiClient.startSession(systemInstruction, functionDeclarations);
        session.activate();

        Log.i(TAG,
                "[SESSION] Started with " + subAgents.size() + " sub-agents | Session ID: " + session.getSessionId());
    }

    public void stopSession() {
        AgentSession session = sessionManager.getActiveSession();
        if (session == null) {
            Log.w(TAG, "No active session");
            return;
        }

        sessionManager.terminateSession(session.getSessionId());
        Log.i(TAG, "[SESSION] Stopped");
    }

    public boolean isSessionActive() {
        return sessionManager.hasActiveSession();
    }

    /**
     * Get GeminiLiveClient from active session
     */
    public GeminiLiveClient getGeminiClient() {
        AgentSession session = sessionManager.getActiveSession();
        if (session == null) {
            Log.w(TAG, "No active session - cannot get Gemini client");
            return null;
        }
        return session.getGeminiClient();
    }

    /**
     * Get active session (for advanced access)
     */
    public AgentSession getSession() {
        return sessionManager.getActiveSession();
    }

    /**
     * Get session manager (for lifecycle management)
     */
    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public void updateSystemInstruction() {
        if (isSessionActive()) {
            Log.i(TAG, "Config changed - restart session for changes to take effect");
        }
    }

    /**
     * Pause session (app backgrounded)
     */
    public void pauseSession() {
        sessionManager.pauseAllSessions();
    }

    /**
     * Resume session (app foregrounded)
     */
    public void resumeSession() {
        sessionManager.resumeAllSessions();
    }

    public void shutdown() {
        stopSession();
        sessionManager.shutdown();
        for (SubAgent agent : subAgents.values()) {
            agent.shutdown();
        }
        subAgents.clear();
        Log.i(TAG, "TapMate Orchestrator shutdown");
    }
}
