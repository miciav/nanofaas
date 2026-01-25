# SLO and Performance Targets

## Targets (placeholder)

- Define p95/p99 latency per function once benchmarks are available.
- Track cold-start distribution for Job-based execution.

## Metrics mapping

- Latency: `function_latency_ms{function}`
- Queue depth: `function_queue_depth{function}`
- Success/error: `function_success_total{function}`, `function_error_total{function}`
- Retry: `function_retry_total{function}`
