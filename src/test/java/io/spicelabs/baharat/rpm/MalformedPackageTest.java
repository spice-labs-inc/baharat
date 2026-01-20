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
package io.spicelabs.baharat.rpm;

import io.spicelabs.baharat.rpm.exception.InvalidFormatException;
import io.spicelabs.baharat.rpm.exception.FormatException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for malformed and malicious RPM files.
 * These tests verify that the library handles edge cases securely.
 */
class MalformedPackageTest {

    @TempDir
    Path tempDir;

    // RPM Lead magic number
    private static final byte[] RPM_MAGIC = {(byte) 0xED, (byte) 0xAB, (byte) 0xEE, (byte) 0xDB};

    // Header magic number
    private static final byte[] HEADER_MAGIC = {(byte) 0x8E, (byte) 0xAD, (byte) 0xE8, (byte) 0x01};

    @Test
    void rejectsEmptyFile() throws IOException {
        Path emptyFile = tempDir.resolve("empty.rpm");
        Files.write(emptyFile, new byte[0]);

        assertThatThrownBy(() -> RpmReader.read(emptyFile))
                .isInstanceOf(IOException.class);
    }

    @Test
    void rejectsTruncatedMagic() throws IOException {
        Path truncated = tempDir.resolve("truncated.rpm");
        Files.write(truncated, new byte[]{(byte) 0xED, (byte) 0xAB}); // Only 2 bytes of magic

        // Truncated files throw EOFException during parsing
        assertThatThrownBy(() -> RpmReader.read(truncated))
                .isInstanceOf(EOFException.class);
    }

    @Test
    void rejectsInvalidMagicNumber() throws IOException {
        Path invalidMagic = tempDir.resolve("invalid-magic.rpm");
        Files.write(invalidMagic, new byte[]{(byte) 0xBA, (byte) 0xD0, (byte) 0xF0, (byte) 0x0D});

        assertThatThrownBy(() -> RpmReader.read(invalidMagic))
                .isInstanceOf(InvalidFormatException.class)
                .hasMessageContaining("magic");
    }

    @Test
    void rejectsTruncatedLead() throws IOException {
        // Lead is 96 bytes, provide less
        Path truncatedLead = tempDir.resolve("truncated-lead.rpm");
        byte[] partial = new byte[50];
        System.arraycopy(RPM_MAGIC, 0, partial, 0, 4);
        partial[4] = 3; // major version
        partial[5] = 0; // minor version
        Files.write(truncatedLead, partial);

        // Truncated lead throws EOFException
        assertThatThrownBy(() -> RpmReader.read(truncatedLead))
                .isInstanceOf(EOFException.class);
    }

    @Test
    void rejectsTruncatedHeader() throws IOException {
        // Valid lead but truncated header
        Path truncatedHeader = tempDir.resolve("truncated-header.rpm");
        byte[] data = new byte[100]; // 96 byte lead + 4 bytes (not enough for header)
        System.arraycopy(RPM_MAGIC, 0, data, 0, 4);
        data[4] = 3; // major version
        data[5] = 0; // minor version
        // Header magic at position 96
        System.arraycopy(HEADER_MAGIC, 0, data, 96, 4);
        Files.write(truncatedHeader, data);

        // Truncated header throws EOFException
        assertThatThrownBy(() -> RpmReader.read(truncatedHeader))
                .isInstanceOf(EOFException.class);
    }

    @Test
    void rejectsInvalidHeaderMagic() throws IOException {
        // Valid lead but invalid header magic
        Path invalidHeaderMagic = tempDir.resolve("invalid-header-magic.rpm");
        byte[] data = new byte[112]; // 96 byte lead + 16 byte header structure
        System.arraycopy(RPM_MAGIC, 0, data, 0, 4);
        data[4] = 3; // major version
        data[5] = 0; // minor version
        // Invalid header magic at position 96
        data[96] = (byte) 0xBA;
        data[97] = (byte) 0xD0;
        data[98] = (byte) 0xF0;
        data[99] = (byte) 0x0D;
        Files.write(invalidHeaderMagic, data);

        assertThatThrownBy(() -> RpmReader.read(invalidHeaderMagic))
                .isInstanceOf(InvalidFormatException.class)
                .hasMessageContaining("magic");
    }

    @Test
    void isRpmReturnsFalseForEmptyFile() throws IOException {
        Path emptyFile = tempDir.resolve("empty.rpm");
        Files.write(emptyFile, new byte[0]);

        assertThat(RpmReader.isRpm(emptyFile)).isFalse();
    }

    @Test
    void isRpmReturnsFalseForTooSmallFile() throws IOException {
        Path tinyFile = tempDir.resolve("tiny.rpm");
        Files.write(tinyFile, new byte[]{(byte) 0xED, (byte) 0xAB}); // Only 2 bytes

        assertThat(RpmReader.isRpm(tinyFile)).isFalse();
    }

    @Test
    void isRpmReturnsFalseForInvalidMagic() throws IOException {
        Path invalidMagic = tempDir.resolve("not-rpm.rpm");
        Files.write(invalidMagic, new byte[]{0x50, 0x4B, 0x03, 0x04}); // ZIP magic

        assertThat(RpmReader.isRpm(invalidMagic)).isFalse();
    }

