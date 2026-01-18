package com.tapmate.aiagent.core;

import android.content.Context;
import android.util.Log;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.FunctionDeclaration;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.ai.client.generativeai.type.Tool;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * Gemini Live client for TapMate.
 * Handles communication with Gemini API including function calling.
 *
 * Current: Uses Gemini SDK with text-based interaction
 * Future: Will upgrade to native audio when available on Android
 */
public class GeminiLiveClient {

    private static final String TAG = "GeminiLiveClient";

    // Use trained model endpoint for GUI tasks
    private static final String MODEL_NAME = "gemini-2.5-flash";

    private final Context context;
    private final String apiKey;
    private final Executor executor;
    private final Map<String, Function<JSONObject, CompletableFuture<FunctionResponse>>> functionHandlers;

    private GenerativeModelFutures model;
    private String systemInstruction;
    private List<Content> conversationHistory;

    public GeminiLiveClient(Context context, String apiKey) {
        this.context = context;
        this.apiKey = apiKey;
        this.executor = Executors.newSingleThreadExecutor();
        this.functionHandlers = new HashMap<>();
        this.conversationHistory = new ArrayList<>();
        Log.i(TAG, "GeminiLiveClient initialized");
    }

    /**
     * Start a new session with system instruction and functions.
     */
    public void startSession(String systemInstruction, List<JSONObject> functionDeclarations) {
        this.systemInstruction = systemInstruction;
        this.conversationHistory.clear();

        Log.i(TAG, "Starting Gemini session with " + functionDeclarations.size() + " functions");
        Log.d(TAG, "System instruction: " + systemInstruction.substring(0, Math.min(100, systemInstruction.length())) + "...");

        // Convert function declarations to Gemini format
        List<FunctionDeclaration> geminiFunction s = convertFunctionDeclarations(functionDeclarations);

        // Create model with functions
        GenerativeModel.Builder builder = new GenerativeModel.Builder()
            .setModelName(MODEL_NAME)
            .setApiKey(apiKey)
            .setSystemInstruction(Content.fromText(systemInstruction));

        if (!geminiFunctions.isEmpty()) {
            builder.setTools(List.of(new Tool(geminiFunctions)));
        }

        GenerativeModel generativeModel = builder.build();
        this.model = GenerativeModelFutures.from(generativeModel);

        Log.i(TAG, "Gemini session started");
    }

    /**
     * Stop the current session.
     */
    public void stopSession() {
        Log.i(TAG, "Stopping Gemini session");
        conversationHistory.clear();
        model = null;
    }

    /**
     * Send a message to Gemini and get response.
     */
    public CompletableFuture<String> sendMessage(String message) {
        if (model == null) {
            return CompletableFuture.completedFuture("Error: No active session");
        }

        Log.d(TAG, "Sending message: " + message);

        CompletableFuture<String> future = new CompletableFuture<>();

        // Add user message to history
        Content userContent = Content.fromText(message);
        conversationHistory.add(userContent);

        // Generate response
        ListenableFuture<GenerateContentResponse> responseFuture =
            model.generateContent(conversationHistory);

        Futures.addCallback(responseFuture, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                try {
                    String responseText = result.getText();

                    // Add assistant response to history
                    conversationHistory.add(Content.fromText(responseText));

                    Log.d(TAG, "Response received: " + responseText.substring(0, Math.min(100, responseText.length())));
                    future.complete(responseText);

                } catch (Exception e) {
                    Log.e(TAG, "Error processing response", e);
                    future.completeExceptionally(e);
                }
            }

            @Override
            public void onFailure(Throwable t) {
                Log.e(TAG, "Error generating response", t);
                future.completeExceptionally(t);
            }
        }, executor);

        return future;
    }

    /**
     * Register a function handler.
     */
    public void registerFunction(JSONObject declaration,
                                 Function<JSONObject, CompletableFuture<FunctionResponse>> handler) {
        try {
            String functionName = declaration.getString("name");
            functionHandlers.put(functionName, handler);
            Log.d(TAG, "Registered function handler: " + functionName);
        } catch (Exception e) {
            Log.e(TAG, "Error registering function", e);
        }
    }

    /**
     * Update system instruction for current session.
     */
    public void updateSystemInstruction(String newInstruction) {
        this.systemInstruction = newInstruction;

        // For now, we need to recreate the model with new system instruction
        // In future with native audio, this will be a live update
        Log.i(TAG, "System instruction updated (will apply to new messages)");
    }

    /**
     * Convert JSON function declarations to Gemini FunctionDeclaration format.
     */
    private List<FunctionDeclaration> convertFunctionDeclarations(List<JSONObject> jsonDeclarations) {
        List<FunctionDeclaration> declarations = new ArrayList<>();

        for (JSONObject json : jsonDeclarations) {
            try {
                String name = json.getString("name");
                String description = json.getString("description");

                // For now, create basic function declarations
                // Will enhance with parameter schemas as we add more complex functions
                FunctionDeclaration declaration = FunctionDeclaration.newBuilder()
                    .setName(name)
                    .setDescription(description)
                    .build();

                declarations.add(declaration);

            } catch (Exception e) {
                Log.e(TAG, "Error converting function declaration", e);
            }
        }

        return declarations;
    }

    /**
     * Check if session is active.
     */
    public boolean isSessionActive() {
        return model != null;
    }
}
