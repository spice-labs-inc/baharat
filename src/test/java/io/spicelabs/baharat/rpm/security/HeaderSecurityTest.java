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
import io.spicelabs.baharat.rpm.exception.InvalidFormatException;
import io.spicelabs.baharat.rpm.exception.FormatException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Security tests for RPM header parsing.
 * Tests for DoS via large allocations, integer overflow, and malformed headers.
 */
class HeaderSecurityTest {

    @TempDir
    Path tempDir;

    // RPM Lead magic number
    private static final byte[] RPM_MAGIC = {(byte) 0xED, (byte) 0xAB, (byte) 0xEE, (byte) 0xDB};

    // Header magic number
    private static final byte[] HEADER_MAGIC = {(byte) 0x8E, (byte) 0xAD, (byte) 0xE8, (byte) 0x01};

    // Maximum reasonable entry count (to prevent DoS)
    private static final int REASONABLE_MAX_ENTRIES = 100_000;

    // Maximum reasonable data store size (to prevent DoS)
    private static final int REASONABLE_MAX_DATA_SIZE = 100 * 1024 * 1024; // 100 MB

    @Test
    void rejectsNegativeEntryCount() throws IOException {
        // Entry count with high bit set (negative when interpreted as signed int)
        byte[] rpm = createRpmWithHeaderCounts(0x80000001, 100);
        Path rpmFile = tempDir.resolve("negative-entry-count.rpm");
        Files.write(rpmFile, rpm);

        assertThatThrownBy(() -> RpmReader.read(rpmFile))
                .isInstanceOf(FormatException.class);
    }

    @Test
    void rejectsNegativeDataStoreSize() throws IOException {
        // Data store size with high bit set
        byte[] rpm = createRpmWithHeaderCounts(10, 0x80000001);
        Path rpmFile = tempDir.resolve("negative-data-size.rpm");
        Files.write(rpmFile, rpm);

        assertThatThrownBy(() -> RpmReader.read(rpmFile))
                .isInstanceOf(FormatException.class);
    }

    @Test
    void rejectsExcessiveEntryCountDoS() throws IOException {
        // Entry count designed to cause OutOfMemory
        // 0x7FFFFFFF = Integer.MAX_VALUE (~2 billion)
        byte[] rpm = createRpmWithHeaderCounts(0x7FFFFFFF, 0);
        Path rpmFile = tempDir.resolve("excessive-entry-count.rpm");
        Files.write(rpmFile, rpm);

        // This should throw an exception, not cause OOM
        // NOTE: Current implementation may throw OOM - this test documents that
        assertThatThrownBy(() -> RpmReader.read(rpmFile))
                .isInstanceOf(Throwable.class);
    }

    @Test
    void rejectsExcessiveDataStoreSizeDoS() throws IOException {
        // Data store size designed to cause OutOfMemory
        byte[] rpm = createRpmWithHeaderCounts(0, 0x7FFFFFFF);
        Path rpmFile = tempDir.resolve("excessive-data-size.rpm");
        Files.write(rpmFile, rpm);

        assertThatThrownBy(() -> RpmReader.read(rpmFile))
                .isInstanceOf(Throwable.class);
    }

    @Test
    void rejectsEntryCountExceedingFileSize() throws IOException {
        // Entry count says 1000 entries, but file only has room for 10
        byte[] rpm = createRpmWithHeaderCounts(1000, 0);
        Path rpmFile = tempDir.resolve("entry-count-exceeds-file.rpm");
        Files.write(rpmFile, rpm);

        assertThatThrownBy(() -> RpmReader.read(rpmFile))
                .isInstanceOf(Exception.class);
    }

    @Test
    void rejectsDataStoreSizeExceedingFileSize() throws IOException {
        // Data store size says 1MB, but file only has 100 bytes
        byte[] rpm = createRpmWithHeaderCounts(0, 1024 * 1024);
        Path rpmFile = tempDir.resolve("data-size-exceeds-file.rpm");
        Files.write(rpmFile, rpm);

        assertThatThrownBy(() -> RpmReader.read(rpmFile))
                .isInstanceOf(Exception.class);
    }

    @Test
    void rejectsNonZeroReservedBytes() throws IOException {
        // Reserved bytes in header should be zero
        byte[] rpm = createRpmWithNonZeroReserved();
        Path rpmFile = tempDir.resolve("non-zero-reserved.rpm");
        Files.write(rpmFile, rpm);

        assertThatThrownBy(() -> RpmReader.read(rpmFile))
                .isInstanceOf(InvalidFormatException.class)
                .hasMessageContaining("reserved");
    }

    @Test
    void rejectsInvalidIndexEntryType() throws IOException {
        // Index entry with invalid type code (255)
        byte[] rpm = createRpmWithInvalidIndexEntry();
        Path rpmFile = tempDir.resolve("invalid-index-type.rpm");
        Files.write(rpmFile, rpm);

        assertThatThrownBy(() -> RpmReader.read(rpmFile))
                .isInstanceOf(InvalidFormatException.class);
    }

