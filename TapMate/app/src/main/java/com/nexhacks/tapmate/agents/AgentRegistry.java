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
        Log.d(TAG, "Routing function call: " + functionName);
        for (BaseAgent agent : agents) {
            try {
                if (agent.handleFunction(functionName, args, callId)) {
                    Log.d(TAG, "Function " + functionName + " handled by agent");
                    return true; // Handled by this agent
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in agent handling " + functionName, e);
            }
        }
        Log.w(TAG, "No agent handled function: " + functionName);
        return false; // No agent handled it
    }
    
    public int getAgentCount() {
        return agents.size();
    }
}
