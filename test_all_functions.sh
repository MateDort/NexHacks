#!/bin/bash

# Comprehensive Function Calling Test
# Tests all functions in TapMate and validates responses

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Test results
TESTS_PASSED=0
TESTS_FAILED=0
TESTS_TOTAL=0

# Log file
LOG_FILE="/tmp/tapmate_comprehensive_test.log"

echo "========================================"
echo "TapMate - All Functions Test"
echo "========================================"
echo ""

# Function to run a single test
run_test() {
    local test_name="$1"
    local voice_command="$2"
    local expected_function="$3"
    local expected_result_pattern="$4"
    
    TESTS_TOTAL=$((TESTS_TOTAL + 1))
    
    echo ""
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}Test #${TESTS_TOTAL}: ${test_name}${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo "Voice command: \"${voice_command}\""
    echo "Expected function: ${expected_function}"
    echo ""
    
    # Force stop and restart app
    echo -e "${YELLOW}→ Restarting app...${NC}"
    adb shell am force-stop com.nexhacks.tapmate
    sleep 1
    adb logcat -c
    
    # Start MainActivity
    adb shell am start -n com.nexhacks.tapmate/.ui.MainActivity > /dev/null 2>&1
    sleep 3
    
    # Start logcat in background
    adb logcat > "$LOG_FILE" &
    LOGCAT_PID=$!
    sleep 1
    
    # Tap microphone button (center of screen)
    SCREEN_SIZE=$(adb shell wm size | grep "Physical size" | cut -d: -f2 | tr -d ' ')
    WIDTH=$(echo $SCREEN_SIZE | cut -dx -f1)
    HEIGHT=$(echo $SCREEN_SIZE | cut -dx -f2)
    TAP_X=$((WIDTH / 2))
    TAP_Y=$((HEIGHT / 2))
    adb shell input tap $TAP_X $TAP_Y
    sleep 2
    
    echo -e "${YELLOW}→ Session started. Say: \"${voice_command}\"${NC}"
    echo "   Press ENTER after speaking..."
    read -t 30 || true
    
    # Wait for processing
    sleep 8
    
    # Stop logcat
    kill $LOGCAT_PID 2>/dev/null
    sleep 1
    
    # Analyze results
    echo ""
    echo -e "${YELLOW}→ Analyzing results...${NC}"
    
    # Check if function was called
    FUNCTION_CALLED=false
    if grep -q "toolCall function: ${expected_function}" "$LOG_FILE" || \
       grep -q "\"name\":\"${expected_function}\"" "$LOG_FILE"; then
        FUNCTION_CALLED=true
        echo -e "${GREEN}  ✓ Function called: ${expected_function}${NC}"
    else
        echo -e "${RED}  ✗ Function NOT called: ${expected_function}${NC}"
    fi
    
    # Check if function was executed
    FUNCTION_EXECUTED=false
    if grep -q "toolCall function executed: ${expected_function}" "$LOG_FILE" || \
       grep -q "=== FUNCTION CALL RECEIVED ===" "$LOG_FILE"; then
        FUNCTION_EXECUTED=true
        echo -e "${GREEN}  ✓ Function executed${NC}"
    else
        echo -e "${RED}  ✗ Function NOT executed${NC}"
    fi
    
    # Check if response was sent
    RESPONSE_SENT=false
    RESPONSE_CONTENT=""
    if grep -q "Function response JSON:" "$LOG_FILE"; then
        RESPONSE_SENT=true
        RESPONSE_CONTENT=$(grep -A 5 "Function response JSON:" "$LOG_FILE" | grep '"response"' | head -1)
        echo -e "${GREEN}  ✓ Response sent back to Gemini${NC}"
        if [ ! -z "$RESPONSE_CONTENT" ]; then
            echo "    Response: ${RESPONSE_CONTENT:0:100}..."
        fi
    else
        echo -e "${RED}  ✗ No response sent${NC}"
    fi
    
    # Check for expected result pattern
    RESULT_VALID=false
    if [ ! -z "$expected_result_pattern" ]; then
        if grep -q "$expected_result_pattern" "$LOG_FILE"; then
            RESULT_VALID=true
            echo -e "${GREEN}  ✓ Expected result pattern found: ${expected_result_pattern}${NC}"
        else
            echo -e "${YELLOW}  ⚠ Expected result pattern not found: ${expected_result_pattern}${NC}"
        fi
    fi
    
    # Check for executableCode (should not appear)
    if grep -q "executableCode" "$LOG_FILE"; then
        echo -e "${YELLOW}  ⚠ Warning: executableCode generated (not critical)${NC}"
    fi
    
    # Determine test result
    if [ "$FUNCTION_CALLED" = true ] && [ "$FUNCTION_EXECUTED" = true ] && [ "$RESPONSE_SENT" = true ]; then
        echo ""
        echo -e "${GREEN}✓ TEST PASSED${NC}"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        echo ""
        echo -e "${RED}✗ TEST FAILED${NC}"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        
        # Show relevant error logs
        echo ""
        echo "Error logs:"
        grep -i "error\|exception" "$LOG_FILE" | grep -i "gemini\|tapmate\|session" | tail -5 || echo "  No errors found"
    fi
    
    # Save detailed log
    cp "$LOG_FILE" "/tmp/tapmate_test_${TESTS_TOTAL}_${expected_function}.log"
    
    sleep 2
}

