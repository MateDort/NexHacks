package com.tapmate.aiagent.core;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Response object returned by sub-agents after executing a function.
 * This is sent back to Gemini to continue the conversation.
 */
public class FunctionResponse {
    private final String functionName;
    private final JSONObject response;
    private final boolean success;
    private final String errorMessage;

    private FunctionResponse(String functionName, JSONObject response, boolean success, String errorMessage) {
        this.functionName = functionName;
        this.response = response;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    /**
     * Create a successful function response.
     *
     * @param functionName Name of the function that was called
     * @param response Response data as JSON
     * @return FunctionResponse
     */
    public static FunctionResponse success(String functionName, JSONObject response) {
        return new FunctionResponse(functionName, response, true, null);
    }

    /**
     * Create an error function response.
     *
     * @param functionName Name of the function that was called
     * @param errorMessage Error message
     * @return FunctionResponse
     */
    public static FunctionResponse error(String functionName, String errorMessage) {
        try {
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("error", errorMessage);
            errorResponse.put("success", false);
            return new FunctionResponse(functionName, errorResponse, false, errorMessage);
        } catch (JSONException e) {
            // Fallback if JSON creation fails
            return new FunctionResponse(functionName, new JSONObject(), false, errorMessage);
        }
    }

    /**
     * Get the function name.
     */
    public String getFunctionName() {
        return functionName;
    }

    /**
     * Get the response data.
     */
    public JSONObject getResponse() {
        return response;
    }

    /**
     * Check if the function executed successfully.
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Get error message if function failed.
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Convert to JSON format for sending to Gemini.
     */
    public JSONObject toJSON() {
        try {
            JSONObject json = new JSONObject();
            json.put("name", functionName);
            json.put("response", response);
            return json;
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    @Override
    public String toString() {
        return "FunctionResponse{" +
                "functionName='" + functionName + '\'' +
                ", success=" + success +
                ", response=" + response.toString() +
                (errorMessage != null ? ", error='" + errorMessage + '\'' : "") +
                '}';
    }
}
