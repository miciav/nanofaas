#!/bin/bash
#
# Integration tests for HTTP mode
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/test_helpers.sh"

# ============================================================================
# HTTP Mode Tests
# ============================================================================

test_http_success() {
    local test_name="http_success"
    start_test "$test_name"

    # Start callback server
    start_callback_server

    # Start mock HTTP function
    TEST_SCENARIO=success \
    python3 ${FIXTURES_DIR}/http_server.py &
    local http_pid=$!
    wait_for_port 8080

    # Run watchdog
    CALLBACK_URL="http://127.0.0.1:$CALLBACK_PORT/v1/internal/executions" \
    EXECUTION_ID="exec-http-001" \
    EXECUTION_MODE=HTTP \
    TIMEOUT_MS=5000 \
    WATCHDOG_CMD="sleep 0" \
    RUNTIME_URL="http://127.0.0.1:8080/invoke" \
    INVOCATION_PAYLOAD='{"input": "hello"}' \
    $WATCHDOG_BIN &
    local wd_pid=$!

    # Wait for watchdog to complete
    wait $wd_pid || true
    kill $http_pid 2>/dev/null || true

    # Verify callback
    local callback=$(get_last_callback)
    assert_json_field "$callback" ".callback.payload.success" "true" "$test_name"
    assert_json_contains "$callback" "HELLO" "$test_name - uppercase transformation"

    stop_callback_server
    end_test "$test_name"
}

test_http_echo() {
    local test_name="http_echo"
    start_test "$test_name"

    start_callback_server

    TEST_SCENARIO=echo \
    python3 ${FIXTURES_DIR}/http_server.py &
    local http_pid=$!
    wait_for_port 8080

    CALLBACK_URL="http://127.0.0.1:$CALLBACK_PORT/v1/internal/executions" \
    EXECUTION_ID="exec-http-002" \
    EXECUTION_MODE=HTTP \
    TIMEOUT_MS=5000 \
    WATCHDOG_CMD="sleep 0" \
    RUNTIME_URL="http://127.0.0.1:8080/invoke" \
    INVOCATION_PAYLOAD='{"test": "data", "number": 42}' \
    $WATCHDOG_BIN &
    wait $! || true
    kill $http_pid 2>/dev/null || true

    local callback=$(get_last_callback)
    assert_json_field "$callback" ".callback.payload.success" "true" "$test_name"
    assert_json_contains "$callback" '"test"' "$test_name - echoed field"

    stop_callback_server
    end_test "$test_name"
}

test_http_server_error_500() {
    local test_name="http_server_error_500"
    start_test "$test_name"

    start_callback_server

    TEST_SCENARIO=error_500 \
    python3 ${FIXTURES_DIR}/http_server.py &
    local http_pid=$!
    wait_for_port 8080

    CALLBACK_URL="http://127.0.0.1:$CALLBACK_PORT/v1/internal/executions" \
    EXECUTION_ID="exec-http-003" \
    EXECUTION_MODE=HTTP \
    TIMEOUT_MS=5000 \
    WATCHDOG_CMD="sleep 0" \
    RUNTIME_URL="http://127.0.0.1:8080/invoke" \
    INVOCATION_PAYLOAD='{"input": "test"}' \
    $WATCHDOG_BIN &
    wait $! || true
    kill $http_pid 2>/dev/null || true

    local callback=$(get_last_callback)
    assert_json_field "$callback" ".callback.payload.success" "false" "$test_name"
    assert_json_contains "$callback" "FUNCTION_ERROR" "$test_name - error code"

    stop_callback_server
    end_test "$test_name"
}

test_http_timeout() {
    local test_name="http_timeout"
    start_test "$test_name"

    start_callback_server

    TEST_SCENARIO=hang \
    python3 ${FIXTURES_DIR}/http_server.py &
    local http_pid=$!
    wait_for_port 8080

    CALLBACK_URL="http://127.0.0.1:$CALLBACK_PORT/v1/internal/executions" \
    EXECUTION_ID="exec-http-004" \
    EXECUTION_MODE=HTTP \
    TIMEOUT_MS=1000 \
    WATCHDOG_CMD="sleep 0" \
    RUNTIME_URL="http://127.0.0.1:8080/invoke" \
    INVOCATION_PAYLOAD='{"input": "test"}' \
    $WATCHDOG_BIN &
    wait $! || true
    kill $http_pid 2>/dev/null || true

    local callback=$(get_last_callback)
    assert_json_field "$callback" ".callback.payload.success" "false" "$test_name"
    assert_json_contains "$callback" "TIMEOUT" "$test_name - timeout error"

    stop_callback_server
    end_test "$test_name"
}

test_http_slow_startup() {
    local test_name="http_slow_startup"
    start_test "$test_name"

    start_callback_server

    STARTUP_DELAY_MS=2000 \
    TEST_SCENARIO=success \
    python3 ${FIXTURES_DIR}/http_server.py &
    local http_pid=$!

    # Don't wait for port - watchdog should handle this

    CALLBACK_URL="http://127.0.0.1:$CALLBACK_PORT/v1/internal/executions" \
    EXECUTION_ID="exec-http-005" \
    EXECUTION_MODE=HTTP \
    TIMEOUT_MS=10000 \
    READY_TIMEOUT_MS=5000 \
    WATCHDOG_CMD="sleep 0" \
    RUNTIME_URL="http://127.0.0.1:8080/invoke" \
    INVOCATION_PAYLOAD='{"input": "slow"}' \
    $WATCHDOG_BIN &
    wait $! || true
    kill $http_pid 2>/dev/null || true

    local callback=$(get_last_callback)
    assert_json_field "$callback" ".callback.payload.success" "true" "$test_name"

    stop_callback_server
    end_test "$test_name"
}

