package com.nexhacks.tapmate.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

public class TapMateAccessibilityService extends AccessibilityService {

    private static final String TAG = "TapMateAccessibility";
    private static TapMateAccessibilityService instance;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.d(TAG, "Service Connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // We can listen to events here if needed
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Service Interrupted");
    }

    public static TapMateAccessibilityService getInstance() {
        return instance;
    }

    // --- GUI AGENT CORE ---

    // 1. Extract Screen State (The "Eyes" for the LLM)
    public String getScreenState() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "[]";

        JSONArray jsonTree = new JSONArray();
        traverseNode(root, jsonTree);
        return jsonTree.toString();
    }

    // Optimized for Tokens: Truncate long text
    private static final int MAX_TEXT_LENGTH = 50;

    private void traverseNode(AccessibilityNodeInfo node, JSONArray jsonArray) {
        if (node == null || !node.isVisibleToUser()) return; // Pruning 1: Invisible nodes

        // Filter: Only add nodes that are meaningful to the Agent
        if (isUsefulNode(node)) {
            try {
                JSONObject jsonNode = new JSONObject();
                jsonNode.put("id", node.getViewIdResourceName());
                
                // Pruning 2: Truncate text to save tokens
                CharSequence text = node.getText();
                if (text != null && text.length() > MAX_TEXT_LENGTH) {
                    jsonNode.put("text", text.subSequence(0, MAX_TEXT_LENGTH) + "...");
                } else {
                    jsonNode.put("text", text);
                }
                
                jsonNode.put("desc", node.getContentDescription());
                jsonNode.put("clickable", node.isClickable());
                jsonNode.put("editable", node.isEditable());
                jsonNode.put("scrollable", node.isScrollable()); // Added scrollable
                
                // Pruning 3: Remove Bounds if not strictly needed (ID is better)
                // Kept for now as fallback, but could be removed to save ~20% more tokens
                Rect bounds = new Rect();
                node.getBoundsInScreen(bounds);
                jsonNode.put("b", bounds.toShortString()); // Shortened key name

                jsonArray.put(jsonNode);
            } catch (Exception e) {
                Log.e(TAG, "Error parsing node", e);
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            traverseNode(node.getChild(i), jsonArray);
        }
    }

    private boolean isUsefulNode(AccessibilityNodeInfo node) {
        // Pruning 4: stricter check
        boolean hasText = node.getText() != null && !node.getText().toString().trim().isEmpty();
        boolean hasDesc = node.getContentDescription() != null;
        boolean isActionable = node.isClickable() || node.isEditable() || node.isScrollable();
        
        return hasText || hasDesc || isActionable;
    }

    // 2. Perform Actions (The "Hands")
    public boolean performClick(String viewId) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;

        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(viewId);
        if (nodes != null && !nodes.isEmpty()) {
            return nodes.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
        return false;
    }

    public boolean performInput(String viewId, String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(viewId);
        if (nodes != null && !nodes.isEmpty()) {
            // Usually requires setting arguments for SET_TEXT
            // Simplified for prototype
            return nodes.get(0).performAction(AccessibilityNodeInfo.ACTION_FOCUS); 
        }
        return false;
    }

    // 3. Smart Extraction (For Memory)
    // "Find the text that contains 'Plate' or 'License'"
    public String extractTextContaining(String keyword) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(keyword);
        if (nodes != null && !nodes.isEmpty()) {
            CharSequence text = nodes.get(0).getText();
            return text != null ? text.toString() : null;
        }
        return null;
    }
}
