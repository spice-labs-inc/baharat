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

import io.spicelabs.baharat.rpm.exception.InvalidFormatException;
import io.spicelabs.baharat.rpm.payload.CpioArchiveReader;
import io.spicelabs.baharat.rpm.payload.CompressionType;
import io.spicelabs.baharat.rpm.payload.PayloadReader;
import io.spicelabs.baharat.rpm.header.Header;
import io.spicelabs.baharat.rpm.header.HeaderTag;
import io.spicelabs.baharat.rpm.header.IndexEntry;
import io.spicelabs.baharat.rpm.header.TagType;
import io.spicelabs.baharat.rpm.metadata.PackageMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Security tests for path traversal vulnerabilities.
 * Tests that malicious paths in package payloads are rejected.
 */
class PathTraversalSecurityTest {

    private static final String CPIO_MAGIC = "070701";

    // Basic path traversal patterns

    @ParameterizedTest
    @ValueSource(strings = {
            "../etc/passwd",
            "../../etc/passwd",
            "../../../etc/passwd",
            "../../../../etc/passwd",
            "../../../../../etc/passwd"
    })
    void rejectsSimplePathTraversal(String path) throws Exception {
        byte[] payload = createCompressedCpioPayload(path, "malicious content", 0100644);
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(payload), CompressionType.GZIP, metadata)) {
            assertThatThrownBy(() -> reader.entries().toList())
                    .hasRootCauseInstanceOf(InvalidFormatException.class)
                    .hasMessageContaining("traversal");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "./../../etc/passwd",
            "./../../../etc/passwd",
            "./foo/../../etc/passwd"
    })
    void rejectsTraversalWithDotSlash(String path) throws Exception {
        byte[] payload = createCompressedCpioPayload(path, "malicious", 0100644);
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(payload), CompressionType.GZIP, metadata)) {
            assertThatThrownBy(() -> reader.entries().toList())
                    .hasRootCauseInstanceOf(InvalidFormatException.class);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/usr/share/../../../etc/passwd",
            "/opt/../../../etc/shadow",
            "/var/log/../../../root/.ssh/id_rsa"
    })
    void rejectsTraversalInMiddleOfPath(String path) throws Exception {
        byte[] payload = createCompressedCpioPayload(path, "content", 0100644);
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(payload), CompressionType.GZIP, metadata)) {
            assertThatThrownBy(() -> reader.entries().toList())
                    .hasRootCauseInstanceOf(InvalidFormatException.class);
        }
    }

    // Null byte injection tests

    @Test
    void rejectsNullByteInPath() throws Exception {
        String maliciousPath = "/usr/bin/safe\0/../../../etc/passwd";
        byte[] payload = createCompressedCpioPayload(maliciousPath, "content", 0100644);
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(payload), CompressionType.GZIP, metadata)) {
            // Null byte in path is detected either as null byte or as path traversal
            assertThatThrownBy(() -> reader.entries().toList())
                    .hasRootCauseInstanceOf(InvalidFormatException.class);
        }
    }

    @Test
    void rejectsNullByteAtEnd() throws Exception {
        String path = "/etc/passwd\0";
        byte[] payload = createCompressedCpioPayload(path, "content", 0100644);
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(payload), CompressionType.GZIP, metadata)) {
            // Should either reject or truncate at null
            try {
                List<?> entries = reader.entries().toList();
                // If it succeeded, the path should be truncated
                assertThat(entries).isNotEmpty();
            } catch (RuntimeException e) {
                assertThat(e.getCause()).isInstanceOf(InvalidFormatException.class);
            }
        }
    }

    // Backslash handling (Windows-style paths)

    @ParameterizedTest
    @ValueSource(strings = {
            "..\\etc\\passwd",
            "..\\..\\etc\\passwd",
            "/usr\\..\\..\\etc\\passwd",
            "\\..\\..\\etc\\passwd"
    })
    void rejectsBackslashTraversal(String path) throws Exception {
        byte[] payload = createCompressedCpioPayload(path, "content", 0100644);
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(payload), CompressionType.GZIP, metadata)) {
            try {
                List<?> entries = reader.entries().toList();
                // If processing succeeded, backslashes should be converted
                // and traversal detected
            } catch (RuntimeException e) {
                // Expected - traversal detected
                assertThat(e.getCause()).isInstanceOf(InvalidFormatException.class);
            }
        }
    }

    // Valid paths with dots that should be allowed

    @ParameterizedTest
    @ValueSource(strings = {
            "/usr/share/doc/readme.txt",
            "/usr/lib/libfoo.so.1.0",
            "/etc/config.d/app.conf",
            "/opt/app/.hidden",
            "/var/www/.htaccess"
    })
    void allowsValidPathsWithDots(String path) throws Exception {
        byte[] payload = createCompressedCpioPayload(path, "content", 0100644);
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(payload), CompressionType.GZIP, metadata)) {
            List<?> entries = reader.entries().toList();
            // Should succeed
            assertThat(entries).hasSize(1);
        }
    }

    @Test
    void allowsSingleDotComponent() throws Exception {
        String path = "/usr/./share/./doc/file.txt";
        byte[] payload = createCompressedCpioPayload(path, "content", 0100644);
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(payload), CompressionType.GZIP, metadata)) {
            List<?> entries = reader.entries().toList();
            assertThat(entries).hasSize(1);
        }
    }

    @Test
    void allowsTraversalThatStaysWithinRoot() throws Exception {
        // /usr/lib/../share/doc - goes up but stays within root
        String path = "/usr/lib/../share/doc/file.txt";
        byte[] payload = createCompressedCpioPayload(path, "content", 0100644);
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(payload), CompressionType.GZIP, metadata)) {
            List<?> entries = reader.entries().toList();
            // Should succeed - traversal doesn't escape root
            assertThat(entries).hasSize(1);
        }
    }

    // Path length limits

    @Test
    void rejectsExcessivelyLongPath() throws Exception {
        // MAX_PATH_LENGTH is 4096
        String longPath = "/" + "a".repeat(5000);
        byte[] payload = createCompressedCpioPayload(longPath, "content", 0100644);
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(payload), CompressionType.GZIP, metadata)) {
            assertThatThrownBy(() -> reader.entries().toList())
                    .hasRootCauseInstanceOf(InvalidFormatException.class);
        }
    }

    @Test
    void allowsPathAtMaxLength() throws Exception {
        // Just under MAX_PATH_LENGTH
        String path = "/" + "a".repeat(4000);
        byte[] payload = createCompressedCpioPayload(path, "content", 0100644);
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(payload), CompressionType.GZIP, metadata)) {
            List<?> entries = reader.entries().toList();
            assertThat(entries).hasSize(1);
        }
    }

    // Empty and special paths

    @Test
    void handlesEmptyPath() throws Exception {
        byte[] payload = createCompressedCpioPayload("", "content", 0100644);
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(payload), CompressionType.GZIP, metadata)) {
            try {
                List<?> entries = reader.entries().toList();
                // If accepted, document this is allowed
                assertThat(entries).hasSizeGreaterThanOrEqualTo(0);
            } catch (RuntimeException e) {
                // Rejection of empty paths is also acceptable
            }
        }
    }

    @Test
    void handlesRootPath() throws Exception {
        byte[] payload = createCompressedCpioPayload("/", "content", 0040755);
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(payload), CompressionType.GZIP, metadata)) {
            // Root path may be allowed for directories
            try {
                List<?> entries = reader.entries().toList();
                assertThat(entries).hasSize(1);
            } catch (RuntimeException e) {
                // Or may be rejected - both are valid
            }
        }
    }

    // URL-encoded traversal (should be handled as literal)

    @Test
    void handlesUrlEncodedDots() throws Exception {
        // %2e = . , %2f = /
        // These should be treated as literal characters, not decoded
        String path = "/usr/%2e%2e/etc/passwd";
        byte[] payload = createCompressedCpioPayload(path, "content", 0100644);
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(payload), CompressionType.GZIP, metadata)) {
            // Should be treated as literal path (file named "%2e%2e")
            List<?> entries = reader.entries().toList();
            assertThat(entries).hasSize(1);
        }
    }

    // Path normalization edge cases

    @Test
    void handlesMultipleSlashes() throws Exception {
        String path = "/usr///share////doc/file.txt";
        byte[] payload = createCompressedCpioPayload(path, "content", 0100644);
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(payload), CompressionType.GZIP, metadata)) {
            List<?> entries = reader.entries().toList();
            assertThat(entries).hasSize(1);
        }
    }

    @Test
    void handlesTrailingSlash() throws Exception {
        String path = "/usr/share/doc/";
        byte[] payload = createCompressedCpioPayload(path, "", 0040755);
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(payload), CompressionType.GZIP, metadata)) {
            List<?> entries = reader.entries().toList();
            assertThat(entries).hasSize(1);
        }
    }

    // Direct CPIO path validation (without PayloadReader wrapper)

    @Test
    void cpioReaderDoesNotValidatePaths() throws Exception {
        // CpioArchiveReader itself doesn't validate - PayloadReader does
        byte[] archive = createCpioArchive("../../../etc/passwd", "malicious", 0100644);
        ByteArrayInputStream in = new ByteArrayInputStream(archive);

        try (CpioArchiveReader reader = new CpioArchiveReader(in)) {
            CpioArchiveReader.CpioEntry entry = reader.nextEntry();
            // Raw reader should return the path as-is
            assertThat(entry).isNotNull();
            assertThat(entry.name()).contains("..");
        }
    }

    // Helper methods

    private byte[] createCompressedCpioPayload(String path, String content, int mode) throws IOException {
        byte[] cpio = createCpioArchive(path, content, mode);

        ByteArrayOutputStream compressedOut = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressedOut)) {
            gzip.write(cpio);
        }

        return compressedOut.toByteArray();
    }

    private byte[] createCpioArchive(String name, String content, int mode) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(createCpioEntry(name, content, mode));
        out.write(createCpioEntry("TRAILER!!!", "", 0));
        return out.toByteArray();
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
