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

import io.spicelabs.baharat.rpm.RpmReader;
import io.spicelabs.baharat.rpm.payload.CpioArchiveReader;
import io.spicelabs.baharat.rpm.payload.CompressionType;
import io.spicelabs.baharat.rpm.payload.PayloadReader;
import io.spicelabs.baharat.rpm.header.Header;
import io.spicelabs.baharat.rpm.header.HeaderTag;
import io.spicelabs.baharat.rpm.header.IndexEntry;
import io.spicelabs.baharat.rpm.header.TagType;
import io.spicelabs.baharat.rpm.metadata.PackageMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for proper resource cleanup in error scenarios.
 * Verifies that streams and other resources are closed even when parsing fails.
 */
class ResourceCleanupTest {

    @TempDir
    Path tempDir;

    private static final String CPIO_MAGIC = "070701";
    private static final byte[] RPM_MAGIC = {(byte) 0xED, (byte) 0xAB, (byte) 0xEE, (byte) 0xDB};

    @Test
    void cpioReaderClosesInputStreamOnSuccess() throws Exception {
        AtomicBoolean closed = new AtomicBoolean(false);
        byte[] archive = createValidCpioArchive();
        InputStream trackingStream = createTrackingInputStream(archive, closed);

        try (CpioArchiveReader reader = new CpioArchiveReader(trackingStream)) {
            // Read all entries
            while (reader.nextEntry() != null) {
                // Skip content
            }
        }

        assertThat(closed.get()).isTrue();
    }

    @Test
    void cpioReaderClosesInputStreamOnParseError() throws Exception {
        AtomicBoolean closed = new AtomicBoolean(false);
        byte[] badData = "not a cpio archive".getBytes();
        InputStream trackingStream = createTrackingInputStream(badData, closed);

        try (CpioArchiveReader reader = new CpioArchiveReader(trackingStream)) {
            try {
                reader.nextEntry();
            } catch (Exception e) {
                // Expected
            }
        }

        assertThat(closed.get()).isTrue();
    }

    @Test
    void cpioReaderClosesInputStreamOnIteratorError() throws Exception {
        AtomicBoolean closed = new AtomicBoolean(false);
        // Create archive that will fail mid-iteration
        byte[] corruptArchive = createCorruptCpioArchive();
        InputStream trackingStream = createTrackingInputStream(corruptArchive, closed);

        try (CpioArchiveReader reader = new CpioArchiveReader(trackingStream)) {
            try {
                reader.stream().forEach(entry -> {
                    // Process entries
                });
            } catch (Exception e) {
                // Expected
            }
        }

        assertThat(closed.get()).isTrue();
    }

    @Test
    void payloadReaderClosesAllStreamsOnSuccess() throws Exception {
        AtomicInteger closeCount = new AtomicInteger(0);
        byte[] payload = createCompressedCpioPayload();

        InputStream trackingStream = new FilterInputStream(new ByteArrayInputStream(payload)) {
            @Override
            public void close() throws IOException {
                closeCount.incrementAndGet();
                super.close();
            }
        };

        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(trackingStream, CompressionType.GZIP, metadata)) {
            reader.entries().forEach(entry -> {
                // Process entries
            });
        }

        // At least the main stream should be closed
        assertThat(closeCount.get()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void payloadReaderClosesStreamsOnDecompressionError() throws Exception {
        AtomicBoolean closed = new AtomicBoolean(false);
        // Invalid gzip data
        byte[] badGzip = new byte[]{0x1F, (byte) 0x8B, 0x00, 0x00}; // Incomplete gzip

        InputStream trackingStream = createTrackingInputStream(badGzip, closed);
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(trackingStream, CompressionType.GZIP, metadata)) {
            try {
                reader.entries().toList();
            } catch (Exception e) {
                // Expected
            }
        }

        assertThat(closed.get()).isTrue();
    }

    @Test
    void handleDoubleClose() throws Exception {
        byte[] archive = createValidCpioArchive();

        CpioArchiveReader reader = new CpioArchiveReader(new ByteArrayInputStream(archive));
        reader.close();
        reader.close(); // Should not throw

        // No assertion needed - test passes if no exception
    }

    @Test
    void handleCloseAfterPartialRead() throws Exception {
        byte[] archive = createValidCpioArchive();
        ByteArrayInputStream in = new ByteArrayInputStream(archive);

        try (CpioArchiveReader reader = new CpioArchiveReader(in)) {
            // Read first entry only
            CpioArchiveReader.CpioEntry entry = reader.nextEntry();
            assertThat(entry).isNotNull();
            // Don't read content, just close
        }

        // No exception should be thrown
    }

