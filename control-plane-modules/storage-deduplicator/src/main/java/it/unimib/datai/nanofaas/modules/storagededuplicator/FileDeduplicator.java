package it.unimib.datai.nanofaas.modules.storagededuplicator;

import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class FileDeduplicator {
    private static final Logger log = LoggerFactory.getLogger(FileDeduplicator.class);

    public DeduplicationResult deduplicate(Path sourceDir, Path commonStorageDir) throws IOException {
        if (!Files.exists(sourceDir)) {
            return new DeduplicationResult(0, 0);
        }

        Files.createDirectories(commonStorageDir);
        AtomicInteger filesProcessed = new AtomicInteger(0);
        AtomicLong bytesSaved = new AtomicLong(0);

        Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (attrs.isRegularFile()) {
                    processFile(file, commonStorageDir, filesProcessed, bytesSaved);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return new DeduplicationResult(filesProcessed.get(), bytesSaved.get());
    }

    private void processFile(Path file, Path commonStorageDir, AtomicInteger filesProcessed, AtomicLong bytesSaved) throws IOException {
        String hash;
        try (InputStream is = Files.newInputStream(file)) {
            hash = DigestUtils.sha1Hex(is);
        }

        Path commonPath = commonStorageDir.resolve(hash);

        if (Files.exists(commonPath)) {
            // Check if they are already the same inode (same file)
            if (Files.isSameFile(file, commonPath)) {
                return;
            }
            long size = Files.size(file);
            Files.delete(file);
            Files.createLink(file, commonPath);
            filesProcessed.incrementAndGet();
            bytesSaved.addAndGet(size);
            log.debug("Deduplicated file {} -> {}", file, hash);
        } else {
            // Move to common storage and link back
            Files.move(file, commonPath, StandardCopyOption.ATOMIC_MOVE);
            Files.createLink(file, commonPath);
            log.debug("Stored unique file {} as {}", file, hash);
        }
    }

    public record DeduplicationResult(int filesCount, long bytesSaved) {}
}
