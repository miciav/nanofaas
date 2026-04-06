# Memory Optimization via Storage Deduplication

NanoFaaS includes a sophisticated storage deduplication engine designed to maximize function density and reduce memory overhead, especially in environments running multiple instances of the same function or functions sharing common runtimes (like GraalVM native images).

## The Problem: Memory Duplication in Containers

In a standard containerized environment, each function pod has its own isolated filesystem layers. When you run 10 instances of a 50MB GraalVM native image, the Linux kernel might treat them as 10 different files if they originate from different container layers or separate image pulls. This prevents the kernel from sharing the physical memory pages between these processes, leading to significant RAM wastage.

## The Solution: Inode Sharing via Hard Links

NanoFaaS solves this by implementing a proactive deduplication strategy at the storage level:

1.  **SHA-1 Hashing**: When a function is registered, NanoFaaS analyzes its artifacts and computes a SHA-1 hash for every regular file.
2.  **Common Storage Area**: Unique files are stored in a centralized "common storage" directory, named after their SHA-1 hash.
3.  **Hard Link Consolidation**: If multiple functions (or multiple versions of the same function) contain the exact same file, NanoFaaS replaces the local copy with a **hard link** to the common storage version.
4.  **Kernel Page Cache Sharing**: Because the files now share the same **inode** on the physical disk, the Linux kernel's Page Cache identifies them as the same data. When the first instance loads the file into RAM, all subsequent instances will hit the cache, effectively sharing the physical memory pages.

## Key Benefits

-   **Increased Density**: Up to 28% increase in physical memory sharing between instances.
-   **Reduced RAM Overhead**: Drastic reduction in memory footprint when scaling horizontally.
-   **Faster Startup**: Subsequent instances of a function benefit from data already being warm in the kernel's page cache.
-   **Storage Efficiency**: Eliminates redundant copies of large binaries and libraries on the host disk.

## Configuration

Deduplication is managed by the `storage-deduplicator` module. You can configure it in `application.yml`:

```yaml
nanofaas:
  storage:
    deduplication:
      enabled: true
      base-path: /var/lib/nanofaas/storage
      auto-deduplicate-on-registration: true
```

## Performance Impact

Tests show that deduplicating two 20MB GraalVM-style binaries takes approximately **350ms**, making it a negligible overhead during the function registration phase while providing massive long-term memory savings during execution.
