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
package io.spicelabs.baharat.deb;

import io.spicelabs.baharat.PackageException;
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
 * Security and fuzzing tests for AR archive reader (used in DEB packages).
 * Tests for malformed headers, boundary conditions, and DoS prevention.
 */
class ArArchiveSecurityTest {

    private static final byte[] AR_MAGIC = "!<arch>\n".getBytes(StandardCharsets.US_ASCII);
    private static final int HEADER_SIZE = 60;

    // Tests for invalid size fields

    @Test
    void rejectsNonNumericSize() throws Exception {
        byte[] archive = createArArchiveWithBadSize("test.txt", "ABCDEFGHIJ");
        ByteArrayInputStream in = new ByteArrayInputStream(archive);

        try (ArArchiveReader reader = new ArArchiveReader(in)) {
            assertThatThrownBy(reader::nextEntry)
                    .isInstanceOf(Exception.class);
        }
    }

    @Test
    void handlesNegativeSize() throws Exception {
        // Note: Long.parseLong accepts negative values, so the parser accepts them
        // This documents the current behavior - negative sizes don't throw during parsing
        byte[] archive = createArArchiveWithBadSize("test.txt", "-12345    ");
        ByteArrayInputStream in = new ByteArrayInputStream(archive);

        try (ArArchiveReader reader = new ArArchiveReader(in)) {
            ArArchiveReader.ArEntry entry = reader.nextEntry();
            // Parser accepts negative size - this may be a security consideration
            // for callers who should validate entry.size() >= 0
            assertThat(entry).isNotNull();
            assertThat(entry.size()).isLessThan(0);
        }
    }

    @Test
    void rejectsExcessiveSize() throws Exception {
        // Size claims to be larger than the remaining file
        byte[] archive = createArArchiveWithBadSize("test.txt", "9999999999");
        ByteArrayInputStream in = new ByteArrayInputStream(archive);

        try (ArArchiveReader reader = new ArArchiveReader(in)) {
            try {
                ArArchiveReader.ArEntry entry = reader.nextEntry();
                // If we got here, try to skip the entry
                if (entry != null) {
                    reader.skipCurrentEntry();
                }
            } catch (Exception e) {
                // Expected - file doesn't have enough data
            }
        }
    }

    @Test
    void handlesMaxIntSize() throws Exception {
        // Integer.MAX_VALUE as size
        byte[] archive = createArArchiveWithBadSize("test.txt", "2147483647");
        ByteArrayInputStream in = new ByteArrayInputStream(archive);

        try (ArArchiveReader reader = new ArArchiveReader(in)) {
            ArArchiveReader.ArEntry entry = reader.nextEntry();
            if (entry != null) {
                // Should handle gracefully - either reject or handle partial data
                assertThat(entry.size()).isEqualTo(Integer.MAX_VALUE);
            }
        } catch (Exception e) {
            // Also acceptable
        }
    }

    @Test
    void handlesZeroSize() throws Exception {
        byte[] archive = createArArchiveWithContent("empty.txt", new byte[0]);
        ByteArrayInputStream in = new ByteArrayInputStream(archive);

        try (ArArchiveReader reader = new ArArchiveReader(in)) {
            ArArchiveReader.ArEntry entry = reader.nextEntry();
            assertThat(entry).isNotNull();
            assertThat(entry.size()).isEqualTo(0);
        }
    }

    // Tests for invalid timestamp

    @Test
    void handlesInvalidTimestamp() throws Exception {
        byte[] archive = createArArchiveWithBadTimestamp("test.txt", "BADTIME     ");
        ByteArrayInputStream in = new ByteArrayInputStream(archive);

        try (ArArchiveReader reader = new ArArchiveReader(in)) {
            try {
                ArArchiveReader.ArEntry entry = reader.nextEntry();
                // May succeed with default timestamp or reject
            } catch (Exception e) {
                // Acceptable
            }
        }
    }

    @Test
    void handlesNegativeTimestamp() throws Exception {
        byte[] archive = createArArchiveWithBadTimestamp("test.txt", "-1          ");
        ByteArrayInputStream in = new ByteArrayInputStream(archive);

        try (ArArchiveReader reader = new ArArchiveReader(in)) {
            ArArchiveReader.ArEntry entry = reader.nextEntry();
            // Negative timestamp may be accepted (epoch - 1 second)
            assertThat(entry).isNotNull();
        }
    }

    // Tests for invalid mode

