package it.unimib.datai.nanofaas.modules.storagededuplicator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileDeduplicatorTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldDeduplicateIdenticalFiles() throws IOException {
        Path sourceDir = tempDir.resolve("source");
        Path commonStorageDir = tempDir.resolve("common");
        Files.createDirectories(sourceDir);

        Path file1 = sourceDir.resolve("file1.txt");
        Path file2 = sourceDir.resolve("file2.txt");
        String content = "identical content";
        Files.writeString(file1, content);
        Files.writeString(file2, content);

        FileDeduplicator deduplicator = new FileDeduplicator();
        FileDeduplicator.DeduplicationResult result = deduplicator.deduplicate(sourceDir, commonStorageDir);

        assertEquals(1, result.filesCount());
        assertEquals(content.length(), result.bytesSaved());
        
        // Since both files are identical, they should share the same inode
        assertTrue(Files.isSameFile(file1, file2));
        
        // Verify common storage has exactly one file
        assertEquals(1, Files.list(commonStorageDir).count());
    }

    @Test
    void shouldKeepDifferentFilesSeparate() throws IOException {
        Path sourceDir = tempDir.resolve("source-diff");
        Path commonStorageDir = tempDir.resolve("common-diff");
        Files.createDirectories(sourceDir);

        Path file1 = sourceDir.resolve("file1.txt");
        Path file2 = sourceDir.resolve("file2.txt");
        Files.writeString(file1, "content 1");
        Files.writeString(file2, "content 2");

        FileDeduplicator deduplicator = new FileDeduplicator();
        deduplicator.deduplicate(sourceDir, commonStorageDir);

        assertFalse(Files.isSameFile(file1, file2));
        assertEquals(2, Files.list(commonStorageDir).count());
    }

    @Test
    void shouldHandleLargeIdenticalFilesEfficiently() throws IOException {
        Path sourceDir = tempDir.resolve("source-large");
        Path commonStorageDir = tempDir.resolve("common-large");
        Files.createDirectories(sourceDir);

        // Simulate two 20MB GraalVM native images with identical content
        int size = 20 * 1024 * 1024; // 20MB
        byte[] largeContent = new byte[size];
        for (int i = 0; i < size; i++) {
            largeContent[i] = (byte) (i % 256);
        }

        Path bin1 = sourceDir.resolve("native-image-1");
        Path bin2 = sourceDir.resolve("native-image-2");
        Files.write(bin1, largeContent);
        Files.write(bin2, largeContent);

        FileDeduplicator deduplicator = new FileDeduplicator();
        
        long start = System.currentTimeMillis();
        FileDeduplicator.DeduplicationResult result = deduplicator.deduplicate(sourceDir, commonStorageDir);
        long duration = System.currentTimeMillis() - start;

        System.out.println("Deduplicated two 20MB files in " + duration + "ms");
        
        assertEquals(1, result.filesCount());
        assertEquals(size, result.bytesSaved());
        assertTrue(Files.isSameFile(bin1, bin2));
        assertEquals(1, Files.list(commonStorageDir).count());
        
        // Final sanity check: read back content from linked file
        byte[] readBack = Files.readAllBytes(bin1);
        assertArrayEquals(largeContent, readBack);
    }
}
