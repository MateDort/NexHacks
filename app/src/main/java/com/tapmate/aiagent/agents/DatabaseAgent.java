package com.tapmate.aiagent.agents;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import com.tapmate.aiagent.core.FunctionResponse;
import com.tapmate.aiagent.core.SubAgent;
import com.tapmate.aiagent.database.ConfigDatabaseHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.CompletableFuture;

/**
 * DatabaseAgent - Handles memory storage and recall.
 * Stores interactions and allows context-aware responses.
 */
public class DatabaseAgent implements SubAgent {

    private static final String TAG = "DatabaseAgent";
    private static final String FUNCTION_NAME = "recall_memory";

    private final Context context;
    private final ConfigDatabaseHelper dbHelper;

    public DatabaseAgent(Context context) {
        this.context = context;
        this.dbHelper = new ConfigDatabaseHelper(context);
    }

    @Override
    public String getName() {
        return FUNCTION_NAME;
    }

    @Override
    public String getDescription() {
        return "Search and recall past interactions and context. " +
                "Use this to remember previous conversations, user preferences, " +
                "or information discussed earlier.";
    }

    @Override
    public JSONObject getFunctionDeclaration() {
        try {
            JSONObject declaration = new JSONObject();
            declaration.put("name", FUNCTION_NAME);
            declaration.put("description", getDescription());

            JSONObject parameters = new JSONObject();
            parameters.put("type", "OBJECT");

            JSONObject properties = new JSONObject();

            // Query parameter
            JSONObject queryParam = new JSONObject();
            queryParam.put("type", "STRING");
            queryParam.put("description", "What to search for in past interactions");
            properties.put("query", queryParam);

            // Limit parameter
            JSONObject limitParam = new JSONObject();
            limitParam.put("type", "INTEGER");
            limitParam.put("description", "Maximum number of results to return (default: 5)");
            properties.put("limit", limitParam);

            parameters.put("properties", properties);

            // Required
            JSONObject required = new JSONObject();
            required.put("0", "query");
            parameters.put("required", required);

            declaration.put("parameters", parameters);

            return declaration;

        } catch (JSONException e) {
            Log.e(TAG, "Error creating function declaration", e);
            return new JSONObject();
        }
    }

    @Override
    public CompletableFuture<FunctionResponse> execute(JSONObject args) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String query = args.getString("query");
                int limit = args.optInt("limit", 5);

                Log.d(TAG, "Searching interactions for: " + query + " (limit: " + limit + ")");

                // Search interactions
                Cursor cursor = dbHelper.searchInteractions(query, limit);
                JSONArray results = new JSONArray();

                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        JSONObject interaction = new JSONObject();
                        interaction.put("timestamp", cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")));
                        interaction.put("user_input", cursor.getString(cursor.getColumnIndexOrThrow("user_input")));
                        interaction.put("agent_response", cursor.getString(cursor.getColumnIndexOrThrow("agent_response")));
                        interaction.put("context", cursor.getString(cursor.getColumnIndexOrThrow("context")));
                        results.put(interaction);
                    }
                    cursor.close();
                }

                JSONObject response = new JSONObject();
                response.put("query", query);
                response.put("results_found", results.length());
                response.put("results", results);
                response.put("success", true);

                Log.i(TAG, "Found " + results.length() + " matching interactions");

                return FunctionResponse.success(FUNCTION_NAME, response);

            } catch (Exception e) {
                Log.e(TAG, "Error searching interactions", e);
                return FunctionResponse.error(FUNCTION_NAME, "Search error: " + e.getMessage());
            }
        });
    }

    /**
     * Store a new interaction in the database.
     * Called by the orchestrator after each conversation turn.
     */
    public void storeInteraction(String userInput, String agentResponse, String context) {
        try {
            boolean success = dbHelper.storeInteraction(userInput, agentResponse, context);
            if (success) {
                Log.d(TAG, "Interaction stored successfully");
            } else {
                Log.w(TAG, "Failed to store interaction");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error storing interaction", e);
        }
    }

    /**
     * Get recent interactions for context.
     */
    public JSONArray getRecentInteractions(int limit) {
        JSONArray results = new JSONArray();

        try {
            Cursor cursor = dbHelper.getRecentInteractions(limit);

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    JSONObject interaction = new JSONObject();
                    interaction.put("user_input", cursor.getString(cursor.getColumnIndexOrThrow("user_input")));
                    interaction.put("agent_response", cursor.getString(cursor.getColumnIndexOrThrow("agent_response")));
                    results.put(interaction);
                }
                cursor.close();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error getting recent interactions", e);
        }

        return results;
    }
}
