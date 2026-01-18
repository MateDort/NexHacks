package com.tapmate.aiagent.core;

import org.json.JSONObject;
import java.util.concurrent.CompletableFuture;

/**
 * Base interface for all sub-agents in the TapMate system.
 * Each sub-agent represents a specialized capability (GUI control, vision, navigation, etc.)
 * and is exposed as a function that Gemini can call.
 */
public interface SubAgent {

    /**
     * Get the unique name of this sub-agent's function.
     * This name is used by Gemini to call the function.
     *
     * @return Function name (e.g., "control_gui", "detect_objects")
     */
    String getName();

    /**
     * Get a human-readable description of what this sub-agent does.
     * Used by Gemini to understand when to call this function.
     *
     * @return Function description
     */
    String getDescription();

    /**
     * Get the function declaration in Gemini API format.
     * Includes function name, description, and parameter schema.
     *
     * @return Function declaration as JSONObject
     */
    JSONObject getFunctionDeclaration();

    /**
     * Execute this sub-agent's function with the provided arguments.
     * This is called when Gemini invokes the function during a conversation.
     *
     * @param args Function arguments as JSON object
     * @return CompletableFuture with function response
     */
    CompletableFuture<FunctionResponse> execute(JSONObject args);

    /**
     * Optional lifecycle method called when the agent is initialized.
     * Override this to perform any setup needed.
     */
    default void initialize() {
        // Default: no initialization needed
    }

    /**
     * Optional lifecycle method called when the agent is being shut down.
     * Override this to clean up resources.
     */
    default void shutdown() {
        // Default: no cleanup needed
    }
}
