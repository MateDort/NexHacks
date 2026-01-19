package com.tapmate.aiagent.core;

import android.util.Log;

import com.tapmate.aiagent.audio.AudioHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * AgentSession - Represents a single user's active session with Gemini Live
 *
 * Based on TARS agent_session.py pattern, adapted for Android single-user
 * model.
 * Each session maintains its own GeminiLiveClient, AudioHandler, and
 * conversation context.
 *
 * Session Lifecycle:
 * CREATED → ACTIVE → [RECONNECTING] → ACTIVE → TERMINATED
 * ↓
 * PAUSED (app backgrounded)
 */
public class AgentSession {

    private static final String TAG = "AgentSession";

    /**
     * Session status
     */
    public enum SessionStatus {
        CREATED, // Session created but not started
        ACTIVE, // Session active and streaming
        PAUSED, // Session paused (app backgrounded)
        RECONNECTING, // Attempting to reconnect
        TERMINATED // Session ended
    }

    /**
     * Permission levels (for future multi-user support)
     * Like TARS phone number → permissions, but for user profiles
     */
    public enum PermissionLevel {
        FULL, // All functions available (authenticated user)
        LIMITED, // Read-only functions (guest mode)
        NONE // No function access (error state)
    }

    // Session identity
    private final String sessionId;
    private final String userId;
    private SessionStatus status;
    private final long createdAt;
    private long lastActivityAt;

    // Clients
    private GeminiLiveClient geminiClient;
    private AudioHandler audioHandler;

    // State
    private PermissionLevel permissions;
    private final Map<String, Object> sessionContext; // Conversation context

    // Metrics
    private int functionCallCount;
    private int reconnectionAttempts;
    private long totalAudioBytesSent;
    private long totalAudioBytesReceived;

    /**
     * Create a new agent session
     *
     * @param userId      User identifier (for future multi-user support)
     * @param permissions Permission level for this session
     */
    public AgentSession(String userId, PermissionLevel permissions) {
        this.sessionId = UUID.randomUUID().toString();
        this.userId = userId;
        this.permissions = permissions;
        this.status = SessionStatus.CREATED;
        this.createdAt = System.currentTimeMillis();
        this.lastActivityAt = this.createdAt;
        this.sessionContext = new HashMap<>();

        // Initialize metrics
        this.functionCallCount = 0;
        this.reconnectionAttempts = 0;
        this.totalAudioBytesSent = 0;
        this.totalAudioBytesReceived = 0;

        // Initialize clients (AudioHandler first, then pass to GeminiLiveClient)
        this.audioHandler = new AudioHandler();
        this.geminiClient = new GeminiLiveClient(this.audioHandler);

        Log.i(TAG, String.format("[SESSION] Created: %s | User: %s | Permissions: %s",
                sessionId, userId, permissions));
    }

    // ========== Lifecycle Management ==========

    /**
     * Mark session as active (called after Gemini setup complete)
     */
    public void activate() {
        this.status = SessionStatus.ACTIVE;
        updateLastActivity();
        Log.i(TAG, "[SESSION] Activated: " + sessionId);
    }

    /**
     * Pause session (app backgrounded)
     */
    public void pause() {
        if (status == SessionStatus.ACTIVE) {
            this.status = SessionStatus.PAUSED;
            Log.i(TAG, "[SESSION] Paused: " + sessionId);
        }
    }

    /**
     * Resume session (app foregrounded)
     */
    public void resume() {
        if (status == SessionStatus.PAUSED) {
            this.status = SessionStatus.ACTIVE;
            updateLastActivity();
            Log.i(TAG, "[SESSION] Resumed: " + sessionId);
        }
    }

    /**
     * Mark session as reconnecting
     */
    public void markReconnecting() {
        this.status = SessionStatus.RECONNECTING;
        this.reconnectionAttempts++;
        Log.i(TAG, String.format("[SESSION] Reconnecting: %s | Attempt: %d",
                sessionId, reconnectionAttempts));
    }

