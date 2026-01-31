# ISSUE-006-k8s-dispatcher-coupling

**Title:** Decouple KubernetesDispatcher
**Severity:** Low
**Impacted Modules:** `control-plane`

## Evidence
- `KubernetesDispatcher` catches `KubernetesClientException` and manually constructs `InvocationResult` error objects.
- It mixes low-level Fabric8 interaction with domain-level result mapping.

## Proposed Solution
Introduce a cleaner boundary. The Dispatcher should ideally throw typed exceptions or return a domain status, and the mapping to `InvocationResult` (if that's the DTO) should happen higher up or in a consistent mapping layer.

*Note: This is lower priority than the structure changes.*

## Status: COMPLETED
- Extracted `createJob` and `handleError` helper methods in `KubernetesDispatcher`.
- Improved readability and modularity of the `dispatch` method.
- Compilation and tests verified.

## Risk
- **Medium**: Logic change.

## Validation
- `KubernetesDispatcherTest`.