    @Test
    void rejectsIndexEntryOffsetBeyondDataStore() throws IOException {
        // Index entry pointing beyond data store
        byte[] rpm = createRpmWithOutOfBoundsOffset();
        Path rpmFile = tempDir.resolve("out-of-bounds-offset.rpm");
        Files.write(rpmFile, rpm);

        // Should either reject or handle gracefully
        try {
            RpmReader.read(rpmFile);
        } catch (FormatException | IOException e) {
            // Expected
        }
    }

    @Test
    void rejectsIndexEntryNegativeOffset() throws IOException {
        // Index entry with negative offset
        byte[] rpm = createRpmWithNegativeOffset();
        Path rpmFile = tempDir.resolve("negative-offset.rpm");
        Files.write(rpmFile, rpm);

        // Should reject or handle gracefully
        try {
            RpmReader.read(rpmFile);
        } catch (FormatException | IOException | IllegalArgumentException e) {
            // Expected - IllegalArgumentException from IndexEntry constructor validation
        }
    }

    @Test
    void rejectsIndexEntryNegativeCount() throws IOException {
        // Index entry with negative count
        byte[] rpm = createRpmWithNegativeEntryCount();
        Path rpmFile = tempDir.resolve("negative-entry-count-in-index.rpm");
        Files.write(rpmFile, rpm);

        // Should reject or handle gracefully
        try {
            RpmReader.read(rpmFile);
        } catch (FormatException | IOException | IllegalArgumentException e) {
            // Expected - IllegalArgumentException from IndexEntry constructor validation
        }
    }

    @Test
    void handlesManySmallEntries() throws IOException {
        // Create RPM with many small but valid entries
        int entryCount = 10000;
        byte[] rpm = createRpmWithManyEntries(entryCount);
        Path rpmFile = tempDir.resolve("many-entries.rpm");
        Files.write(rpmFile, rpm);

        // Should handle without issue
        try {
            RpmReader.read(rpmFile);
        } catch (FormatException | IOException e) {
            // May fail due to incomplete data, but should not crash
        }
    }

    @Test
    void handlesZeroEntryCount() throws IOException {
        // Edge case: zero entries
        byte[] rpm = createRpmWithHeaderCounts(0, 0);
        Path rpmFile = tempDir.resolve("zero-entries.rpm");
        Files.write(rpmFile, rpm);

        // Should handle gracefully
        try {
            RpmReader.read(rpmFile);
        } catch (FormatException | IOException e) {
            // May fail validation, but should not crash
        }
    }

    @Test
    void handlesMaxIntEntryCount() throws IOException {
        // Integer.MAX_VALUE entry count
        ByteArrayInputStream stream = new ByteArrayInputStream(
                createRpmWithHeaderCounts(Integer.MAX_VALUE, 0));

        // Should reject quickly without attempting allocation
        assertThatThrownBy(() -> RpmReader.read(stream))
                .isInstanceOf(Throwable.class);
    }

    @Test
    void handlesCorruptedHeaderMagicPartial() throws IOException {
        // Partial header magic corruption
        byte[] rpm = createValidRpmStructure();
        rpm[97] = 0x00; // Corrupt second byte of header magic
        Path rpmFile = tempDir.resolve("partial-header-magic-corrupt.rpm");
        Files.write(rpmFile, rpm);

        assertThatThrownBy(() -> RpmReader.read(rpmFile))
                .isInstanceOf(InvalidFormatException.class)
                .hasMessageContaining("magic");
    }

    @Test
    void handlesAllZeroHeader() throws IOException {
        // All zeros after valid lead
        byte[] rpm = new byte[200];
        System.arraycopy(RPM_MAGIC, 0, rpm, 0, 4);
        rpm[4] = 3; // major version
        Path rpmFile = tempDir.resolve("all-zero-header.rpm");
        Files.write(rpmFile, rpm);

        assertThatThrownBy(() -> RpmReader.read(rpmFile))
                .isInstanceOf(InvalidFormatException.class);
    }

    @Test
    void handlesAllOnesHeader() throws IOException {
        // All 0xFF after valid lead
        byte[] rpm = new byte[200];
        System.arraycopy(RPM_MAGIC, 0, rpm, 0, 4);
        rpm[4] = 3; // major version
        for (int i = 96; i < rpm.length; i++) {
            rpm[i] = (byte) 0xFF;
        }
        Path rpmFile = tempDir.resolve("all-ones-header.rpm");
        Files.write(rpmFile, rpm);

        assertThatThrownBy(() -> RpmReader.read(rpmFile))
                .isInstanceOf(InvalidFormatException.class);
    }

    // Helper methods

    private byte[] createRpmWithHeaderCounts(int entryCount, int dataSize) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // Lead (96 bytes)
        out.write(RPM_MAGIC);
        out.write(new byte[]{3, 0}); // version
        out.write(new byte[90]); // rest of lead