    /**
     * Terminate session and cleanup resources
     */
    public void terminate() {
        this.status = SessionStatus.TERMINATED;

        // Stop Gemini client
        if (geminiClient != null) {
            geminiClient.stopSession();
        }

        // Stop audio handler
        if (audioHandler != null) {
            audioHandler.stopCapture();
            audioHandler.stopPlayback();
        }

        Log.i(TAG, String.format("[SESSION] Terminated: %s | Duration: %dms | Function Calls: %d",
                sessionId, getDurationMs(), functionCallCount));
    }

    /**
     * Update last activity timestamp
     */
    public void updateLastActivity() {
        this.lastActivityAt = System.currentTimeMillis();
    }

    // ========== Client Access ==========

    public GeminiLiveClient getGeminiClient() {
        return geminiClient;
    }

    public AudioHandler getAudioHandler() {
        return audioHandler;
    }

    // ========== Context Management ==========

    /**
     * Store context data (like TARS session context)
     * Example: "last_uber_car" → {"color": "red", "plate": "ABC-123"}
     */
    public void setContext(String key, Object value) {
        sessionContext.put(key, value);
        updateLastActivity();
        Log.d(TAG, String.format("[SESSION] Context set: %s = %s", key, value));
    }

    public Object getContext(String key) {
        return sessionContext.get(key);
    }

    public Object getContext(String key, Object defaultValue) {
        return sessionContext.getOrDefault(key, defaultValue);
    }

    public void clearContext() {
        sessionContext.clear();
        Log.d(TAG, "[SESSION] Context cleared: " + sessionId);
    }

    // ========== Metrics ==========

    public void incrementFunctionCallCount() {
        this.functionCallCount++;
        updateLastActivity();
    }

    public void recordAudioSent(long bytes) {
        this.totalAudioBytesSent += bytes;
    }

    public void recordAudioReceived(long bytes) {
        this.totalAudioBytesReceived += bytes;
    }

    public long getDurationMs() {
        return System.currentTimeMillis() - createdAt;
    }

    public long getIdleTimeMs() {
        return System.currentTimeMillis() - lastActivityAt;
    }

    // ========== Permission Checking ==========

    /**
     * Check if this session has permission for a function
     * Like TARS permission checking by phone number
     */
    public boolean hasPermission(String functionName) {
        switch (permissions) {
            case FULL:
                return true; // All functions allowed

            case LIMITED:
                // Read-only functions only
                return isReadOnlyFunction(functionName);

            case NONE:
            default:
                return false; // No functions allowed
        }
    }

    /**
     * Determine if function is read-only (safe for LIMITED permission)
     */
    private boolean isReadOnlyFunction(String functionName) {
        // Read-only functions that don't modify state
        return functionName.equals("read_screen")
                || functionName.equals("get_config")
                || functionName.equals("recall_memory")
                || functionName.equals("google_search");
    }

    public void setPermissions(PermissionLevel permissions) {
        this.permissions = permissions;
        Log.i(TAG, String.format("[SESSION] Permissions changed: %s → %s",
                sessionId, permissions));
    }

    // ========== Getters ==========

    public String getSessionId() {
        return sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public PermissionLevel getPermissions() {
        return permissions;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getLastActivityAt() {
        return lastActivityAt;
    }

    public int getFunctionCallCount() {
        return functionCallCount;
    }

    public int getReconnectionAttempts() {
        return reconnectionAttempts;
    }

    public long getTotalAudioBytesSent() {
        return totalAudioBytesSent;
    }

    public long getTotalAudioBytesReceived() {
        return totalAudioBytesReceived;
    }

    // ========== Debug Info ==========

    public String getDebugInfo() {
        return String.format(
                "Session[id=%s, user=%s, status=%s, duration=%dms, idle=%dms, calls=%d, reconnects=%d, audioSent=%d, audioReceived=%d]",
                sessionId, userId, status, getDurationMs(), getIdleTimeMs(),
                functionCallCount, reconnectionAttempts,
                totalAudioBytesSent, totalAudioBytesReceived);
    }

    @Override
    public String toString() {
        return String.format("AgentSession[%s, %s]", sessionId, status);
    }
}