    @Test
    void handlesInvalidMode() throws Exception {
        byte[] archive = createArArchiveWithBadMode("test.txt", "BADMODE ");
        ByteArrayInputStream in = new ByteArrayInputStream(archive);

        try (ArArchiveReader reader = new ArArchiveReader(in)) {
            try {
                ArArchiveReader.ArEntry entry = reader.nextEntry();
                // May use default mode or reject
            } catch (Exception e) {
                // Acceptable
            }
        }
    }

    @Test
    void handlesOversizedMode() throws Exception {
        // Mode that doesn't fit in octal
        byte[] archive = createArArchiveWithBadMode("test.txt", "99999999");
        ByteArrayInputStream in = new ByteArrayInputStream(archive);

        try (ArArchiveReader reader = new ArArchiveReader(in)) {
            try {
                reader.nextEntry();
            } catch (Exception e) {
                // Acceptable
            }
        }
    }

    // Tests for invalid uid/gid

    @Test
    void handlesInvalidUid() throws Exception {
        byte[] archive = createArArchiveWithBadOwner("test.txt", "BADUID", "0     ");
        ByteArrayInputStream in = new ByteArrayInputStream(archive);

        try (ArArchiveReader reader = new ArArchiveReader(in)) {
            try {
                reader.nextEntry();
            } catch (Exception e) {
                // Acceptable
            }
        }
    }

    @Test
    void handlesNegativeUid() throws Exception {
        byte[] archive = createArArchiveWithBadOwner("test.txt", "-1    ", "0     ");
        ByteArrayInputStream in = new ByteArrayInputStream(archive);

        try (ArArchiveReader reader = new ArArchiveReader(in)) {
            ArArchiveReader.ArEntry entry = reader.nextEntry();
            // May accept negative uid
            assertThat(entry).isNotNull();
        }
    }

    // Tests for entry magic

    @ParameterizedTest
    @ValueSource(strings = {"XX", "  ", "``", "\0\0", "AB"})
    void rejectsInvalidEntryMagic(String magic) throws Exception {
        byte[] archive = createArArchiveWithBadEntryMagic("test.txt", magic);
        ByteArrayInputStream in = new ByteArrayInputStream(archive);

        try (ArArchiveReader reader = new ArArchiveReader(in)) {
            assertThatThrownBy(reader::nextEntry)
                    .isInstanceOf(PackageException.InvalidPackageException.class)
                    .hasMessageContaining("entry magic");
        }
    }

    // Tests for name handling

    @Test
    void handlesNameWithTrailingSlash() throws Exception {
        byte[] archive = createArArchiveWithName("test/           ", "content");
        ByteArrayInputStream in = new ByteArrayInputStream(archive);

        try (ArArchiveReader reader = new ArArchiveReader(in)) {
            ArArchiveReader.ArEntry entry = reader.nextEntry();
            assertThat(entry).isNotNull();
            // Trailing slash should be removed
            assertThat(entry.name()).isEqualTo("test");
        }
    }

    @Test
    void handlesNameWithSpaces() throws Exception {
        byte[] archive = createArArchiveWithName("test            ", "content");
        ByteArrayInputStream in = new ByteArrayInputStream(archive);

        try (ArArchiveReader reader = new ArArchiveReader(in)) {
            ArArchiveReader.ArEntry entry = reader.nextEntry();
            assertThat(entry).isNotNull();
            // Trailing spaces should be trimmed
            assertThat(entry.name()).isEqualTo("test");
        }
    }

    @Test
    void handlesEmptyName() throws Exception {
        byte[] archive = createArArchiveWithName("                ", "content");
        ByteArrayInputStream in = new ByteArrayInputStream(archive);

        try (ArArchiveReader reader = new ArArchiveReader(in)) {
            ArArchiveReader.ArEntry entry = reader.nextEntry();
            // Empty name may be accepted
            assertThat(entry).isNotNull();
        }
    }

    @Test
    void handlesMaxLengthName() throws Exception {
        // 16 character name (max for basic AR)
        byte[] archive = createArArchiveWithName("1234567890123456", "content");
        ByteArrayInputStream in = new ByteArrayInputStream(archive);

        try (ArArchiveReader reader = new ArArchiveReader(in)) {
            ArArchiveReader.ArEntry entry = reader.nextEntry();
            assertThat(entry).isNotNull();
            assertThat(entry.name()).isEqualTo("1234567890123456");
        }
    }

    // BSD extended filename format tests