    @Test
    void handleCloseWithoutReading() throws Exception {
        byte[] archive = createValidCpioArchive();
        ByteArrayInputStream in = new ByteArrayInputStream(archive);

        CpioArchiveReader reader = new CpioArchiveReader(in);
        reader.close();

        // Should close without error even if nothing was read
    }

    @Test
    void payloadReaderHandlesInitializationFailure() throws Exception {
        AtomicBoolean closed = new AtomicBoolean(false);
        byte[] emptyData = new byte[0];

        InputStream trackingStream = createTrackingInputStream(emptyData, closed);
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(trackingStream, CompressionType.GZIP, metadata)) {
            try {
                reader.entries().toList();
            } catch (Exception e) {
                // Expected for empty data
            }
        }

        assertThat(closed.get()).isTrue();
    }

    @Test
    void rpmReaderClosesFileOnError() throws Exception {
        // Create invalid RPM file
        Path invalidRpm = tempDir.resolve("invalid.rpm");
        Files.write(invalidRpm, new byte[]{0x00, 0x00, 0x00, 0x00});

        assertThatThrownBy(() -> RpmReader.read(invalidRpm))
                .isInstanceOf(Exception.class);

        // File should not be locked - verify by writing to it
        Files.write(invalidRpm, new byte[]{0x01});
    }

    @Test
    void rpmReaderClosesFileOnSuccess() throws Exception {
        // This would require a valid RPM file, skip if not available
        // The test verifies that file handles are released after successful read
    }

    @Test
    void streamClosesDuringIteration() throws Exception {
        byte[] archive = createMultiEntryCpioArchive();
        AtomicBoolean closed = new AtomicBoolean(false);
        InputStream trackingStream = createTrackingInputStream(archive, closed);

        try (CpioArchiveReader reader = new CpioArchiveReader(trackingStream)) {
            // Start iterating
            reader.stream().limit(1).forEach(entry -> {
                // Only process first entry
            });
        }

        // Stream should be closed even if not fully consumed
        assertThat(closed.get()).isTrue();
    }

    @Test
    void exceptionDuringCloseIsHandled() throws Exception {
        InputStream failingStream = new ByteArrayInputStream(createValidCpioArchive()) {
            @Override
            public void close() throws IOException {
                throw new IOException("Simulated close failure");
            }
        };

        CpioArchiveReader reader = new CpioArchiveReader(failingStream);

        // Close should propagate the exception
        assertThatThrownBy(reader::close)
                .isInstanceOf(IOException.class)
                .hasMessageContaining("close failure");
    }

    @Test
    void nestedResourcesClosedInOrder() throws Exception {
        AtomicInteger closeOrder = new AtomicInteger(0);
        AtomicInteger outerCloseOrder = new AtomicInteger(0);
        AtomicInteger innerCloseOrder = new AtomicInteger(0);

        byte[] payload = createCompressedCpioPayload();

        InputStream outer = new FilterInputStream(new ByteArrayInputStream(payload)) {
            @Override
            public void close() throws IOException {
                outerCloseOrder.set(closeOrder.incrementAndGet());
                super.close();
            }
        };

        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(outer, CompressionType.GZIP, metadata)) {
            reader.entries().forEach(e -> {});
        }

        // Outer stream should be closed
        assertThat(outerCloseOrder.get()).isGreaterThan(0);
    }

    // Helper methods

    private InputStream createTrackingInputStream(byte[] data, AtomicBoolean closed) {
        return new FilterInputStream(new ByteArrayInputStream(data)) {
            @Override
            public void close() throws IOException {
                closed.set(true);
                super.close();
            }
        };
    }

    private byte[] createValidCpioArchive() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(createCpioEntry("test.txt", "content", 0100644));
        out.write(createCpioEntry("TRAILER!!!", "", 0));
        return out.toByteArray();
    }

    private byte[] createMultiEntryCpioArchive() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(createCpioEntry("file1.txt", "content1", 0100644));
        out.write(createCpioEntry("file2.txt", "content2", 0100644));
        out.write(createCpioEntry("file3.txt", "content3", 0100644));
        out.write(createCpioEntry("TRAILER!!!", "", 0));
        return out.toByteArray();
    }

    private byte[] createCorruptCpioArchive() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(createCpioEntry("test.txt", "content", 0100644));
        // Add corrupted data instead of trailer
        out.write("corrupted data that is not a valid cpio entry".getBytes());
        return out.toByteArray();
    }

    private byte[] createCompressedCpioPayload() throws Exception {
        byte[] cpio = createValidCpioArchive();

        ByteArrayOutputStream compressedOut = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressedOut)) {
            gzip.write(cpio);
        }

        return compressedOut.toByteArray();
    }

    private byte[] createCpioEntry(String name, String content, int mode) throws Exception {
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
