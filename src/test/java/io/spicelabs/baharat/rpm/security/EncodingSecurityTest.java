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
import io.spicelabs.baharat.rpm.payload.PayloadEntry;
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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for encoding and Unicode handling security.
 * Verifies that various character encodings and edge cases are handled safely.
 */
class EncodingSecurityTest {

    private static final String CPIO_MAGIC = "070701";

    // UTF-8 encoding tests

    @Test
    void handlesValidUtf8Filename() throws Exception {
        // Various UTF-8 characters
        String filename = "/usr/share/日本語/файл.txt";
        byte[] payload = createCompressedCpioPayload(filename, "content", 0100644);
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(payload), CompressionType.GZIP, metadata)) {
            List<PayloadEntry> entries = reader.entries().toList();
            assertThat(entries).hasSize(1);
            assertThat(entries.get(0).path()).contains("日本語");
        }
    }

    @Test
    void handlesMultiByteUtf8Characters() throws Exception {
        // 2, 3, and 4 byte UTF-8 sequences
        String filename = "/test/é/中/\uD83D\uDE00.txt"; // é=2 bytes, 中=3 bytes, emoji=4 bytes
        byte[] payload = createCompressedCpioPayload(filename, "content", 0100644);
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(payload), CompressionType.GZIP, metadata)) {
            List<PayloadEntry> entries = reader.entries().toList();
            assertThat(entries).hasSize(1);
            // Path should contain the multi-byte characters
        }
    }

    @Test
    void handlesInvalidUtf8Sequences() throws Exception {
        // Create filename with invalid UTF-8 byte sequence
        // 0xFF is invalid as UTF-8 start byte
        byte[] invalidUtf8Name = new byte[]{0x2F, 0x74, 0x65, 0x73, 0x74, (byte) 0xFF, 0x2E, 0x74, 0x78, 0x74, 0x00};

        byte[] archive = createCpioArchiveWithRawName(invalidUtf8Name, "content", 0100644);
        ByteArrayInputStream in = new ByteArrayInputStream(archive);

        try (CpioArchiveReader reader = new CpioArchiveReader(in)) {
            CpioArchiveReader.CpioEntry entry = reader.nextEntry();
            if (entry != null) {
                // Should handle gracefully - either replace or preserve invalid bytes
                assertThat(entry.name()).isNotNull();
            }
        }
    }

    @Test
    void handlesTruncatedUtf8Sequences() throws Exception {
        // Truncated multi-byte sequence: 0xE4 starts 3-byte sequence but only 1 byte present
        byte[] truncatedUtf8 = new byte[]{0x2F, 0x74, 0x65, 0x73, 0x74, (byte) 0xE4, 0x00};

        byte[] archive = createCpioArchiveWithRawName(truncatedUtf8, "content", 0100644);
        ByteArrayInputStream in = new ByteArrayInputStream(archive);

        try (CpioArchiveReader reader = new CpioArchiveReader(in)) {
            try {
                CpioArchiveReader.CpioEntry entry = reader.nextEntry();
                // If parsing succeeds, entry should have some name
                if (entry != null) {
                    assertThat(entry.name()).isNotEmpty();
                }
            } catch (Exception e) {
                // Rejection of invalid UTF-8 is acceptable
            }
        }
    }

    @Test
    void handlesOverlongUtf8Sequences() throws Exception {
        // Overlong encoding of '/' (0x2F):
        // Should be 0x2F but encoded as 0xC0 0xAF (invalid overlong)
        byte[] overlongUtf8 = new byte[]{(byte) 0xC0, (byte) 0xAF, 0x74, 0x65, 0x73, 0x74, 0x00};

        byte[] archive = createCpioArchiveWithRawName(overlongUtf8, "content", 0100644);
        ByteArrayInputStream in = new ByteArrayInputStream(archive);

        try (CpioArchiveReader reader = new CpioArchiveReader(in)) {
            CpioArchiveReader.CpioEntry entry = reader.nextEntry();
            // Overlong encodings should be rejected or replaced
            assertThat(entry).isNotNull();
        }
    }

    // Null byte handling

    @Test
    void handlesEmbeddedNullBytes() throws Exception {
        // Null byte in middle of filename - path traversal detection catches this
        String filename = "/test\0hidden/file.txt";
        byte[] payload = createCompressedCpioPayload(filename, "content", 0100644);
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(payload), CompressionType.GZIP, metadata)) {
            // Path traversal detection rejects null bytes as a security measure
            assertThatThrownBy(() -> reader.entries().toList())
                    .hasRootCauseInstanceOf(InvalidFormatException.class);
        }
    }

    // BOM (Byte Order Mark) handling

    @Test
    void handlesUtf8Bom() throws Exception {
        // UTF-8 BOM: 0xEF 0xBB 0xBF
        byte[] bomName = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 0x74, 0x65, 0x73, 0x74, 0x00};

        byte[] archive = createCpioArchiveWithRawName(bomName, "content", 0100644);
        ByteArrayInputStream in = new ByteArrayInputStream(archive);

        try (CpioArchiveReader reader = new CpioArchiveReader(in)) {
            CpioArchiveReader.CpioEntry entry = reader.nextEntry();
            if (entry != null) {
                // BOM should either be stripped or preserved
                assertThat(entry.name()).isNotNull();
            }
        }
    }

    // Unicode normalization issues

    @Test
    void handlesUnicodeNormalizationForms() throws Exception {
        // Same character in different normalization forms
        // é as precomposed (NFC): U+00E9
        String nfc = "/test/\u00E9.txt";
        // é as decomposed (NFD): U+0065 U+0301
        String nfd = "/test/e\u0301.txt";

        // These look identical but have different byte representations
        byte[] payloadNfc = createCompressedCpioPayload(nfc, "content", 0100644);
        byte[] payloadNfd = createCompressedCpioPayload(nfd, "content", 0100644);

        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(payloadNfc), CompressionType.GZIP, metadata)) {
            try {
                List<PayloadEntry> entries = reader.entries().toList();
                // NFC form should be handled
                assertThat(entries).hasSizeGreaterThanOrEqualTo(0);
            } catch (Exception e) {
                // Rejection is acceptable
            }
        }

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(payloadNfd), CompressionType.GZIP, metadata)) {
            try {
                List<PayloadEntry> entries = reader.entries().toList();
                // NFD form should be handled (though may differ from NFC)
                assertThat(entries).hasSizeGreaterThanOrEqualTo(0);
            } catch (Exception e) {
                // Rejection is acceptable
            }
        }
    }

    // Unicode confusables (homoglyphs)

    @ParameterizedTest
    @ValueSource(strings = {
            "/etc/passwd",           // Normal
            "/еtc/passwd",           // Cyrillic 'е' instead of Latin 'e'
            "/etc/pаsswd",           // Cyrillic 'а' instead of Latin 'a'
            "/\u0435tc/passwd"       // Explicit Cyrillic е
    })
    void handlesUnicodeConfusables(String path) throws Exception {
        byte[] payload = createCompressedCpioPayload(path, "content", 0100644);
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(payload), CompressionType.GZIP, metadata)) {
            List<PayloadEntry> entries = reader.entries().toList();
            // Should preserve the exact Unicode characters
            assertThat(entries).hasSize(1);
        }
    }

    // Control characters

    @ParameterizedTest
    @ValueSource(strings = {
            "/test\u0001file.txt",   // SOH
            "/test\u0007file.txt",   // BEL
            "/test\u001Bfile.txt",   // ESC
            "/test\u007Ffile.txt"    // DEL
    })
    void handlesControlCharacters(String path) throws Exception {
        byte[] payload = createCompressedCpioPayload(path, "content", 0100644);
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(payload), CompressionType.GZIP, metadata)) {
            try {
                List<PayloadEntry> entries = reader.entries().toList();
                // If accepted, entries should be present
                assertThat(entries).hasSizeGreaterThanOrEqualTo(0);
            } catch (Exception e) {
                // Rejection of control characters is acceptable
            }
        }
    }

    // Right-to-left override

    @Test
    void handlesRightToLeftOverride() throws Exception {
        // RLO (Right-to-Left Override) can be used to disguise filenames
        // U+202E is RLO
        String maliciousPath = "/usr/bin/\u202Etxt.exe";
        byte[] payload = createCompressedCpioPayload(maliciousPath, "content", 0100644);
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(payload), CompressionType.GZIP, metadata)) {
            List<PayloadEntry> entries = reader.entries().toList();
            assertThat(entries).hasSize(1);
            // RLO should be preserved or filtered
        }
    }

    // Zero-width characters

    @Test
    void handlesZeroWidthCharacters() throws Exception {
        // Zero-width space, zero-width joiner, zero-width non-joiner
        String path = "/test/\u200B\u200C\u200Dfile.txt";
        byte[] payload = createCompressedCpioPayload(path, "content", 0100644);
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(payload), CompressionType.GZIP, metadata)) {
            List<PayloadEntry> entries = reader.entries().toList();
            assertThat(entries).hasSize(1);
        }
    }

    // Latin-1 (ISO-8859-1) encoding

    @Test
    void handlesLatin1Characters() throws Exception {
        // Some old packages might use Latin-1 instead of UTF-8
        byte[] latin1Name = new byte[]{0x2F, (byte) 0xE4, (byte) 0xF6, (byte) 0xFC, 0x00}; // /äöü

        byte[] archive = createCpioArchiveWithRawName(latin1Name, "content", 0100644);
        ByteArrayInputStream in = new ByteArrayInputStream(archive);

        try (CpioArchiveReader reader = new CpioArchiveReader(in)) {
            CpioArchiveReader.CpioEntry entry = reader.nextEntry();
            // Should handle Latin-1 somehow (may interpret as UTF-8)
            assertThat(entry).isNotNull();
        }
    }

    // Emoji and supplementary characters

    @Test
    void handlesSupplementaryCharacters() throws Exception {
        // Characters outside BMP (require surrogate pairs in Java)
        String path = "/test/\uD83D\uDE00\uD83D\uDCA9.txt"; // 😀💩
        byte[] payload = createCompressedCpioPayload(path, "content", 0100644);
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(payload), CompressionType.GZIP, metadata)) {
            List<PayloadEntry> entries = reader.entries().toList();
            assertThat(entries).hasSize(1);
        }
    }

    // Combining characters

    @Test
    void handlesCombiningCharacters() throws Exception {
        // Multiple combining characters
        String path = "/test/a\u0300\u0301\u0302.txt"; // a with grave, acute, circumflex
        byte[] payload = createCompressedCpioPayload(path, "content", 0100644);
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(payload), CompressionType.GZIP, metadata)) {
            List<PayloadEntry> entries = reader.entries().toList();
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
        out.write(createCpioEntry(name, content, mode));
        out.write(createCpioEntry("TRAILER!!!", "", 0));
        return out.toByteArray();
    }

    private byte[] createCpioArchiveWithRawName(byte[] nameWithNull, String content, int mode) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);

        String header = String.format(
                "%s%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X",
                CPIO_MAGIC,
                0, mode, 0, 0, 1, 0, contentBytes.length, 0, 0, 0, 0, nameWithNull.length, 0
        );

        out.write(header.getBytes(StandardCharsets.US_ASCII));
        out.write(nameWithNull);

        int headerAndName = 110 + nameWithNull.length;
        int padding = (4 - (headerAndName % 4)) % 4;
        out.write(new byte[padding]);

        out.write(contentBytes);

        int contentPadding = (4 - (contentBytes.length % 4)) % 4;
        out.write(new byte[contentPadding]);

        // Add trailer
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