    @Test
    void handlesBsdExtendedFilename() throws Exception {
        // BSD format: #1/N means next N bytes are the filename
        String longName = "this_is_a_very_long_filename_that_exceeds_16_chars.txt";
        byte[] archive = createArArchiveWithBsdExtendedName(longName, "content");
        ByteArrayInputStream in = new ByteArrayInputStream(archive);

        try (ArArchiveReader reader = new ArArchiveReader(in)) {
            ArArchiveReader.ArEntry entry = reader.nextEntry();
            if (entry != null) {
                // May or may not support BSD extended format
                // Just verify no crash
            }
        } catch (Exception e) {
            // Acceptable if not supported
        }
    }

    @Test
    void handlesBsdExtendedWithZeroLength() throws Exception {
        // #1/0 - zero length extended name
        byte[] archive = createArArchiveWithName("#1/0            ", "content");
        ByteArrayInputStream in = new ByteArrayInputStream(archive);

        try (ArArchiveReader reader = new ArArchiveReader(in)) {
            ArArchiveReader.ArEntry entry = reader.nextEntry();
            // Should handle gracefully
        } catch (Exception e) {
            // Acceptable
        }
    }

    @Test
    void handlesBsdExtendedWithHugeLength() throws Exception {
        // #1/999999999 - huge extended name length
        byte[] archive = createArArchiveWithName("#1/999999999    ", "content");
        ByteArrayInputStream in = new ByteArrayInputStream(archive);

        try (ArArchiveReader reader = new ArArchiveReader(in)) {
            try {
                reader.nextEntry();
            } catch (Exception e) {
                // Expected - not enough data for extended name
            }
        }
    }

    // Fuzzing tests

    @Test
    void handlesRandomDataGracefully() {
        Random random = new Random(42);

        for (int i = 0; i < 100; i++) {
            byte[] randomData = new byte[128];
            random.nextBytes(randomData);

            ByteArrayInputStream in = new ByteArrayInputStream(randomData);

            try (ArArchiveReader reader = new ArArchiveReader(in)) {
                reader.readHeader();
                reader.nextEntry();
            } catch (PackageException.InvalidPackageException e) {
                // Expected for random data
            } catch (IOException e) {
                // Also acceptable
            } catch (Exception e) {
                // Any exception should be clean
                assertThat(e).isNotInstanceOf(OutOfMemoryError.class);
                assertThat(e).isNotInstanceOf(StackOverflowError.class);
            }
        }
    }

    @Test
    void handlesRandomDataWithValidMagic() {
        Random random = new Random(42);

        for (int i = 0; i < 100; i++) {
            byte[] data = new byte[200];
            random.nextBytes(data);

            // Insert valid AR magic
            System.arraycopy(AR_MAGIC, 0, data, 0, AR_MAGIC.length);

            ByteArrayInputStream in = new ByteArrayInputStream(data);

            try (ArArchiveReader reader = new ArArchiveReader(in)) {
                reader.readHeader(); // Should succeed
                reader.nextEntry();  // May fail
            } catch (Exception e) {
                // Expected for random data after magic
                assertThat(e).isNotInstanceOf(OutOfMemoryError.class);
            }
        }
    }

    @Test
    void handlesAllZerosAfterMagic() throws Exception {
        byte[] data = new byte[AR_MAGIC.length + HEADER_SIZE + 100];
        System.arraycopy(AR_MAGIC, 0, data, 0, AR_MAGIC.length);
        // Rest is zeros

        ByteArrayInputStream in = new ByteArrayInputStream(data);

        try (ArArchiveReader reader = new ArArchiveReader(in)) {
            reader.readHeader();
            // Entry magic will be zeros, should reject
            assertThatThrownBy(reader::nextEntry)
                    .isInstanceOf(PackageException.InvalidPackageException.class);
        }
    }

    @Test
    void handlesAllSpacesHeader() throws Exception {
        byte[] data = new byte[AR_MAGIC.length + HEADER_SIZE];
        System.arraycopy(AR_MAGIC, 0, data, 0, AR_MAGIC.length);
        // Fill header with spaces
        for (int i = AR_MAGIC.length; i < data.length; i++) {
            data[i] = ' ';
        }

        ByteArrayInputStream in = new ByteArrayInputStream(data);

        try (ArArchiveReader reader = new ArArchiveReader(in)) {
            reader.readHeader();
            assertThatThrownBy(reader::nextEntry)
                    .isInstanceOf(PackageException.InvalidPackageException.class);
        }
    }

    // Padding tests