# ============================================
# Test Suite
# ============================================

echo "This script will test all available functions."
echo "You'll need to speak commands when prompted."
echo ""
read -p "Press ENTER to start testing..."

# Test 1: Google Search
run_test \
    "Google Search" \
    "What's the weather in New York?" \
    "google_search" \
    "query"

# Test 2: Open App
run_test \
    "Open App" \
    "Open Chrome" \
    "gui_open_app" \
    "app_name"

# Test 3: Get Location
run_test \
    "Get Location" \
    "Where am I?" \
    "get_location" \
    "latitude\|longitude\|location"

# Test 4: Memory Save
run_test \
    "Memory Save" \
    "Remember my favorite color is blue" \
    "memory_save" \
    "key.*value"

# Test 5: Memory Recall
run_test \
    "Memory Recall" \
    "What's my favorite color?" \
    "memory_recall" \
    "key"

# Test 6: GUI Click (if screen has elements)
echo ""
echo -e "${YELLOW}Note: GUI Click test requires visible UI elements${NC}"
read -p "Open an app with buttons first, then press ENTER..."
run_test \
    "GUI Click" \
    "Click the first button" \
    "gui_click" \
    "node_id\|text"

# Test 7: GUI Type
run_test \
    "GUI Type" \
    "Type hello world" \
    "gui_type" \
    "text.*hello"

# Test 8: GUI Scroll
run_test \
    "GUI Scroll" \
    "Scroll down" \
    "gui_scroll" \
    "direction"

# Test 9: Maps Navigation
run_test \
    "Maps Navigation" \
    "Navigate to Times Square" \
    "maps_navigation" \
    "destination"

# Test 10: GUI Execute Plan
run_test \
    "GUI Execute Plan" \
    "Send a text message to John saying hello" \
    "gui_execute_plan" \
    "goal\|steps"

# ============================================
# Final Results
# ============================================

echo ""
echo "========================================"
echo "FINAL RESULTS"
echo "========================================"
echo ""
echo "Total Tests: ${TESTS_TOTAL}"
echo -e "${GREEN}Passed: ${TESTS_PASSED}${NC}"
echo -e "${RED}Failed: ${TESTS_FAILED}${NC}"
echo ""

if [ $TESTS_FAILED -eq 0 ]; then
    echo -e "${GREEN}🎉 ALL TESTS PASSED! Function calling is working perfectly!${NC}"
    SUCCESS_RATE=100
else
    SUCCESS_RATE=$((TESTS_PASSED * 100 / TESTS_TOTAL))
    echo -e "${YELLOW}Success Rate: ${SUCCESS_RATE}%${NC}"
    
    if [ $SUCCESS_RATE -ge 80 ]; then
        echo -e "${GREEN}✓ Function calling is mostly working!${NC}"
    elif [ $SUCCESS_RATE -ge 50 ]; then
        echo -e "${YELLOW}⚠ Function calling has issues - review failed tests${NC}"
    else
        echo -e "${RED}✗ Function calling needs significant fixes${NC}"
    fi
fi

echo ""
echo "Detailed logs saved to:"
echo "  /tmp/tapmate_test_*.log"
echo ""
echo "To view a specific test log:"
echo "  cat /tmp/tapmate_test_1_google_search.log | grep -E 'toolCall|Function response|error'"
echo ""

# Generate summary report
SUMMARY_FILE="/tmp/tapmate_test_summary.txt"
cat > "$SUMMARY_FILE" << EOF
TapMate Function Calling Test Summary
Generated: $(date)
======================================

Total Tests: ${TESTS_TOTAL}
Passed: ${TESTS_PASSED}
Failed: ${TESTS_FAILED}
Success Rate: ${SUCCESS_RATE}%

Test Details:
EOF

echo ""
echo "Summary saved to: ${SUMMARY_FILE}"
echo ""
