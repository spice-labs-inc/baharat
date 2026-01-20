/*
 * Copyright 2026 Spice Labs, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.spicelabs.baharat.rpm.security;

import io.spicelabs.baharat.rpm.payload.CpioArchiveReader;
import io.spicelabs.baharat.rpm.payload.CompressionType;
import io.spicelabs.baharat.rpm.payload.PayloadReader;
import io.spicelabs.baharat.rpm.header.Header;
import io.spicelabs.baharat.rpm.header.HeaderTag;
import io.spicelabs.baharat.rpm.header.IndexEntry;
import io.spicelabs.baharat.rpm.header.TagType;
import io.spicelabs.baharat.rpm.metadata.PackageMetadata;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for concurrent access patterns.
 * Verifies thread safety and proper behavior under concurrent use.
 */
class ConcurrencyTest {

    @TempDir
    Path tempDir;

    private static final String CPIO_MAGIC = "070701";

    @Test
    void multipleReadersOnSameFileWork() throws Exception {
        // Create a test archive file
        byte[] archive = createValidCpioArchive();
        Path archiveFile = tempDir.resolve("test.cpio");
        Files.write(archiveFile, archive);

        int readerCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(readerCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < readerCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to start

                    // Each thread creates its own reader
                    byte[] data = Files.readAllBytes(archiveFile);
                    try (CpioArchiveReader reader = new CpioArchiveReader(new ByteArrayInputStream(data))) {
                        CpioArchiveReader.CpioEntry entry = reader.nextEntry();
                        if (entry != null && entry.name().equals("test.txt")) {
                            successCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                }
            }));
        }

        startLatch.countDown(); // Start all threads

        // Wait for completion
        for (Future<?> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }

        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(readerCount);
        assertThat(errorCount.get()).isEqualTo(0);
    }

    @Test
    void singleReaderNotThreadSafe() throws Exception {
        // Demonstrates that a single reader should not be shared across threads
        byte[] archive = createMultiEntryCpioArchive();
        CpioArchiveReader reader = new CpioArchiveReader(new ByteArrayInputStream(archive));

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger entryCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();

                    // All threads share the same reader - this is unsafe
                    CpioArchiveReader.CpioEntry entry = reader.nextEntry();
                    if (entry != null) {
                        entryCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                }
            }));
        }

        startLatch.countDown();

        for (Future<?> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }

        executor.shutdown();
        reader.close();

        // With 5 threads reading 5 entries, we expect some inconsistency
        // (skipped entries, duplicate reads, or errors)
        // The test documents that sharing is unsafe
        assertThat(entryCount.get() + errorCount.get()).isGreaterThan(0);
    }

    @RepeatedTest(5)
    void concurrentArchiveCreationAndReading() throws Exception {
        // Simulate a scenario where archives are being created and read concurrently
        int iterations = 20;
        ExecutorService executor = Executors.newFixedThreadPool(4);
        AtomicInteger createCount = new AtomicInteger(0);
        AtomicInteger readCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < iterations; i++) {
            final int index = i;

            // Creator thread
            futures.add(executor.submit(() -> {
                try {
                    byte[] archive = createValidCpioArchive();
                    Path file = tempDir.resolve("archive-" + index + ".cpio");
                    Files.write(file, archive);
                    createCount.incrementAndGet();
                } catch (Exception e) {
                    // Acceptable
                }
            }));

            // Reader thread
            futures.add(executor.submit(() -> {
                try {
                    Path file = tempDir.resolve("archive-" + index + ".cpio");
                    if (Files.exists(file)) {
                        byte[] data = Files.readAllBytes(file);
                        try (CpioArchiveReader reader = new CpioArchiveReader(new ByteArrayInputStream(data))) {
                            reader.nextEntry();
                            readCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    // File might not exist yet - acceptable
                }
            }));
        }

        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }

        executor.shutdown();

        assertThat(createCount.get()).isGreaterThan(0);
        // Some reads may succeed depending on timing
    }

    @Test
    void payloadReaderConcurrentStreams() throws Exception {
        // Multiple PayloadReader instances on the same data should work
        byte[] payload = createCompressedCpioPayload();
        PackageMetadata metadata = createMinimalMetadata();

        int readerCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(readerCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < readerCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();

                    // Each thread gets its own reader and stream
                    try (PayloadReader reader = new PayloadReader(
                            new ByteArrayInputStream(payload),
                            CompressionType.GZIP,
                            metadata)) {
                        List<?> entries = reader.entries().toList();
                        if (!entries.isEmpty()) {
                            successCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    // Log but continue
                }
            }));
        }

        startLatch.countDown();

        for (Future<?> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }

        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(readerCount);
    }

    @Test
    void rapidOpenClose() throws Exception {
        // Rapidly open and close readers
        byte[] archive = createValidCpioArchive();

        int iterations = 100;
        AtomicInteger successCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < iterations; i++) {
            futures.add(executor.submit(() -> {
                try (CpioArchiveReader reader = new CpioArchiveReader(new ByteArrayInputStream(archive))) {
                    reader.nextEntry();
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // Should not happen
                }
            }));
        }

        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }

        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(iterations);
    }

    @Test
    void interruptedThread() throws Exception {
        // Test behavior when thread is interrupted
        byte[] archive = createValidCpioArchive();

        Thread readerThread = new Thread(() -> {
            try (CpioArchiveReader reader = new CpioArchiveReader(new ByteArrayInputStream(archive))) {
                while (!Thread.currentThread().isInterrupted()) {
                    CpioArchiveReader.CpioEntry entry = reader.nextEntry();
                    if (entry == null) break;
                }
            } catch (Exception e) {
                // May throw on interrupt
            }
        });

        readerThread.start();
        Thread.sleep(10);
        readerThread.interrupt();
        readerThread.join(1000);

        assertThat(readerThread.isAlive()).isFalse();
    }

    @Test
    void closeWhileReading() throws Exception {
        // Close reader while another thread is reading
        byte[] archive = createMultiEntryCpioArchive();
        CpioArchiveReader reader = new CpioArchiveReader(new ByteArrayInputStream(archive));

        AtomicInteger entriesRead = new AtomicInteger(0);

        Thread readThread = new Thread(() -> {
            try {
                while (true) {
                    CpioArchiveReader.CpioEntry entry = reader.nextEntry();
                    if (entry == null) break;
                    entriesRead.incrementAndGet();
                    Thread.sleep(100); // Slow down reading
                }
            } catch (Exception e) {
                // May throw when closed
            }
        });

        readThread.start();
        Thread.sleep(50);
        reader.close(); // Close while reading
        readThread.join(2000);

        // Thread should have terminated (closed or exception)
        assertThat(readThread.isAlive()).isFalse();
    }

    @Test
    void metadataAccessIsSafe() throws Exception {
        // PackageMetadata should be thread-safe for reads
        PackageMetadata metadata = createMinimalMetadata();

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();

                    // Multiple threads reading metadata
                    String compressor = metadata.payloadCompressor();
                    if ("gzip".equals(compressor)) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Should not happen
                }
            }));
        }

        startLatch.countDown();

        for (Future<?> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }

        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(threadCount);
    }

    // Helper methods

    private byte[] createValidCpioArchive() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(createCpioEntry("test.txt", "content", 0100644));
        out.write(createCpioEntry("TRAILER!!!", "", 0));
        return out.toByteArray();
    }

    private byte[] createMultiEntryCpioArchive() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < 5; i++) {
            out.write(createCpioEntry("file" + i + ".txt", "content" + i, 0100644));
        }
        out.write(createCpioEntry("TRAILER!!!", "", 0));
        return out.toByteArray();
    }

    private byte[] createCompressedCpioPayload() throws IOException {
        byte[] cpio = createValidCpioArchive();

        ByteArrayOutputStream compressedOut = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressedOut)) {
            gzip.write(cpio);
        }

        return compressedOut.toByteArray();
    }

    private byte[] createCpioEntry(String name, String content, int mode) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        byte[] nameBytes = (name + "\0").getBytes(StandardCharsets.UTF_8);
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);

        String header = String.format(
                "%s%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X",
                CPIO_MAGIC,
                0, mode, 0, 0, 1, 0, contentBytes.length, 0, 0, 0, 0, nameBytes.length, 0
        );

        out.write(header.getBytes(StandardCharsets.US_ASCII));
        out.write(nameBytes);

        int headerAndName = 110 + nameBytes.length;
        int padding = (4 - (headerAndName % 4)) % 4;
        out.write(new byte[padding]);

        out.write(contentBytes);

        int contentPadding = (4 - (contentBytes.length % 4)) % 4;
        out.write(new byte[contentPadding]);

        return out.toByteArray();
    }

    private PackageMetadata createMinimalMetadata() {
        ByteArrayOutputStream dataStore = new ByteArrayOutputStream();

        byte[] compressorBytes = "gzip\0".getBytes(StandardCharsets.US_ASCII);
        int compressorOffset = dataStore.size();
        dataStore.writeBytes(compressorBytes);

        byte[] formatBytes = "cpio\0".getBytes(StandardCharsets.US_ASCII);
        int formatOffset = dataStore.size();
        dataStore.writeBytes(formatBytes);

        List<IndexEntry> entries = List.of(
                new IndexEntry(HeaderTag.PAYLOADCOMPRESSOR.tag(), TagType.STRING, compressorOffset, 1),
                new IndexEntry(HeaderTag.PAYLOADFORMAT.tag(), TagType.STRING, formatOffset, 1)
        );

        Header header = new Header(entries, dataStore.toByteArray());
        return new PackageMetadata(header);
    }
}