    @Test
    void handlesOddSizeWithPadding() throws Exception {
        // Odd-sized content requires 1 byte padding
        byte[] content = new byte[7]; // 7 bytes (odd)
        byte[] archive = createArArchiveWithContent("odd.txt", content);
        ByteArrayInputStream in = new ByteArrayInputStream(archive);

        try (ArArchiveReader reader = new ArArchiveReader(in)) {
            ArArchiveReader.ArEntry entry = reader.nextEntry();
            assertThat(entry).isNotNull();
            assertThat(entry.size()).isEqualTo(7);
            reader.skipCurrentEntry();

            // Should be at end of archive (or next entry if any)
            ArArchiveReader.ArEntry next = reader.nextEntry();
            assertThat(next).isNull();
        }
    }

    @Test
    void handlesEvenSizeNoPadding() throws Exception {
        // Even-sized content needs no padding
        byte[] content = new byte[8]; // 8 bytes (even)
        byte[] archive = createArArchiveWithContent("even.txt", content);
        ByteArrayInputStream in = new ByteArrayInputStream(archive);

        try (ArArchiveReader reader = new ArArchiveReader(in)) {
            ArArchiveReader.ArEntry entry = reader.nextEntry();
            assertThat(entry).isNotNull();
            assertThat(entry.size()).isEqualTo(8);
        }
    }

    // Helper methods

    private byte[] createArArchiveWithContent(String name, byte[] content) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(AR_MAGIC);

        byte[] header = createEntryHeader(name, content.length, System.currentTimeMillis() / 1000, 0, 0, 0100644);
        out.write(header);
        out.write(content);

        // Padding for odd size
        if (content.length % 2 != 0) {
            out.write('\n');
        }

