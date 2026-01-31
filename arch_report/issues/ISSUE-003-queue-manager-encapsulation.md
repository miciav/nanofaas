# ISSUE-003-queue-manager-encapsulation

**Title:** Fix QueueManager encapsulation leak
**Severity:** Medium
**Impacted Modules:** `control-plane`

## Evidence
- `QueueManager.java` exposes `public Collection<FunctionQueueState> states()`.
- `Scheduler.java` iterates over this collection directly to perform locking and polling.
- This exposes the internal storage structure of the queue manager to the scheduler.

## Proposed Solution
Refactor `QueueManager` to provide a processing method that handles the iteration safely.

## Status: COMPLETED
- Added `forEachQueue` to `QueueManager`.
- Removed `states()` from `QueueManager`.
- Updated `Scheduler.loop()` to use `forEachQueue`.
- Compilation and tests verified.

## Risk
- **Medium**: Changes interaction between Scheduler and Queue.

## Validation
- `QueueManagerTest` and `E2eFlowTest`.