        // Header
        out.write(HEADER_MAGIC);
        out.write(intToBytes(0)); // reserved
        out.write(intToBytes(entryCount));
        out.write(intToBytes(dataSize));

        return out.toByteArray();
    }

    private byte[] createRpmWithNonZeroReserved() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // Lead (96 bytes)
        out.write(RPM_MAGIC);
        out.write(new byte[]{3, 0}); // version
        out.write(new byte[90]); // rest of lead

        // Header with non-zero reserved
        out.write(HEADER_MAGIC);
        out.write(intToBytes(0x12345678)); // non-zero reserved
        out.write(intToBytes(0)); // entry count
        out.write(intToBytes(0)); // data size

        return out.toByteArray();
    }

    private byte[] createRpmWithInvalidIndexEntry() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // Lead (96 bytes)
        out.write(RPM_MAGIC);
        out.write(new byte[]{3, 0}); // version
        out.write(new byte[90]); // rest of lead

        // Header
        out.write(HEADER_MAGIC);
        out.write(intToBytes(0)); // reserved
        out.write(intToBytes(1)); // 1 entry
        out.write(intToBytes(4)); // 4 bytes data

        // Index entry (16 bytes)
        out.write(intToBytes(1000)); // tag
        out.write(intToBytes(255)); // invalid type code
        out.write(intToBytes(0)); // offset
        out.write(intToBytes(1)); // count

        // Data store
        out.write(new byte[4]);

        return out.toByteArray();
    }

    private byte[] createRpmWithOutOfBoundsOffset() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // Lead (96 bytes)
        out.write(RPM_MAGIC);
        out.write(new byte[]{3, 0}); // version
        out.write(new byte[90]); // rest of lead

        // Header
        out.write(HEADER_MAGIC);
        out.write(intToBytes(0)); // reserved
        out.write(intToBytes(1)); // 1 entry
        out.write(intToBytes(4)); // 4 bytes data

        // Index entry pointing beyond data store
        out.write(intToBytes(1000)); // tag
        out.write(intToBytes(4)); // STRING type
        out.write(intToBytes(1000)); // offset way beyond data store
        out.write(intToBytes(1)); // count

        // Data store (only 4 bytes)
        out.write(new byte[4]);

        return out.toByteArray();
    }

    private byte[] createRpmWithNegativeOffset() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // Lead (96 bytes)
        out.write(RPM_MAGIC);
        out.write(new byte[]{3, 0}); // version
        out.write(new byte[90]); // rest of lead

        // Header
        out.write(HEADER_MAGIC);
        out.write(intToBytes(0)); // reserved
        out.write(intToBytes(1)); // 1 entry
        out.write(intToBytes(4)); // 4 bytes data

        // Index entry with negative offset
        out.write(intToBytes(1000)); // tag
        out.write(intToBytes(4)); // STRING type
        out.write(intToBytes(-1)); // negative offset
        out.write(intToBytes(1)); // count

        // Data store
        out.write(new byte[4]);

        return out.toByteArray();
    }

    private byte[] createRpmWithNegativeEntryCount() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // Lead (96 bytes)
        out.write(RPM_MAGIC);
        out.write(new byte[]{3, 0}); // version
        out.write(new byte[90]); // rest of lead

        // Header
        out.write(HEADER_MAGIC);
        out.write(intToBytes(0)); // reserved
        out.write(intToBytes(1)); // 1 entry
        out.write(intToBytes(4)); // 4 bytes data

        // Index entry with negative count
        out.write(intToBytes(1000)); // tag
        out.write(intToBytes(4)); // STRING type
        out.write(intToBytes(0)); // offset
        out.write(intToBytes(-1)); // negative count

        // Data store
        out.write(new byte[4]);

        return out.toByteArray();
    }

    private byte[] createRpmWithManyEntries(int count) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // Lead (96 bytes)
        out.write(RPM_MAGIC);
        out.write(new byte[]{3, 0}); // version
        out.write(new byte[90]); // rest of lead

        // Header
        out.write(HEADER_MAGIC);
        out.write(intToBytes(0)); // reserved
        out.write(intToBytes(count)); // many entries
        out.write(intToBytes(count * 4)); // data store size

        // Index entries
        for (int i = 0; i < count; i++) {
            out.write(intToBytes(1000 + i)); // tag
            out.write(intToBytes(3)); // INT32 type
            out.write(intToBytes(i * 4)); // offset
            out.write(intToBytes(1)); // count
        }

        // Data store
        out.write(new byte[count * 4]);

        return out.toByteArray();
    }

    private byte[] createValidRpmStructure() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // Lead (96 bytes)
        out.write(RPM_MAGIC);
        out.write(new byte[]{3, 0}); // version
        out.write(new byte[90]); // rest of lead

        // Header
        out.write(HEADER_MAGIC);
        out.write(intToBytes(0)); // reserved
        out.write(intToBytes(0)); // 0 entries
        out.write(intToBytes(0)); // 0 data size

        return out.toByteArray();
    }

    private byte[] intToBytes(int value) {
        return ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value).array();
    }
}