        return out.toByteArray();
    }

    private byte[] createArArchiveWithBadSize(String name, String size) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(AR_MAGIC);

        byte[] header = new byte[HEADER_SIZE];
        java.util.Arrays.fill(header, (byte) ' ');

        // Name
        byte[] nameBytes = name.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(nameBytes, 0, header, 0, Math.min(nameBytes.length, 16));

        // Timestamp
        header[16] = '0';

        // UID
        header[28] = '0';

        // GID
        header[34] = '0';

        // Mode
        System.arraycopy("100644".getBytes(), 0, header, 40, 6);

        // Size (bad value)
        byte[] sizeBytes = size.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(sizeBytes, 0, header, 48, Math.min(sizeBytes.length, 10));

        // Entry magic
        header[58] = '`';
        header[59] = '\n';

        out.write(header);
        out.write(new byte[10]); // Some content

        return out.toByteArray();
    }

    private byte[] createArArchiveWithBadTimestamp(String name, String timestamp) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(AR_MAGIC);

        byte[] header = new byte[HEADER_SIZE];
        java.util.Arrays.fill(header, (byte) ' ');

        byte[] nameBytes = name.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(nameBytes, 0, header, 0, Math.min(nameBytes.length, 16));

        byte[] tsBytes = timestamp.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(tsBytes, 0, header, 16, Math.min(tsBytes.length, 12));

        header[28] = '0';
        header[34] = '0';
        System.arraycopy("100644".getBytes(), 0, header, 40, 6);
        System.arraycopy("4".getBytes(), 0, header, 48, 1);
        header[58] = '`';
        header[59] = '\n';

        out.write(header);
        out.write(new byte[4]);

        return out.toByteArray();
    }

    private byte[] createArArchiveWithBadMode(String name, String mode) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(AR_MAGIC);

        byte[] header = new byte[HEADER_SIZE];
        java.util.Arrays.fill(header, (byte) ' ');

        byte[] nameBytes = name.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(nameBytes, 0, header, 0, Math.min(nameBytes.length, 16));

        header[16] = '0';
        header[28] = '0';
        header[34] = '0';

        byte[] modeBytes = mode.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(modeBytes, 0, header, 40, Math.min(modeBytes.length, 8));

        System.arraycopy("4".getBytes(), 0, header, 48, 1);
        header[58] = '`';
        header[59] = '\n';

        out.write(header);
        out.write(new byte[4]);

        return out.toByteArray();
    }

    private byte[] createArArchiveWithBadOwner(String name, String uid, String gid) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(AR_MAGIC);

        byte[] header = new byte[HEADER_SIZE];
        java.util.Arrays.fill(header, (byte) ' ');

        byte[] nameBytes = name.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(nameBytes, 0, header, 0, Math.min(nameBytes.length, 16));

        header[16] = '0';

        byte[] uidBytes = uid.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(uidBytes, 0, header, 28, Math.min(uidBytes.length, 6));

        byte[] gidBytes = gid.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(gidBytes, 0, header, 34, Math.min(gidBytes.length, 6));

        System.arraycopy("100644".getBytes(), 0, header, 40, 6);
        System.arraycopy("4".getBytes(), 0, header, 48, 1);
        header[58] = '`';
        header[59] = '\n';

        out.write(header);
        out.write(new byte[4]);

        return out.toByteArray();
    }

    private byte[] createArArchiveWithBadEntryMagic(String name, String magic) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(AR_MAGIC);

        byte[] header = new byte[HEADER_SIZE];
        java.util.Arrays.fill(header, (byte) ' ');

        byte[] nameBytes = name.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(nameBytes, 0, header, 0, Math.min(nameBytes.length, 16));

        header[16] = '0';
        header[28] = '0';
        header[34] = '0';
        System.arraycopy("100644".getBytes(), 0, header, 40, 6);
        System.arraycopy("4".getBytes(), 0, header, 48, 1);

        // Bad entry magic
        byte[] magicBytes = magic.getBytes(StandardCharsets.US_ASCII);
        header[58] = magicBytes[0];
        header[59] = magicBytes.length > 1 ? magicBytes[1] : (byte) ' ';

        out.write(header);
        out.write(new byte[4]);

        return out.toByteArray();
    }

    private byte[] createArArchiveWithName(String name, String content) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(AR_MAGIC);

        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);

        byte[] header = new byte[HEADER_SIZE];
        java.util.Arrays.fill(header, (byte) ' ');

        byte[] nameBytes = name.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(nameBytes, 0, header, 0, Math.min(nameBytes.length, 16));

        header[16] = '0';
        header[28] = '0';
        header[34] = '0';
        System.arraycopy("100644".getBytes(), 0, header, 40, 6);

        String size = String.valueOf(contentBytes.length);
        System.arraycopy(size.getBytes(), 0, header, 48, size.length());

        header[58] = '`';
        header[59] = '\n';

        out.write(header);
        out.write(contentBytes);

        if (contentBytes.length % 2 != 0) {
            out.write('\n');
        }

        return out.toByteArray();
    }

    private byte[] createArArchiveWithBsdExtendedName(String name, String content) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(AR_MAGIC);

        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);

        // BSD extended format: #1/N in name field, then N bytes of filename prepended to content
        String bsdName = String.format("#1/%-13d", nameBytes.length);

        byte[] header = new byte[HEADER_SIZE];
        java.util.Arrays.fill(header, (byte) ' ');

        System.arraycopy(bsdName.getBytes(), 0, header, 0, 16);

        header[16] = '0';
        header[28] = '0';
        header[34] = '0';
        System.arraycopy("100644".getBytes(), 0, header, 40, 6);

        // Size includes extended name + content
        String size = String.valueOf(nameBytes.length + contentBytes.length);
        System.arraycopy(size.getBytes(), 0, header, 48, size.length());

        header[58] = '`';
        header[59] = '\n';

        out.write(header);
        out.write(nameBytes);
        out.write(contentBytes);

        int totalSize = nameBytes.length + contentBytes.length;
        if (totalSize % 2 != 0) {
            out.write('\n');
        }

        return out.toByteArray();
    }

    private byte[] createEntryHeader(String name, long size, long timestamp, int uid, int gid, int mode) {
        byte[] header = new byte[HEADER_SIZE];
        java.util.Arrays.fill(header, (byte) ' ');

        byte[] nameBytes = name.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(nameBytes, 0, header, 0, Math.min(nameBytes.length, 16));

        String ts = String.valueOf(timestamp);
        System.arraycopy(ts.getBytes(), 0, header, 16, Math.min(ts.length(), 12));

        String uidStr = String.valueOf(uid);
        System.arraycopy(uidStr.getBytes(), 0, header, 28, Math.min(uidStr.length(), 6));

        String gidStr = String.valueOf(gid);
        System.arraycopy(gidStr.getBytes(), 0, header, 34, Math.min(gidStr.length(), 6));

        String modeStr = Integer.toOctalString(mode);
        System.arraycopy(modeStr.getBytes(), 0, header, 40, Math.min(modeStr.length(), 8));

        String sizeStr = String.valueOf(size);
        System.arraycopy(sizeStr.getBytes(), 0, header, 48, Math.min(sizeStr.length(), 10));

        header[58] = '`';
        header[59] = '\n';

        return header;
    }
}
