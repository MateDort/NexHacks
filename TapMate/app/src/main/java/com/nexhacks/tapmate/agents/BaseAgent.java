package com.nexhacks.tapmate.agents;

import org.json.JSONArray;
import org.json.JSONObject;
import android.os.Handler;

public abstract class BaseAgent {
    protected Handler mainHandler;
    protected AgentCallback callback;
    
    public interface AgentCallback {
        void onResult(String functionName, String result, String callId);
        void onError(String functionName, String error, String callId);
    }
    
    public BaseAgent(Handler mainHandler, AgentCallback callback) {
        this.mainHandler = mainHandler;
        this.callback = callback;
    }
    
    // Each agent declares its functions
    public abstract JSONArray getFunctionDeclarations();
    
    // Each agent handles its own functions
    // Returns true if handled, false if not handled by this agent
    public abstract boolean handleFunction(String functionName, JSONObject args, String callId);
    
    // Get list of function names this agent handles
    public abstract String[] getHandledFunctions();
    
    // Helper method to create function declarations
    protected JSONObject createFunctionDeclaration(String name, String description,
                                                  String[] paramNames, String[] required) {
        try {
            JSONObject func = new JSONObject();
            func.put("name", name);
            func.put("description", description);
            
            JSONObject params = new JSONObject();
            params.put("type", "OBJECT");
            JSONObject properties = new JSONObject();
            
            for (String param : paramNames) {
                JSONObject paramDef = new JSONObject();
                paramDef.put("type", "STRING");
                properties.put(param, paramDef);
            }
            
            params.put("properties", properties);
            JSONArray requiredArray = new JSONArray();
            for (String req : required) {
                requiredArray.put(req);
            }
            params.put("required", requiredArray);
            func.put("parameters", params);
            
            return func;
        } catch (Exception e) {
            android.util.Log.e("BaseAgent", "Error creating function declaration for " + name, e);
            return new JSONObject();
        }
    }
    
    // Helper method to create function declarations with custom parameter types
    protected JSONObject createFunctionDeclarationWithTypes(String name, String description,
                                                           JSONObject paramDefinitions, String[] required) {
        try {
            JSONObject func = new JSONObject();
            func.put("name", name);
            func.put("description", description);
            
            JSONObject params = new JSONObject();
            params.put("type", "OBJECT");
            params.put("properties", paramDefinitions);
            
            JSONArray requiredArray = new JSONArray();
            for (String req : required) {
                requiredArray.put(req);
            }
            params.put("required", requiredArray);
            func.put("parameters", params);
            
            return func;
        } catch (Exception e) {
            android.util.Log.e("BaseAgent", "Error creating function declaration for " + name, e);
            return new JSONObject();
        }
    }
}
