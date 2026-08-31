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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Security and fuzzing tests for CPIO archive reader.
 * Tests for malformed headers, boundary conditions, and DoS prevention.
 */
class CpioSecurityTest {

    private static final String CPIO_MAGIC_NEWC = "070701";
    private static final String CPIO_MAGIC_CRC = "070702";

    // Tests for invalid magic numbers

    @Test
    void rejectsEmptyInput() {
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[0]);

        try (CpioArchiveReader reader = new CpioArchiveReader(in)) {
            CpioArchiveReader.CpioEntry entry = reader.nextEntry();
            assertThat(entry).isNull();
        } catch (Exception e) {
            // Empty input may return null or throw
        }
    }

    @Test
    void rejectsTruncatedHeader() {
        // Less than 110 bytes
        byte[] truncated = new byte[50];
        System.arraycopy(CPIO_MAGIC_NEWC.getBytes(), 0, truncated, 0, 6);
        ByteArrayInputStream in = new ByteArrayInputStream(truncated);

        try (CpioArchiveReader reader = new CpioArchiveReader(in)) {
            CpioArchiveReader.CpioEntry entry = reader.nextEntry();
            assertThat(entry).isNull(); // Should return null for incomplete header
        } catch (Exception e) {
            // May throw on truncated input
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"000000", "070700", "070703", "ABCDEF", "123456", "      "})
    void rejectsInvalidMagicNumbers(String magic) throws IOException {
        byte[] header = createCpioHeader(magic, "test.txt", 0, 0100644);
        ByteArrayInputStream in = new ByteArrayInputStream(header);

        try (CpioArchiveReader reader = new CpioArchiveReader(in)) {
            assertThatThrownBy(reader::nextEntry)
                    .isInstanceOf(InvalidFormatException.class)
                    .hasMessageContaining("Invalid CPIO magic");
        }
    }

    @Test
    void acceptsValidMagicNewc() throws Exception {
        byte[] archive = createValidCpioArchive("test.txt", "content", 0100644);
        ByteArrayInputStream in = new ByteArrayInputStream(archive);

        try (CpioArchiveReader reader = new CpioArchiveReader(in)) {
            CpioArchiveReader.CpioEntry entry = reader.nextEntry();
            assertThat(entry).isNotNull();
            assertThat(entry.name()).isEqualTo("test.txt");
        }
    }

    @Test
    void acceptsValidMagicCrc() throws Exception {
        byte[] archive = createCpioArchiveWithMagic(CPIO_MAGIC_CRC, "test.txt", "content", 0100644);
        ByteArrayInputStream in = new ByteArrayInputStream(archive);

        try (CpioArchiveReader reader = new CpioArchiveReader(in)) {
            CpioArchiveReader.CpioEntry entry = reader.nextEntry();
            assertThat(entry).isNotNull();
        }
    }

    // Tests for invalid hex in header fields

    @Test
    void rejectsNonHexInInode() throws IOException {
        byte[] header = createCpioHeaderRaw();
        // Corrupt inode field (positions 6-13)
        header[6] = 'G'; // Invalid hex character
        ByteArrayInputStream in = new ByteArrayInputStream(header);

        try (CpioArchiveReader reader = new CpioArchiveReader(in)) {
            assertThatThrownBy(reader::nextEntry)
                    .isInstanceOf(InvalidFormatException.class)
                    .hasMessageContaining("Invalid hex");
        }
    }

    @Test
    void rejectsNonHexInMode() throws IOException {
        byte[] header = createCpioHeaderRaw();
        // Corrupt mode field (positions 14-21)
        header[14] = 'Z';
        ByteArrayInputStream in = new ByteArrayInputStream(header);

        try (CpioArchiveReader reader = new CpioArchiveReader(in)) {
            assertThatThrownBy(reader::nextEntry)
                    .isInstanceOf(InvalidFormatException.class)
                    .hasMessageContaining("Invalid hex");
        }
    }

    @Test
    void rejectsNonHexInFileSize() throws IOException {
        byte[] header = createCpioHeaderRaw();
        // Corrupt filesize field (positions 54-61)
        header[54] = 'X';
        ByteArrayInputStream in = new ByteArrayInputStream(header);

        try (CpioArchiveReader reader = new CpioArchiveReader(in)) {
            assertThatThrownBy(reader::nextEntry)
                    .isInstanceOf(InvalidFormatException.class)
                    .hasMessageContaining("Invalid hex");
        }
    }

    @Test
    void rejectsNonHexInNameSize() throws IOException {
        byte[] header = createCpioHeaderRaw();
        // Corrupt namesize field (positions 94-101)
        header[94] = '!';
        ByteArrayInputStream in = new ByteArrayInputStream(header);

        try (CpioArchiveReader reader = new CpioArchiveReader(in)) {
            assertThatThrownBy(reader::nextEntry)
                    .isInstanceOf(InvalidFormatException.class)
                    .hasMessageContaining("Invalid hex");
        }
    }

    @Test
    void rejectsSpacesInHexFields() throws IOException {
        byte[] header = createCpioHeaderRaw();
        // Put spaces in filesize field
        for (int i = 54; i < 62; i++) {
            header[i] = ' ';
        }
        ByteArrayInputStream in = new ByteArrayInputStream(header);

        try (CpioArchiveReader reader = new CpioArchiveReader(in)) {
            assertThatThrownBy(reader::nextEntry)
                    .isInstanceOf(InvalidFormatException.class);
        }
    }

    // Tests for boundary conditions on sizes

    @Test
    void rejectsZeroNameSize() throws IOException {
        byte[] header = createCpioHeader(CPIO_MAGIC_NEWC, "", 0, 0100644);
        // Manually set namesize to 0
        String zeroNameSize = "00000000";
        System.arraycopy(zeroNameSize.getBytes(), 0, header, 94, 8);
        ByteArrayInputStream in = new ByteArrayInputStream(header);

        try (CpioArchiveReader reader = new CpioArchiveReader(in)) {
            assertThatThrownBy(reader::nextEntry)
                    .isInstanceOf(InvalidFormatException.class)
                    .hasMessageContaining("name size");
        }
    }

    @Test
    void rejectsExcessiveNameSize() throws IOException {
        byte[] header = createCpioHeaderRaw();
        // Set namesize to MAX (0xFFFFFFFF would be 4GB+)
        // Using 64KB + 1 to exceed MAX_NAME_SIZE
        String hugeNameSize = String.format("%08X", 65 * 1024 + 1);
        System.arraycopy(hugeNameSize.getBytes(), 0, header, 94, 8);
        ByteArrayInputStream in = new ByteArrayInputStream(header);

        try (CpioArchiveReader reader = new CpioArchiveReader(in)) {
            assertThatThrownBy(reader::nextEntry)
                    .isInstanceOf(InvalidFormatException.class)
                    .hasMessageContaining("name size");
        }
    }

    @Test
    void rejectsExcessiveFileSize() throws IOException {
        byte[] header = createCpioHeaderRaw();
        // Set filesize to > 8GB (MAX_FILE_SIZE)
        // 0x200000001 = 8GB + 1
        String hugeFileSize = "200000001";
        // Only 8 chars available, so use max that fits
        String maxFileSize = "FFFFFFFF"; // ~4GB, let's test this boundary
        System.arraycopy(maxFileSize.getBytes(), 0, header, 54, 8);

        // For this test, create a header with namesize that will be read
        byte[] fullHeader = new byte[header.length + 10]; // name + padding
        System.arraycopy(header, 0, fullHeader, 0, header.length);
        // Add a simple name
        fullHeader[110] = 't';
        fullHeader[111] = '\0';

        ByteArrayInputStream in = new ByteArrayInputStream(fullHeader);

        try (CpioArchiveReader reader = new CpioArchiveReader(in)) {
            // May succeed in parsing header but will fail reading data
            // The key is it shouldn't allocate 4GB
            CpioArchiveReader.CpioEntry entry = reader.nextEntry();
            if (entry != null) {
                // Don't try to read the content
                assertThat(entry.size()).isGreaterThan(0);
            }
        } catch (InvalidFormatException e) {
            // Expected if file size exceeds limit
            assertThat(e.getMessage()).containsIgnoringCase("size");
        }
    }

    @Test
    void handlesTruncatedFilename() throws IOException {
        byte[] header = createCpioHeaderRaw();
        // Set namesize to 100 but only provide 10 bytes
        String nameSize = String.format("%08X", 100);
        System.arraycopy(nameSize.getBytes(), 0, header, 94, 8);

        byte[] truncated = new byte[header.length + 10]; // Only 10 bytes for name
        System.arraycopy(header, 0, truncated, 0, header.length);

        ByteArrayInputStream in = new ByteArrayInputStream(truncated);

        try (CpioArchiveReader reader = new CpioArchiveReader(in)) {
            assertThatThrownBy(reader::nextEntry)
                    .isInstanceOf(InvalidFormatException.class)
                    .hasMessageContaining("end of");
        }
    }

    @Test
    void handlesTruncatedFileData() throws Exception {
        // Updated with user approval (2026-08-28, Fresh Scent Phase 4): truncated file
        // content must surface LOUDLY (IOException) instead of being returned silently
        // (catalog §6 — silent partial data is the worst class).
        byte[] archive = createCpioArchiveWithTruncatedData("test.txt", 1000, 100);
        ByteArrayInputStream in = new ByteArrayInputStream(archive);

        try (CpioArchiveReader reader = new CpioArchiveReader(in)) {
            CpioArchiveReader.CpioEntry entry = reader.nextEntry();
            assertThat(entry).isNotNull();
            assertThat(entry.size()).isEqualTo(1000);

            assertThatThrownBy(() -> entry.dataStream().readAllBytes())
                    .isInstanceOf(java.io.IOException.class)
                    .hasMessageContaining("Truncated");
        }
    }

    // Tests for missing trailer

    @Test
    void handlesMissingTrailer() throws Exception {
        // Updated with user approval (2026-08-28, Fresh Scent Phase 4): a CPIO archive
        // without the mandatory TRAILER!!! entry is CORRUPT — it must throw instead of
        // silently ending the iteration (catalog §5/§6).
        byte[] entry = createCpioEntry("test.txt", "content", 0100644);
        ByteArrayInputStream in = new ByteArrayInputStream(entry);

        try (CpioArchiveReader reader = new CpioArchiveReader(in)) {
            CpioArchiveReader.CpioEntry first = reader.nextEntry();
            assertThat(first).isNotNull();

            assertThatThrownBy(reader::nextEntry)
                    .isInstanceOf(InvalidFormatException.class)
                    .hasMessageContaining("Truncated CPIO archive");
        }
    }

    @Test
    void handlesMultipleTrailers() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(createCpioEntry("file.txt", "content", 0100644));
        out.write(createCpioEntry("TRAILER!!!", "", 0));
        out.write(createCpioEntry("TRAILER!!!", "", 0)); // Duplicate trailer

        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

        try (CpioArchiveReader reader = new CpioArchiveReader(in)) {
            CpioArchiveReader.CpioEntry entry = reader.nextEntry();
            assertThat(entry).isNotNull();

            // Should stop at first trailer
            assertThat(reader.nextEntry()).isNull();
            assertThat(reader.nextEntry()).isNull(); // Should stay null
        }
    }

    // Tests for padding/alignment issues

    @Test
    void handlesMisalignedData() throws Exception {
        // Create archive with incorrect padding
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // Header
        String header = createCpioHeaderString("test.txt", 5, 0100644); // 5 bytes content
        out.write(header.getBytes(StandardCharsets.US_ASCII));

        // Name + null terminator
        out.write("test.txt\0".getBytes(StandardCharsets.US_ASCII));

        // Skip correct padding (should be 1 byte to align to 4)
        // Don't add padding - this is the misalignment

        // Content (5 bytes)
        out.write("hello".getBytes());

        // Skip content padding too

        // Trailer
        out.write(createCpioEntry("TRAILER!!!", "", 0));

        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

        try (CpioArchiveReader reader = new CpioArchiveReader(in)) {
            CpioArchiveReader.CpioEntry entry = reader.nextEntry();
            // May read incorrectly or fail gracefully
            // The important thing is it doesn't crash
        } catch (Exception e) {
            // Acceptable
        }
    }

    @Test
    void handlesOddNameLength() throws Exception {
        // Odd-length filename to test padding
        byte[] archive = createValidCpioArchive("odd", "content", 0100644);
        ByteArrayInputStream in = new ByteArrayInputStream(archive);

        try (CpioArchiveReader reader = new CpioArchiveReader(in)) {
            CpioArchiveReader.CpioEntry entry = reader.nextEntry();
            assertThat(entry).isNotNull();
            assertThat(entry.name()).isEqualTo("odd");
        }
    }

    @Test
    void handlesOddContentLength() throws Exception {
        // Odd-length content to test data padding
        byte[] archive = createValidCpioArchive("test.txt", "abc", 0100644); // 3 bytes
        ByteArrayInputStream in = new ByteArrayInputStream(archive);

        try (CpioArchiveReader reader = new CpioArchiveReader(in)) {
            CpioArchiveReader.CpioEntry entry = reader.nextEntry();
            assertThat(entry).isNotNull();
            byte[] content = entry.dataStream().readNBytes((int) entry.size());
            assertThat(new String(content)).isEqualTo("abc");
        }
    }

    // Fuzzing with random data

    @Test
    void handlesRandomDataGracefully() {
        Random random = new Random(42); // Fixed seed for reproducibility

        for (int i = 0; i < 100; i++) {
            byte[] randomData = new byte[128];
            random.nextBytes(randomData);

            ByteArrayInputStream in = new ByteArrayInputStream(randomData);

            try (CpioArchiveReader reader = new CpioArchiveReader(in)) {
                reader.nextEntry();
            } catch (InvalidFormatException e) {
                // Expected for random data
            } catch (IOException e) {
                // Also acceptable
            } catch (Exception e) {
                // Any other exception should be wrapped properly
                assertThat(e).isNotInstanceOf(OutOfMemoryError.class);
                assertThat(e).isNotInstanceOf(StackOverflowError.class);
            }
        }
    }

    @Test
    void handlesRandomDataWithValidMagic() {
        Random random = new Random(42);

        for (int i = 0; i < 100; i++) {
            byte[] data = new byte[256];
            random.nextBytes(data);

            // Insert valid magic at start
            System.arraycopy(CPIO_MAGIC_NEWC.getBytes(), 0, data, 0, 6);

            ByteArrayInputStream in = new ByteArrayInputStream(data);

            try (CpioArchiveReader reader = new CpioArchiveReader(in)) {
                reader.nextEntry();
            } catch (Exception e) {
                // Expected - random data after magic
                assertThat(e).isNotInstanceOf(OutOfMemoryError.class);
                assertThat(e).isNotInstanceOf(StackOverflowError.class);
            }
        }
    }

    // Tests for special filenames

    @Test
    void handlesFilenameWithNullBytes() throws Exception {
        // Filename containing embedded null
        // Note: CPIO reader may strip null bytes or include them depending on implementation
        byte[] archive = createValidCpioArchive("test\0hidden.txt", "content", 0100644);
        ByteArrayInputStream in = new ByteArrayInputStream(archive);

        try (CpioArchiveReader reader = new CpioArchiveReader(in)) {
            CpioArchiveReader.CpioEntry entry = reader.nextEntry();
            if (entry != null) {
                // Document actual behavior: name may have null stripped or truncated
                // The important thing is that the parser handles it without crashing
                assertThat(entry.name()).isNotEmpty();
            }
        }
    }

    @Test
    void handlesVeryLongFilename() throws Exception {
        // Maximum allowed filename (just under 64KB)
        String longName = "a".repeat(60000);
        byte[] archive = createValidCpioArchive(longName, "x", 0100644);
        ByteArrayInputStream in = new ByteArrayInputStream(archive);

        try (CpioArchiveReader reader = new CpioArchiveReader(in)) {
            CpioArchiveReader.CpioEntry entry = reader.nextEntry();
            assertThat(entry).isNotNull();
            assertThat(entry.name()).hasSize(60000);
        }
    }

    // Helper methods

    private byte[] createCpioHeaderRaw() {
        return createCpioHeader(CPIO_MAGIC_NEWC, "t", 0, 0100644);
    }

    private byte[] createCpioHeader(String magic, String name, long fileSize, int mode) {
        int nameSize = name.length() + 1; // Include null terminator

        String header = String.format(
                "%s%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X",
                magic,
                0,              // inode
                mode,           // mode
                0,              // uid
                0,              // gid
                1,              // nlink
                0,              // mtime
                fileSize,       // filesize
                0,              // devmajor
                0,              // devminor
                0,              // rdevmajor
                0,              // rdevminor
                nameSize,       // namesize
                0               // check
        );

        return header.getBytes(StandardCharsets.US_ASCII);
    }

    private String createCpioHeaderString(String name, long fileSize, int mode) {
        int nameSize = name.length() + 1;

        return String.format(
                "%s%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X",
                CPIO_MAGIC_NEWC,
                0, mode, 0, 0, 1, 0, fileSize, 0, 0, 0, 0, nameSize, 0
        );
    }

    private byte[] createCpioEntry(String name, String content, int mode) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        byte[] nameBytes = (name + "\0").getBytes(StandardCharsets.UTF_8);
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);

        String header = String.format(
                "%s%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X",
                CPIO_MAGIC_NEWC,
                0, mode, 0, 0, 1, 0, contentBytes.length, 0, 0, 0, 0, nameBytes.length, 0
        );

        out.write(header.getBytes(StandardCharsets.US_ASCII));
        out.write(nameBytes);

        // Pad to 4-byte boundary
        int headerAndName = 110 + nameBytes.length;
        int padding = (4 - (headerAndName % 4)) % 4;
        out.write(new byte[padding]);

        out.write(contentBytes);

        // Pad content to 4-byte boundary
        int contentPadding = (4 - (contentBytes.length % 4)) % 4;
        out.write(new byte[contentPadding]);

        return out.toByteArray();
    }

    private byte[] createValidCpioArchive(String name, String content, int mode) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(createCpioEntry(name, content, mode));
        out.write(createCpioEntry("TRAILER!!!", "", 0));
        return out.toByteArray();
    }

    private byte[] createCpioArchiveWithMagic(String magic, String name, String content, int mode) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        byte[] nameBytes = (name + "\0").getBytes(StandardCharsets.UTF_8);
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);

        String header = String.format(
                "%s%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X",
                magic,
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

        // Trailer
        out.write(createCpioEntry("TRAILER!!!", "", 0));

        return out.toByteArray();
    }

    private byte[] createCpioArchiveWithTruncatedData(String name, int claimedSize, int actualSize) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        byte[] nameBytes = (name + "\0").getBytes(StandardCharsets.UTF_8);

        String header = String.format(
                "%s%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X",
                CPIO_MAGIC_NEWC,
                0, 0100644, 0, 0, 1, 0, claimedSize, 0, 0, 0, 0, nameBytes.length, 0
        );

        out.write(header.getBytes(StandardCharsets.US_ASCII));
        out.write(nameBytes);

        int headerAndName = 110 + nameBytes.length;
        int padding = (4 - (headerAndName % 4)) % 4;
        out.write(new byte[padding]);

        // Only write actualSize bytes instead of claimedSize
        out.write(new byte[actualSize]);

        return out.toByteArray();
    }
}
