package com.nexhacks.tapmate.agents;

import org.json.JSONArray;
import org.json.JSONObject;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;

public class AgentRegistry {
    private static final String TAG = "AgentRegistry";
    private List<BaseAgent> agents = new ArrayList<>();
    
    public void registerAgent(BaseAgent agent) {
        agents.add(agent);
        Log.d(TAG, "Registered agent handling: " + java.util.Arrays.toString(agent.getHandledFunctions()));
    }
    
    // Collect all function declarations from all agents
    public JSONArray getAllFunctionDeclarations() {
        JSONArray allFuncs = new JSONArray();
        for (BaseAgent agent : agents) {
            try {
                JSONArray agentFuncs = agent.getFunctionDeclarations();
                for (int i = 0; i < agentFuncs.length(); i++) {
                    allFuncs.put(agentFuncs.getJSONObject(i));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting function declarations from agent", e);
            }
        }
        Log.d(TAG, "Total functions registered: " + allFuncs.length());
        return allFuncs;
    }
    
    // Route function call to appropriate agent
    public boolean handleFunctionCall(String functionName, JSONObject args, String callId) {
        // #region agent log
        try {
            java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
            fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " AgentRegistry.handleFunctionCall:ENTRY " + 
                "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"H4\",\"location\":\"AgentRegistry.java:handleFunctionCall\",\"message\":\"Routing function call\",\"data\":{\"functionName\":\"" + 
                functionName + "\",\"callId\":\"" + (callId != null ? callId : "null") + "\",\"agentCount\":" + agents.size() + "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
            fw.close();
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error logging", e);
        }
        // #endregion
        Log.d(TAG, "Routing function call: " + functionName);
        for (BaseAgent agent : agents) {
            try {
                String[] handledFuncs = agent.getHandledFunctions();
                // #region agent log
                try {
                    java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                    fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " AgentRegistry.handleFunctionCall:CHECKING_AGENT " + 
                        "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"H4\",\"location\":\"AgentRegistry.java:handleFunctionCall\",\"message\":\"Checking agent\",\"data\":{\"functionName\":\"" + 
                        functionName + "\",\"agentHandles\":\"" + java.util.Arrays.toString(handledFuncs) + "\"},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                    fw.close();
                } catch (Exception e) {}
                // #endregion
                if (agent.handleFunction(functionName, args, callId)) {
                    // #region agent log
                    try {
                        java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                        fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " AgentRegistry.handleFunctionCall:HANDLED " + 
                            "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"H4\",\"location\":\"AgentRegistry.java:handleFunctionCall\",\"message\":\"Function handled by agent\",\"data\":{\"functionName\":\"" + 
                            functionName + "\"},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                        fw.close();
                    } catch (Exception e) {}
                    // #endregion
                    Log.d(TAG, "Function " + functionName + " handled by agent");
                    return true; // Handled by this agent
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in agent handling " + functionName, e);
                // #region agent log
                try {
                    java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
                    fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " AgentRegistry.handleFunctionCall:AGENT_ERROR " + 
                        "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"H4\",\"location\":\"AgentRegistry.java:handleFunctionCall\",\"message\":\"Error in agent\",\"data\":{\"functionName\":\"" + 
                        functionName + "\",\"error\":\"" + e.getMessage() + "\"},\"timestamp\":" + System.currentTimeMillis() + "}\n");
                    fw.close();
                } catch (Exception ex) {}
                // #endregion
            }
        }
        // #region agent log
        try {
            java.io.FileWriter fw = new java.io.FileWriter("/Users/matedort/NexHacks/.cursor/debug.log", true);
            fw.write(java.util.UUID.randomUUID().toString() + " " + System.currentTimeMillis() + " AgentRegistry.handleFunctionCall:NOT_HANDLED " + 
                "{\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"H4\",\"location\":\"AgentRegistry.java:handleFunctionCall\",\"message\":\"No agent handled function\",\"data\":{\"functionName\":\"" + 
                functionName + "\"},\"timestamp\":" + System.currentTimeMillis() + "}\n");
            fw.close();
        } catch (Exception e) {}
        // #endregion
        Log.w(TAG, "No agent handled function: " + functionName);
        return false; // No agent handled it
    }
    
    public int getAgentCount() {
        return agents.size();
    }
}