test_http_startup_timeout() {
    local test_name="http_startup_timeout"
    start_test "$test_name"

    start_callback_server

    # Start a server that takes too long to start
    STARTUP_DELAY_MS=10000 \
    TEST_SCENARIO=success \
    python3 ${FIXTURES_DIR}/http_server.py &
    local http_pid=$!

    CALLBACK_URL="http://127.0.0.1:$CALLBACK_PORT/v1/internal/executions" \
    EXECUTION_ID="exec-http-006" \
    EXECUTION_MODE=HTTP \
    TIMEOUT_MS=5000 \
    READY_TIMEOUT_MS=1000 \
    WATCHDOG_CMD="sleep 0" \
    RUNTIME_URL="http://127.0.0.1:8080/invoke" \
    INVOCATION_PAYLOAD='{"input": "test"}' \
    $WATCHDOG_BIN &
    wait $! || true
    kill $http_pid 2>/dev/null || true

    local callback=$(get_last_callback)
    assert_json_field "$callback" ".callback.payload.success" "false" "$test_name"
    assert_json_contains "$callback" "STARTUP_ERROR" "$test_name - startup error"

    stop_callback_server
    end_test "$test_name"
}

test_http_invalid_json_response() {
    local test_name="http_invalid_json"
    start_test "$test_name"

    start_callback_server

    TEST_SCENARIO=invalid_json \
    python3 ${FIXTURES_DIR}/http_server.py &
    local http_pid=$!
    wait_for_port 8080

    CALLBACK_URL="http://127.0.0.1:$CALLBACK_PORT/v1/internal/executions" \
    EXECUTION_ID="exec-http-007" \
    EXECUTION_MODE=HTTP \
    TIMEOUT_MS=5000 \
    WATCHDOG_CMD="sleep 0" \
    RUNTIME_URL="http://127.0.0.1:8080/invoke" \
    INVOCATION_PAYLOAD='{"input": "test"}' \
    $WATCHDOG_BIN &
    wait $! || true
    kill $http_pid 2>/dev/null || true

    local callback=$(get_last_callback)
    assert_json_field "$callback" ".callback.payload.success" "false" "$test_name"
    assert_json_contains "$callback" "FUNCTION_ERROR" "$test_name - parse error"

    stop_callback_server
    end_test "$test_name"
}

test_http_large_response() {
    local test_name="http_large_response"
    start_test "$test_name"

    start_callback_server

    TEST_SCENARIO=large_response \
    python3 ${FIXTURES_DIR}/http_server.py &
    local http_pid=$!
    wait_for_port 8080

    CALLBACK_URL="http://127.0.0.1:$CALLBACK_PORT/v1/internal/executions" \
    EXECUTION_ID="exec-http-008" \
    EXECUTION_MODE=HTTP \
    TIMEOUT_MS=10000 \
    WATCHDOG_CMD="sleep 0" \
    RUNTIME_URL="http://127.0.0.1:8080/invoke" \
    INVOCATION_PAYLOAD='{"input": "test"}' \
    $WATCHDOG_BIN &
    wait $! || true
    kill $http_pid 2>/dev/null || true

    local callback=$(get_last_callback)
    assert_json_field "$callback" ".callback.payload.success" "true" "$test_name"

    stop_callback_server
    end_test "$test_name"
}

test_http_trace_id_propagation() {
    local test_name="http_trace_id"
    start_test "$test_name"

    start_callback_server

    TEST_SCENARIO=success \
    python3 ${FIXTURES_DIR}/http_server.py &
    local http_pid=$!
    wait_for_port 8080

    CALLBACK_URL="http://127.0.0.1:$CALLBACK_PORT/v1/internal/executions" \
    EXECUTION_ID="exec-http-009" \
    EXECUTION_MODE=HTTP \
    TIMEOUT_MS=5000 \
    TRACE_ID="trace-abc-123" \
    WATCHDOG_CMD="sleep 0" \
    RUNTIME_URL="http://127.0.0.1:8080/invoke" \
    INVOCATION_PAYLOAD='{"input": "trace"}' \
    $WATCHDOG_BIN &
    wait $! || true
    kill $http_pid 2>/dev/null || true

    local callback=$(get_last_callback)
    assert_json_contains "$callback" "trace-abc-123" "$test_name - trace ID in headers"

    stop_callback_server
    end_test "$test_name"
}

# ============================================================================
# Run all HTTP tests
# ============================================================================

run_http_tests() {
    echo "========================================"
    echo "Running HTTP Mode Tests"
    echo "========================================"

    test_http_success
    test_http_echo
    test_http_server_error_500
    test_http_timeout
    test_http_slow_startup
    test_http_startup_timeout
    test_http_invalid_json_response
    test_http_large_response
    test_http_trace_id_propagation

    echo ""
    echo "HTTP Mode Tests: $TESTS_PASSED passed, $TESTS_FAILED failed"
}

# Run if executed directly
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    run_http_tests
fi
