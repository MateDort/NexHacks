package com.tapmate.aiagent.core;

import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * SessionManager - Central coordinator for session lifecycle
 *
 * Based on TARS session_manager.py pattern, adapted for Android.
 * Manages session creation, termination, and resource cleanup.
 *
 * TARS Pattern:
 * - Multiple concurrent sessions (one per phone call)
 * - Phone number → permissions
 *
 * TapMate Pattern:
 * - Single active session (one user app)
 * - User ID → permissions
 * - Prepared for future multi-user support
 */
public class SessionManager {

    private static final String TAG = "SessionManager";

    // Session timeout configuration
    private static final long SESSION_TIMEOUT_MS = 30 * 60 * 1000;  // 30 minutes idle
    private static final long CLEANUP_INTERVAL_MS = 60 * 1000;      // Check every minute

    // Active sessions (TARS: multiple, TapMate: currently one, but prepared for multiple)
    private final Map<String, AgentSession> activeSessions = new HashMap<>();
    private AgentSession currentActiveSession = null;

    // Background cleanup
    private final ScheduledExecutorService cleanupExecutor;
    private final ExecutorService sessionExecutor;

    /**
     * Create session manager with background cleanup
     */
    public SessionManager() {
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
        this.sessionExecutor = Executors.newCachedThreadPool();

        // Start background cleanup task (like TARS session monitoring)
        startCleanupTask();

        Log.i(TAG, "SessionManager initialized");
    }

    /**
     * Create a new session (like TARS receiving phone call)
     *
     * @param userId User identifier
     * @param permissions Permission level for this session
     * @return Created session
     */
    public synchronized AgentSession createSession(
            String userId,
            AgentSession.PermissionLevel permissions
    ) {
        // TapMate: Single-user model - terminate existing session first
        if (currentActiveSession != null) {
            Log.w(TAG, "Terminating existing session before creating new one");
            terminateSession(currentActiveSession.getSessionId());
        }

        // Create new session
        AgentSession session = new AgentSession(userId, permissions);
        activeSessions.put(session.getSessionId(), session);
        currentActiveSession = session;

        Log.i(TAG, String.format(
                "[SESSION] Created session: %s | User: %s | Permissions: %s | Total sessions: %d",
                session.getSessionId(), userId, permissions, activeSessions.size()
        ));

        return session;
    }

    /**
     * Get active session (TapMate: single session, TARS: by sessionId)
     */
    public synchronized AgentSession getActiveSession() {
        return currentActiveSession;
    }

    /**
     * Get session by ID (for future multi-user support)
     */
    public synchronized AgentSession getSession(String sessionId) {
        return activeSessions.get(sessionId);
    }

    /**
     * Terminate a session (like TARS hanging up phone call)
     *
     * @param sessionId Session to terminate
     */
    public synchronized void terminateSession(String sessionId) {
        AgentSession session = activeSessions.get(sessionId);
        if (session == null) {
            Log.w(TAG, "[SESSION] Cannot terminate - session not found: " + sessionId);
            return;
        }

        Log.i(TAG, "[SESSION] Terminating session: " + sessionId);

        // Execute termination in background to avoid blocking
        sessionExecutor.execute(() -> {
            try {
                // Terminate session (stops Gemini client and audio)
                session.terminate();

                // Remove from active sessions
                synchronized (SessionManager.this) {
                    activeSessions.remove(sessionId);
                    if (session == currentActiveSession) {
                        currentActiveSession = null;
                    }
                }

                Log.i(TAG, String.format(
                        "[SESSION] Terminated: %s | Duration: %dms | Function calls: %d | Remaining sessions: %d",
                        sessionId, session.getDurationMs(), session.getFunctionCallCount(),
                        activeSessions.size()
                ));

            } catch (Exception e) {
                Log.e(TAG, "[SESSION] Error terminating session: " + sessionId, e);
            }
        });
    }

    /**
     * Check if session has permission for a function
     * Like TARS permission checking
     */
    public synchronized boolean hasPermission(String sessionId, String functionName) {
        AgentSession session = activeSessions.get(sessionId);
        if (session == null) {
            Log.w(TAG, "[PERMISSION] Session not found: " + sessionId);
            return false;
        }

        boolean hasPermission = session.hasPermission(functionName);

        if (!hasPermission) {
            Log.w(TAG, String.format(
                    "[PERMISSION] Denied: %s does not have permission for %s (level: %s)",
                    sessionId, functionName, session.getPermissions()
            ));
        }

        return hasPermission;
    }

