# TARS vs TapMate: Function Calling Analysis

## CRITICAL ISSUE DISCOVERED

From your logs (line 50-54), Gemini is returning:
```json
{
  "executableCode": {
    "language": "PYTHON",
    "code": "print(default_api.google_search(query='weather in Atlanta'))\n"
  }
}
```

**This is CODE EXECUTION, not FUNCTION CALLING!**

## Why This Happens

### Model Configuration Differences

| Aspect | TARS (Working ✅) | TapMate (Broken ❌) |
|--------|-------------------|---------------------|
| **Model Mode** | Function Calling Mode | Code Execution Mode |
| **Response Type** | `functionCall` objects | `executableCode` objects |
| **Tools Configuration** | Registered as callable functions | Interpreted as API descriptions for code gen |
| **Code Execution** | Explicitly disabled | Accidentally enabled |

## Root Cause: Code Execution is Enabled

When Gemini sees:
1. Tool definitions that look like API functions
2. System instructions mentioning execution
3. Code execution mode enabled

It generates **Python code** that CALLS your functions, rather than invoking them directly via the function calling API.

## How TARS Avoids This

TARS likely:
1. **Disables code execution explicitly** in generation config
2. **Uses `tool_config` properly** to force function calling mode
3. **Doesn't mention "execute" or "code" in system instructions**
4. **Sets `tool_choice` to require function use**

## TapMate's Issues

### Issue 1: No Code Execution Control
```java
// TapMate (MISSING)
JSONObject genConfig = new JSONObject();
genConfig.put("response_modalities", new JSONArray().put("AUDIO"));
// ❌ NO: genConfig.put("code_execution_config", ...) 
// ❌ NO: setupContent.put("tool_config", ...)
```

Should be:
```java
// Disable code execution
JSONObject codeExecConfig = new JSONObject();
codeExecConfig.put("enable_code_execution", false);
genConfig.put("code_execution_config", codeExecConfig);

// Force function calling mode
JSONObject toolConfig = new JSONObject();
toolConfig.put("function_calling_config", new JSONObject()
    .put("mode", "AUTO")); // or "ANY" to require functions
setupContent.put("tool_config", toolConfig);
```

### Issue 2: System Instructions Mention "Execute"
Current TapMate instructions:
```
"- For GUI tasks, ALWAYS use gui_execute_plan..."
"- The gui_execute_plan function will ... execute steps automatically"
```

This triggers CODE EXECUTION mode! The word "execute" makes Gemini think you want Python code.

## GUI Agent System Architecture

### Current Wrong Approach (Individual Functions)
```
❌ gui_click(node_id)
❌ gui_type(node_id, text)  
❌ gui_scroll(direction)
```

These are TOO GRANULAR. They're like Selenium commands, not agent commands.

### Correct Approach (Unified GUI Agent)

Based on your description, TapMate should work like Anthropic's Computer Use API or Adept's ACT-1:

```
✅ gui_interact(goal, current_state)
   → Agent analyzes screen
   → Plans multi-step actions
   → Executes via accessibility API
   → Returns success/failure
```

**Example:**
```json
{
  "name": "gui_interact",
  "description": "Interact with the phone's GUI to accomplish a goal using the accessibility API. This is an agentic function that can perform multiple actions (tap, scroll, type, swipe) as needed to achieve the goal.",
  "parameters": {
    "type": "object",
    "properties": {
      "goal": {
        "type": "string",
        "description": "What the user wants to accomplish (e.g., 'Send a message to John saying hello', 'Open Instagram and like the top post', 'Find the weather for Atlanta')"
      },
      "max_steps": {
        "type": "number",
        "description": "Maximum number of interactions to attempt (default: 10)"
      }
    },
    "required": ["goal"]
  }
}
```

### How GUI Agent Should Work Internally

```
User: "Send a message to John"
  ↓
Gemini calls: gui_interact(goal="Send a message to John")
  ↓
TapMate GUI Agent:
  1. Analyzes current screen state (accessibility tree)
  2. Plans: ["Open messaging app", "Find contact John", "Type message", "Send"]
  3. Executes each step using accessibility API:
     - accessibilityService.click(messaging_app_button)
     - accessibilityService.type(search_box, "John")
     - accessibilityService.click(john_contact)
     - accessibilityService.type(message_field, "Hello")
     - accessibilityService.click(send_button)
  4. Returns result: "Message sent to John"
  ↓
Gemini gets result and responds to user: "I've sent your message to John"
```

## Comparison Table

| Feature | TARS | TapMate (Current) | TapMate (Should Be) |
|---------|------|-------------------|---------------------|
| **Function Granularity** | One function per capability | Individual UI actions | Unified GUI agent |
| **Code Execution** | Disabled | Accidentally enabled | Should disable |
| **Tool Config** | Properly configured | Missing | Should add |
| **System Instructions** | No "execute" language | Says "execute plan" | Should change wording |
| **GUI Interaction** | N/A (voice only) | Separate click/type/scroll | Unified agentic interaction |
| **Screen Analysis** | N/A | Passed as JSON | Should be internal to agent |

## What Needs to be Fixed

### 1. Disable Code Execution (CRITICAL)
Add to setup message:
```java
JSONObject codeExecConfig = new JSONObject();
codeExecConfig.put("enable_code_execution", false);
genConfig.put("code_execution_config", codeExecConfig);
```

### 2. Add Tool Config (CRITICAL)
```java
JSONObject toolConfig = new JSONObject();
JSONObject funcCallingConfig = new JSONObject();
funcCallingConfig.put("mode", "AUTO"); // or "ANY"
toolConfig.put("function_calling_config", funcCallingConfig);
setupContent.put("tool_config", toolConfig);
```

### 3. Fix System Instructions
Remove all mentions of "execute", "execution", "code":
```
OLD: "use gui_execute_plan with the user's goal"
NEW: "use gui_interact to accomplish the user's goal"

OLD: "will create a todo list and execute steps automatically"
NEW: "will analyze the screen and perform the necessary interactions"
```

### 4. Refactor GUI Functions
Consolidate into one agentic function:
- Remove: `gui_click`, `gui_type`, `gui_scroll`, `gui_open_app`
- Add: `gui_interact(goal, current_screen_state)` 
- Internal: GUI agent plans and executes using accessibility API

### 5. Use Correct Response Format
The function response should use `clientContent` format (which we already fixed), but FIRST we need Gemini to actually CALL functions instead of generating code.

## Testing Priority

1. **Test 1**: Check if code execution appears in logs (`executableCode`)
2. **Test 2**: Check if function calls appear (`functionCall`)
3. **Test 3**: Verify setup message includes code_execution_config
4. **Test 4**: Verify tool_config is present
5. **Test 5**: Test actual function calling with simple command

## References

- Gemini Live API docs: https://ai.google.dev/gemini-api/docs/live
- Function calling spec: https://ai.google.dev/gemini-api/docs/function-calling
- Code execution docs: https://ai.google.dev/gemini-api/docs/code-execution

---

**NEXT STEPS**: 
1. Run the test script to confirm code execution is the issue
2. Add code_execution_config to disable it
3. Add tool_config to enable proper function calling
4. Rebuild and test
