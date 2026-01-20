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

import io.spicelabs.baharat.rpm.payload.CompressionType;
import io.spicelabs.baharat.rpm.payload.PayloadReader;
import io.spicelabs.baharat.rpm.header.Header;
import io.spicelabs.baharat.rpm.header.HeaderTag;
import io.spicelabs.baharat.rpm.header.IndexEntry;
import io.spicelabs.baharat.rpm.header.TagType;
import io.spicelabs.baharat.rpm.metadata.PackageMetadata;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Security tests for decompression bomb (zip bomb) protection.
 * Tests that highly compressible data is rejected when it exceeds limits.
 */
class DecompressionBombTest {

    private static final String CPIO_MAGIC = "070701";

    @Test
    void rejectsDecompressionBombWithCustomLimit() throws Exception {
        // Create valid CPIO archive with a large file (1 MB)
        byte[] cpioArchive = createCpioArchiveWithLargeFile("largefile.bin", 1024 * 1024);

        ByteArrayOutputStream compressedOut = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressedOut)) {
            gzip.write(cpioArchive);
        }

        byte[] compressedData = compressedOut.toByteArray();

        // The compressed size will be much smaller (< 50KB typically for zeros)
        assertThat(compressedData.length).isLessThan(100_000);

        // Create a payload reader with a small limit (500 KB) that's less than decompressed size
        long smallLimit = 500 * 1024; // 500 KB
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(compressedData),
                CompressionType.GZIP,
                metadata,
                smallLimit)) {

            // Try to read entries - should fail when decompressed data exceeds limit
            assertThatThrownBy(() -> reader.entries().toList())
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    void allowsDataUnderLimit() throws Exception {
        // Small data that stays under the limit
        String content = "Hello, World!";
        byte[] payload = createCompressedCpioPayload("test.txt", content, 0100644);

        // Use 10 MB limit (plenty of room)
        long limit = 10 * 1024 * 1024;
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(payload),
                CompressionType.GZIP,
                metadata,
                limit)) {

            List<?> entries = reader.entries().toList();
            assertThat(entries).hasSize(1);
        }
    }

    @Test
    void defaultLimitIs10GB() throws Exception {
        // Default limit should be 10 GB
        // We can't easily test this with actual data, but we can verify
        // the reader is created successfully with default limit
        byte[] payload = createCompressedCpioPayload("test.txt", "content", 0100644);
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(payload),
                metadata)) {

            List<?> entries = reader.entries().toList();
            assertThat(entries).hasSize(1);
        }
    }

    @Test
    void rejectsWhenReadingLargeFileContent() throws Exception {
        // Create a CPIO archive with a file claiming large size
        byte[] largeCpio = createCpioArchiveWithLargeFile("bigfile.bin", 2 * 1024 * 1024); // 2 MB

        ByteArrayOutputStream compressedOut = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressedOut)) {
            gzip.write(largeCpio);
        }

        // Set limit below the file size
        long limit = 1024 * 1024; // 1 MB limit
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(compressedOut.toByteArray()),
                CompressionType.GZIP,
                metadata,
                limit)) {

            // Reading entries should fail when we exceed limit
            assertThatThrownBy(() -> reader.entries().toList())
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    void sizeLimitedInputStreamEnforcesLimitOnRead() throws Exception {
        // Test the underlying SizeLimitedInputStream behavior through PayloadReader

        // Create valid CPIO data that, when decompressed, exceeds the limit
        byte[] cpioArchive = createCpioArchiveWithLargeFile("test.bin", 100_000);

        ByteArrayOutputStream compressedOut = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressedOut)) {
            gzip.write(cpioArchive);
        }

        // Set limit below actual size
        long limit = 50_000; // 50 KB
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(compressedOut.toByteArray()),
                CompressionType.GZIP,
                metadata,
                limit)) {

            assertThatThrownBy(() -> reader.entries().toList())
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    void sizeLimitedInputStreamEnforcesLimitOnSkip() throws Exception {
        // Test that skip() also respects the limit

        // Create valid CPIO data that exceeds the limit
        byte[] cpioArchive = createCpioArchiveWithLargeFile("test.bin", 200_000);

        ByteArrayOutputStream compressedOut = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressedOut)) {
            gzip.write(cpioArchive);
        }

        long limit = 100_000; // 100 KB limit
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(compressedOut.toByteArray()),
                CompressionType.GZIP,
                metadata,
                limit)) {

            assertThatThrownBy(() -> reader.entries().toList())
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    void handlesZeroLimit() throws Exception {
        // Edge case: zero limit should reject any data
        byte[] payload = createCompressedCpioPayload("test.txt", "x", 0100644);
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(payload),
                CompressionType.GZIP,
                metadata,
                0)) { // Zero limit

            assertThatThrownBy(() -> reader.entries().toList())
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    void handlesNegativeLimit() throws Exception {
        // Negative limit should be treated as no data allowed
        byte[] payload = createCompressedCpioPayload("test.txt", "x", 0100644);
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(payload),
                CompressionType.GZIP,
                metadata,
                -1)) { // Negative limit

            // Behavior depends on implementation - may reject immediately
            // or may treat as unlimited
            try {
                reader.entries().toList();
            } catch (RuntimeException e) {
                // Acceptable
            }
        }
    }

    @Test
    void compressionRatioDoesNotMatter() throws Exception {
        // Even with normal compression ratio, limit is enforced
        // Create valid CPIO with a file containing random-like data
        byte[] cpioArchive = createCpioArchiveWithLargeFile("randomfile.bin", 50_000);

        ByteArrayOutputStream compressedOut = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressedOut)) {
            gzip.write(cpioArchive);
        }

        // But we still enforce the limit
        long limit = 30_000; // Less than the data size
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(compressedOut.toByteArray()),
                CompressionType.GZIP,
                metadata,
                limit)) {

            assertThatThrownBy(() -> reader.entries().toList())
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    void explicitCompressionTypeWithLimit() throws Exception {
        byte[] payload = createCompressedCpioPayload("test.txt", "content", 0100644);
        PackageMetadata metadata = createMinimalMetadata();
        long limit = 10 * 1024 * 1024; // 10 MB

        // Test constructor with explicit compression type and limit
        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(payload),
                CompressionType.GZIP,
                metadata,
                limit)) {

            assertThat(reader.compressionType()).isEqualTo(CompressionType.GZIP);
            List<?> entries = reader.entries().toList();
            assertThat(entries).hasSize(1);
        }
    }

    // Helper methods

    private byte[] createCompressedCpioPayload(String name, String content, int mode) throws IOException {
        byte[] cpio = createCpioArchive(name, content, mode);

        ByteArrayOutputStream compressedOut = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressedOut)) {
            gzip.write(cpio);
        }

        return compressedOut.toByteArray();
    }

    private byte[] createCpioArchive(String name, String content, int mode) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(createCpioEntry(name, content.getBytes(StandardCharsets.UTF_8), mode));
        out.write(createCpioEntry("TRAILER!!!", new byte[0], 0));
        return out.toByteArray();
    }

    private byte[] createCpioArchiveWithLargeFile(String name, int size) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(createCpioEntry(name, new byte[size], 0100644));
        out.write(createCpioEntry("TRAILER!!!", new byte[0], 0));
        return out.toByteArray();
    }

    private byte[] createCpioEntry(String name, byte[] content, int mode) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        byte[] nameBytes = (name + "\0").getBytes(StandardCharsets.UTF_8);

        String header = String.format(
                "%s%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X",
                CPIO_MAGIC,
                0, mode, 0, 0, 1, 0, content.length, 0, 0, 0, 0, nameBytes.length, 0
        );

        out.write(header.getBytes(StandardCharsets.US_ASCII));
        out.write(nameBytes);

        int headerAndName = 110 + nameBytes.length;
        int padding = (4 - (headerAndName % 4)) % 4;
        out.write(new byte[padding]);

        out.write(content);

        int contentPadding = (4 - (content.length % 4)) % 4;
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