    /**
     * Update session permissions (for future dynamic permission changes)
     */
    public synchronized void updatePermissions(
            String sessionId,
            AgentSession.PermissionLevel newPermissions
    ) {
        AgentSession session = activeSessions.get(sessionId);
        if (session != null) {
            session.setPermissions(newPermissions);
            Log.i(TAG, String.format(
                    "[PERMISSION] Updated: %s → %s", sessionId, newPermissions
            ));
        }
    }

    /**
     * Cleanup inactive sessions (like TARS timeout handling)
     * Called periodically by background task
     */
    public synchronized void cleanupInactiveSessions() {
        long now = System.currentTimeMillis();
        int cleanedCount = 0;

        // Find inactive sessions
        for (AgentSession session : activeSessions.values()) {
            long idleTime = now - session.getLastActivityAt();

            if (idleTime > SESSION_TIMEOUT_MS) {
                Log.i(TAG, String.format(
                        "[CLEANUP] Session idle for %dms (timeout: %dms): %s",
                        idleTime, SESSION_TIMEOUT_MS, session.getSessionId()
                ));

                terminateSession(session.getSessionId());
                cleanedCount++;
            }
        }

        if (cleanedCount > 0) {
            Log.i(TAG, "[CLEANUP] Cleaned up " + cleanedCount + " inactive sessions");
        }
    }

    /**
     * Start background cleanup task
     */
    private void startCleanupTask() {
        cleanupExecutor.scheduleAtFixedRate(
                () -> {
                    try {
                        cleanupInactiveSessions();
                    } catch (Exception e) {
                        Log.e(TAG, "[CLEANUP] Error in cleanup task", e);
                    }
                },
                CLEANUP_INTERVAL_MS,  // Initial delay
                CLEANUP_INTERVAL_MS,  // Period
                TimeUnit.MILLISECONDS
        );

        Log.i(TAG, "[CLEANUP] Background cleanup task started (interval: " + CLEANUP_INTERVAL_MS + "ms)");
    }

    /**
     * Pause all sessions (app backgrounded)
     */
    public synchronized void pauseAllSessions() {
        Log.i(TAG, "[LIFECYCLE] Pausing all sessions (app backgrounded)");
        for (AgentSession session : activeSessions.values()) {
            session.pause();
        }
    }

    /**
     * Resume all sessions (app foregrounded)
     */
    public synchronized void resumeAllSessions() {
        Log.i(TAG, "[LIFECYCLE] Resuming all sessions (app foregrounded)");
        for (AgentSession session : activeSessions.values()) {
            session.resume();
        }
    }

    /**
     * Get session metrics for debugging/monitoring
     */
    public synchronized String getSessionMetrics() {
        StringBuilder metrics = new StringBuilder();
        metrics.append("=== Session Metrics ===\n");
        metrics.append("Active sessions: ").append(activeSessions.size()).append("\n");

        for (AgentSession session : activeSessions.values()) {
            metrics.append(session.getDebugInfo()).append("\n");
        }

        return metrics.toString();
    }

    /**
     * Shutdown session manager and all sessions
     */
    public synchronized void shutdown() {
        Log.i(TAG, "[SHUTDOWN] Shutting down SessionManager");

        // Terminate all active sessions
        for (String sessionId : activeSessions.keySet()) {
            terminateSession(sessionId);
        }
        activeSessions.clear();
        currentActiveSession = null;

        // Shutdown executors
        cleanupExecutor.shutdown();
        sessionExecutor.shutdown();

        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
            if (!sessionExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                sessionExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            sessionExecutor.shutdownNow();
        }

        Log.i(TAG, "[SHUTDOWN] SessionManager shutdown complete");
    }

    /**
     * Get total active session count
     */
    public synchronized int getActiveSessionCount() {
        return activeSessions.size();
    }

    /**
     * Check if there's an active session
     */
    public synchronized boolean hasActiveSession() {
        return currentActiveSession != null
                && currentActiveSession.getStatus() == AgentSession.SessionStatus.ACTIVE;
    }
}
