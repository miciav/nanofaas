# ISSUE-004-scheduler-complexity

**Title:** Reduce Scheduler complexity
**Severity:** Low
**Impacted Modules:** `control-plane`

## Evidence
- `Scheduler.java` handles:
    - Thread lifecycle (`SmartLifecycle`).
    - Polling loop with sleep.
    - Dispatching logic branching (`if LOCAL`, `if POOL`, `if REMOTE`).
    - Error handling for dispatch results.

## Proposed Solution
Delegate dispatch logic to a `DispatchService` or ensure `DispatcherRouter` handles the completion logic consistently.

## Status: COMPLETED
- Moved dispatch logic from `Scheduler` to `InvocationService.dispatch()`.
- Simplified `Scheduler` to focus on the loop only.
- Updated `InvocationService` to orchestrate execution flow.
- Compilation and tests verified.

## Risk
- **Low**: Refactoring within class.

## Validation
- `E2eFlowTest`.
