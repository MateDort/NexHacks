# Testing Instructions - Function Calling Fix

## What Was Fixed

### Critical Fixes Applied:
1. ✅ **Disabled code execution** - Prevents `executableCode` responses
2. ✅ **Enabled function calling mode** - Adds `tool_config` with function_calling_config
3. ✅ **Fixed response format** - Uses `clientContent` structure (matches TARS)
4. ✅ **Fixed type names** - Lowercase (object, string, number)
5. ✅ **Removed "execute" language** - System instructions no longer trigger code mode

## Testing Method 1: Automated Test Script

```bash
/Users/matedort/NexHacks/test_function_calling.sh
```

Follow the prompts:
1. Script will start logcat capture
2. Open TapMate and start session
3. Say: "What's the weather in Atlanta?"
4. Wait 10 seconds
5. Press ENTER
6. Script analyzes results

**Expected Result:**
- ✅ "FOUND functionCall - Function calling is working!"
- ❌ No "executableCode" found

## Testing Method 2: Manual Testing

### Step 1: Start Fresh Logcat
```bash
adb logcat -c
adb logcat | grep -E "(functionCall|executableCode|HYPOTHESIS)" > /tmp/tapmate_test.log &
```

### Step 2: Test Voice Command
1. Open TapMate on your phone
2. Start a voice session
3. Say one of these:
   - "What's the weather?"
   - "Search Google for pizza places"
   - "What's my location?"

### Step 3: Check Results
```bash
# Stop the logcat (Ctrl+C in the terminal running it)

# Check for good output (functionCall)
grep "functionCall" /tmp/tapmate_test.log

# Check for bad output (executableCode)  
grep "executableCode" /tmp/tapmate_test.log
```

**Success Indicators:**
- See "functionCall" in logs ✅
- NO "executableCode" in logs ✅
- See "HYPOTHESIS D: Function call DETECTED" ✅
- See "Callback invoked successfully" ✅

**Failure Indicators:**
- See "executableCode" ❌
- NO "functionCall" ❌
- Python code strings in output ❌

## Testing Method 3: Direct Log Observation

```bash
adb logcat -c
adb logcat | grep -A 5 "functionCall\|executableCode"
```

Then test voice commands and watch in real-time.

## Test Scenarios

### Scenario 1: Weather Query
**Command**: "What's the weather in Atlanta?"

**Expected Flow**:
```
1. Gemini receives audio + screen state + tools
2. Gemini calls: functionCall("google_search", {query: "weather in Atlanta"})
3. TapMate executes search
4. TapMate sends result back
5. Gemini responds with weather info
```

**Look for in logs**:
```
D/GeminiLiveClient: Found functionCall in part
D/GeminiLiveClient: Function call: google_search
D/SessionActivity: FUNCTION_CALL_RECEIVED: google_search
D/GeminiLiveClient: Sent function response for: google_search
```

### Scenario 2: GUI Interaction
**Command**: "Click the search button"

**Expected Flow**:
```
1. Gemini calls: functionCall("gui_click", {node_id: "search_button"})
2. TapMate uses accessibility service to click
3. Returns success/failure
4. Gemini confirms action
```

**Look for**:
```
D/GeminiLiveClient: Function call: gui_click  
D/SessionActivity: FUNCTION_CALL_RECEIVED: gui_click
```

### Scenario 3: Memory Save
**Command**: "Remember my favorite color is blue"

**Expected Flow**:
```
1. Gemini calls: functionCall("memory_save", {key: "favorite_color", value: "blue"})
2. TapMate saves to memory
3. Confirms saved
```

## What to Look For

### ✅ GOOD - Function Calling Working

```
Message preview: {"serverContent":{"modelTurn":{"parts":[{"functionCall":{"name":"google_search"...
===== HYPOTHESIS D: Function call DETECTED =====
Found functionCall in part
Function call #0: google_search
Invoking callback.onFunctionCall()
Callback invoked successfully for: google_search
FUNCTION_CALL_RECEIVED: google_search
Sending function response
Function response sent via WebSocket
```

### ❌ BAD - Code Execution Mode Active

```
Message preview: {"serverContent":{"modelTurn":{"parts":[{"executableCode":{"language":"PYTHON"...
Binary message is actually JSON, parsing: { "executableCode": { "language": "PYTHON", "code": "print(default_api.google_search..."
```

## Debugging

### If Still Seeing executableCode:

1. **Check setup message was sent:**
```bash
adb logcat | grep "Setup message sent"
```

2. **Verify code_execution_config:**
```bash
adb logcat | grep -A 20 "Setup message" | grep "code_execution"
```

3. **Check tool_config:**
```bash
adb logcat | grep -A 20 "Setup message" | grep "tool_config"
```

### If No Functions Called:

1. **Verify tools were registered:**
```bash
adb logcat | grep "Tools created"
# Should show: "functionsCount: 10"
```

2. **Check setup complete:**
```bash
adb logcat | grep "Setup complete"
```

3. **Verify connection:**
```bash
adb logcat | grep "WebSocket connected"
```

## Comparison: Before vs After

### BEFORE FIX ❌
```
User: "What's the weather?"
Gemini: {"executableCode": {"language": "PYTHON", "code": "print(default_api.google_search(...))"}}
TapMate: ❌ Doesn't understand Python code
User: ❌ No response
```

### AFTER FIX ✅
```
User: "What's the weather?"
Gemini: {"functionCall": {"name": "google_search", "args": {"query": "weather"}}}
TapMate: ✅ Executes google_search
TapMate: ✅ Returns results to Gemini
Gemini: ✅ "The weather is sunny, 75°F"
User: ✅ Hears response!
```

## Success Checklist

- [ ] Rebuilt app with fixes
- [ ] Installed on device
- [ ] Can start voice session
- [ ] Voice command triggers function call
- [ ] See "functionCall" in logs
- [ ] NO "executableCode" in logs
- [ ] Function callback invoked
- [ ] Function response sent back
- [ ] Gemini responds to user

## Files Created

1. `/Users/matedort/NexHacks/test_function_calling.sh` - Automated test script
2. `/Users/matedort/NexHacks/TARS_VS_TAPMATE_ANALYSIS.md` - Detailed analysis
3. `/Users/matedort/NexHacks/FUNCTION_CALLING_FIX_SUMMARY.md` - Fix summary
4. `/Users/matedort/NexHacks/TESTING_INSTRUCTIONS.md` - This file

## Next Steps After Verification

Once function calling works:

1. **Test all 10 functions** to ensure they all work
2. **Consider GUI agent refactor** - Consolidate into unified `gui_interact(goal)` function
3. **Remove debug logs** - Clean up HYPOTHESIS logging
4. **Performance testing** - Test with multiple rapid commands
5. **Error handling** - Test what happens when functions fail

---

**Status**: Ready for Testing  
**Expected Outcome**: Function calling should now work exactly like TARS!