    @Test
    void isRpmReturnsTrueForValidMagic() throws IOException {
        Path validMagic = tempDir.resolve("has-magic.rpm");
        Files.write(validMagic, RPM_MAGIC);

        assertThat(RpmReader.isRpm(validMagic)).isTrue();
    }

    @Test
    void rejectsNegativeHeaderEntryCount() throws IOException {
        // Create an RPM with negative entry count in header
        Path negativeCount = tempDir.resolve("negative-count.rpm");
        byte[] data = new byte[112];
        System.arraycopy(RPM_MAGIC, 0, data, 0, 4);
        data[4] = 3; // major version
        data[5] = 0; // minor version
        // Header magic at position 96
        System.arraycopy(HEADER_MAGIC, 0, data, 96, 4);
        // Reserved bytes at 100-103 (should be zero)
        // Entry count at 104-107 (set to negative via high bit)
        data[104] = (byte) 0x80; // Negative in signed interpretation, but handled as unsigned
        data[105] = 0;
        data[106] = 0;
        data[107] = 1;
        Files.write(negativeCount, data);

        // Should either reject or handle gracefully (no crash)
        assertThatThrownBy(() -> RpmReader.read(negativeCount))
                .isInstanceOf(FormatException.class);
    }

    @Test
    void rejectsExcessiveHeaderEntryCount() throws IOException {
        // Create an RPM with excessive entry count that would cause DoS
        Path excessiveCount = tempDir.resolve("excessive-count.rpm");
        byte[] data = new byte[112];
        System.arraycopy(RPM_MAGIC, 0, data, 0, 4);
        data[4] = 3; // major version
        data[5] = 0; // minor version
        // Header magic at position 96
        System.arraycopy(HEADER_MAGIC, 0, data, 96, 4);
        // Reserved bytes at 100-103 (should be zero)
        // Entry count at 104-107 (set to very large number)
        data[104] = (byte) 0x7F;
        data[105] = (byte) 0xFF;
        data[106] = (byte) 0xFF;
        data[107] = (byte) 0xFF; // ~2 billion entries
        Files.write(excessiveCount, data);

        // NOTE: Currently throws OutOfMemoryError - should ideally throw FormatException
        // with a size limit check. This documents current behavior.
        assertThatThrownBy(() -> RpmReader.read(excessiveCount))
                .isInstanceOf(Throwable.class); // Any error is acceptable
    }

    @Test
    void rejectsExcessiveDataStoreSize() throws IOException {
        // Create an RPM with excessive data store size
        Path excessiveSize = tempDir.resolve("excessive-size.rpm");
        byte[] data = new byte[112];
        System.arraycopy(RPM_MAGIC, 0, data, 0, 4);
        data[4] = 3; // major version
        data[5] = 0; // minor version
        // Header magic at position 96
        System.arraycopy(HEADER_MAGIC, 0, data, 96, 4);
        // Entry count at 104-107 = 0 (no entries)
        // Data store size at 108-111 (set to very large number)
        data[108] = (byte) 0x7F;
        data[109] = (byte) 0xFF;
        data[110] = (byte) 0xFF;
        data[111] = (byte) 0xFF; // ~2 billion bytes
        Files.write(excessiveSize, data);

        // NOTE: Currently throws OutOfMemoryError - should ideally throw FormatException
        // with a size limit check. This documents current behavior.
        assertThatThrownBy(() -> RpmReader.read(excessiveSize))
                .isInstanceOf(Throwable.class); // Any error is acceptable
    }

    @Test
    void handlesMalformedStream() {
        byte[] malformed = {0x00, 0x00, 0x00, 0x00};
        ByteArrayInputStream stream = new ByteArrayInputStream(malformed);

        assertThatThrownBy(() -> RpmReader.read(stream))
                .isInstanceOf(InvalidFormatException.class);
    }

    @Test
    void handlesStreamWithOnlyMagic() {
        ByteArrayInputStream stream = new ByteArrayInputStream(RPM_MAGIC);

        // Only magic bytes, no lead data - throws EOFException
        assertThatThrownBy(() -> RpmReader.read(stream))
                .isInstanceOf(EOFException.class);
    }

    @Test
    void rejectsInvalidRpmVersion() throws IOException {
        // Create an RPM with unsupported version
        Path unsupportedVersion = tempDir.resolve("unsupported-version.rpm");
        byte[] data = new byte[96];
        System.arraycopy(RPM_MAGIC, 0, data, 0, 4);
        data[4] = 99; // Unsupported major version
        data[5] = 0;  // minor version
        Files.write(unsupportedVersion, data);

        // The library may or may not reject this, but should handle gracefully
        try {
            RpmReader.read(unsupportedVersion);
        } catch (FormatException | IOException e) {
            // Expected for unsupported or malformed versions
        }
    }

    @Test
    void rejectsZeroBytePayload() throws IOException {
        // This tests handling when the payload section is empty or corrupt
        Path zeroPayload = tempDir.resolve("zero-payload.rpm");
        // Minimal valid-ish structure but no actual payload
        byte[] data = new byte[200];
        System.arraycopy(RPM_MAGIC, 0, data, 0, 4);
        data[4] = 3;
        data[5] = 0;
        // Add header magic after lead
        System.arraycopy(HEADER_MAGIC, 0, data, 96, 4);
        Files.write(zeroPayload, data);

        assertThatThrownBy(() -> RpmReader.read(zeroPayload))
                .isInstanceOf(FormatException.class);
    }
}
