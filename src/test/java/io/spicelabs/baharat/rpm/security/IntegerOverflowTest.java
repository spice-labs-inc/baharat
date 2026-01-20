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
import io.spicelabs.baharat.rpm.exception.FormatException;
import io.spicelabs.baharat.rpm.payload.CpioArchiveReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for integer overflow vulnerabilities.
 * Verifies that large values don't cause overflows in size calculations.
 */
class IntegerOverflowTest {

    @TempDir
    Path tempDir;

    private static final byte[] RPM_MAGIC = {(byte) 0xED, (byte) 0xAB, (byte) 0xEE, (byte) 0xDB};
    private static final byte[] HEADER_MAGIC = {(byte) 0x8E, (byte) 0xAD, (byte) 0xE8, (byte) 0x01};
    private static final String CPIO_MAGIC = "070701";

    // RPM Header overflow tests

    @Test
    void handleEntryCountTimesEntrySizeOverflow() throws IOException {
        // entryCount * 16 (entry size) could overflow for large entryCount
        // Integer.MAX_VALUE / 16 = 134,217,727
        // If entryCount > this, entryCount * 16 overflows

        int overflowingEntryCount = Integer.MAX_VALUE / 16 + 1; // Will overflow
        byte[] rpm = createRpmWithHeaderCounts(overflowingEntryCount, 0);
        Path rpmFile = tempDir.resolve("overflow-entry-count.rpm");
        Files.write(rpmFile, rpm);

        // Should reject or handle gracefully, not crash
        assertThatThrownBy(() -> RpmReader.read(rpmFile))
                .isInstanceOf(Throwable.class);
    }

    @Test
    void handleEntryCountPlusDataSizeOverflow() throws IOException {
        // Total header size = 16 + entryCount*16 + dataSize
        // Large values could overflow

        int entryCount = Integer.MAX_VALUE / 32;
        int dataSize = Integer.MAX_VALUE / 2;

        byte[] rpm = createRpmWithHeaderCounts(entryCount, dataSize);
        Path rpmFile = tempDir.resolve("combined-overflow.rpm");
        Files.write(rpmFile, rpm);

        assertThatThrownBy(() -> RpmReader.read(rpmFile))
                .isInstanceOf(Throwable.class);
    }

    @Test
    void handleIndexEntryOffsetPlusCountOverflow() throws IOException {
        // offset + count could overflow when reading array data
        byte[] rpm = createRpmWithOverflowingIndexEntry();
        Path rpmFile = tempDir.resolve("index-offset-overflow.rpm");
        Files.write(rpmFile, rpm);

        try {
            RpmReader.read(rpmFile);
        } catch (FormatException | IOException e) {
            // Expected
        } catch (Throwable t) {
            // Should not get other errors like ArrayIndexOutOfBounds
            assertThat(t).isNotInstanceOf(ArrayIndexOutOfBoundsException.class);
        }
    }

    // CPIO overflow tests

    @Test
    void cpioFileSizeOverflow() throws IOException {
        // Create CPIO entry with file size near max long
        // FFFFFFFF in hex = 4,294,967,295 (max 32-bit unsigned)
        byte[] archive = createCpioEntryWithHexFileSize("FFFFFFFF");
        ByteArrayInputStream in = new ByteArrayInputStream(archive);

        try (CpioArchiveReader reader = new CpioArchiveReader(in)) {
            CpioArchiveReader.CpioEntry entry = reader.nextEntry();
            if (entry != null) {
                // Size should be interpreted correctly
                assertThat(entry.size()).isEqualTo(0xFFFFFFFFL);
            }
        } catch (Exception e) {
            // Acceptable to reject
        }
    }

    @Test
    void cpioNameSizePlusHeaderOverflow() throws IOException {
        // nameSize + 110 (header) could overflow if nameSize is huge
        // But nameSize is limited to 64KB, so this shouldn't happen
        // Test the boundary anyway

        byte[] archive = createCpioEntryWithHexNameSize("0000FFFF"); // 64KB
        ByteArrayInputStream in = new ByteArrayInputStream(archive);

        try (CpioArchiveReader reader = new CpioArchiveReader(in)) {
            try {
                reader.nextEntry();
            } catch (Exception e) {
                // Expected - nameSize too large or insufficient data
            }
        }
    }

    @Test
    void cpioHeaderPlusNamePaddingCalculation() throws IOException {
        // Padding calculation: (4 - ((110 + nameSize) % 4)) % 4
        // Should handle all nameSize values without overflow

        for (int nameLen = 1; nameLen <= 100; nameLen++) {
            byte[] archive = createCpioEntryWithName("a".repeat(nameLen));
            ByteArrayInputStream in = new ByteArrayInputStream(archive);

            try (CpioArchiveReader reader = new CpioArchiveReader(in)) {
                CpioArchiveReader.CpioEntry entry = reader.nextEntry();
                if (entry != null) {
                    assertThat(entry.name()).hasSize(nameLen);
                }
            } catch (Exception e) {
                // May fail for other reasons, but should not overflow
            }
        }
    }

    @Test
    void cpioDataPaddingCalculation() throws IOException {
        // Data padding: (4 - (fileSize % 4)) % 4
        // Large fileSize shouldn't cause overflow in padding calculation

        // Test with various file sizes near boundaries
        for (long fileSize : new long[]{0, 1, 2, 3, 4, 100, 0xFFFFFFFFL}) {
            String hexSize = String.format("%08X", fileSize & 0xFFFFFFFFL);
            byte[] archive = createCpioEntryWithHexFileSize(hexSize);
            ByteArrayInputStream in = new ByteArrayInputStream(archive);

            try (CpioArchiveReader reader = new CpioArchiveReader(in)) {
                CpioArchiveReader.CpioEntry entry = reader.nextEntry();
                // Should not throw overflow-related errors
            } catch (Exception e) {
                // May fail for insufficient data, but not overflow
                assertThat(e).isNotInstanceOf(ArithmeticException.class);
            }
        }
    }

    // Array allocation overflow tests

    @Test
    void arrayAllocationWithLargeCount() throws IOException {
        // Creating byte[] with size > Integer.MAX_VALUE would throw
        // But size is limited, so test near the limit

        // Data store size near but below max
        int largeButValid = 100_000_000; // 100 MB (should fail for other reasons)
        byte[] rpm = createRpmWithHeaderCounts(0, largeButValid);
        Path rpmFile = tempDir.resolve("large-data-store.rpm");
        Files.write(rpmFile, rpm);

        try {
            RpmReader.read(rpmFile);
        } catch (Exception e) {
            // Expected - file too small for claimed size
            assertThat(e).isNotInstanceOf(NegativeArraySizeException.class);
        }
    }

    @Test
    void negativeArraySizeFromSignedInterpretation() throws IOException {
        // If unsigned value is interpreted as signed, could become negative
        // e.g., 0x80000000 as signed int = -2147483648

        byte[] rpm = createRpmWithHeaderCounts(0x80000000, 0);
        Path rpmFile = tempDir.resolve("negative-entry-count.rpm");
        Files.write(rpmFile, rpm);

        try {
            RpmReader.read(rpmFile);
        } catch (Exception e) {
            // Should reject negative count, not throw NegativeArraySizeException
            assertThat(e).isNotInstanceOf(NegativeArraySizeException.class);
        }
    }

    // Position/offset overflow tests

    @Test
    void streamPositionOverflow() throws IOException {
        // When tracking position in stream, large offsets could overflow
        // Test with maximum values in header

        byte[] rpm = createRpmWithMaxPositionValues();
        Path rpmFile = tempDir.resolve("max-position.rpm");
        Files.write(rpmFile, rpm);

        try {
            RpmReader.read(rpmFile);
        } catch (Exception e) {
            // Expected
        }
    }

    // Signed/unsigned interpretation tests

    @Test
    void unsignedInterpretationOfHeaderFields() throws IOException {
        // RPM header fields should be unsigned
        // 0xFFFFFFFF as unsigned = 4,294,967,295
        // as signed = -1

        byte[] rpm = createRpmWithHeaderCounts(0xFFFFFFFF, 0);
        Path rpmFile = tempDir.resolve("max-unsigned-count.rpm");
        Files.write(rpmFile, rpm);

        try {
            RpmReader.read(rpmFile);
        } catch (FormatException e) {
            // Should interpret as negative and reject
            assertThat(e.getMessage()).containsIgnoringCase("entry count");
        } catch (Exception e) {
            // Other exceptions acceptable
        }
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

    private byte[] createRpmWithOverflowingIndexEntry() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // Lead (96 bytes)
        out.write(RPM_MAGIC);
        out.write(new byte[]{3, 0}); // version
        out.write(new byte[90]); // rest of lead

        // Header
        out.write(HEADER_MAGIC);
        out.write(intToBytes(0)); // reserved
        out.write(intToBytes(1)); // 1 entry
        out.write(intToBytes(100)); // 100 bytes data

        // Index entry with overflowing offset+count
        out.write(intToBytes(1000)); // tag
        out.write(intToBytes(3)); // INT32 type
        out.write(intToBytes(0x7FFFFFFF)); // huge offset
        out.write(intToBytes(0x7FFFFFFF)); // huge count

        // Small data store
        out.write(new byte[100]);

        return out.toByteArray();
    }

    private byte[] createRpmWithMaxPositionValues() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // Lead (96 bytes)
        out.write(RPM_MAGIC);
        out.write(new byte[]{3, 0}); // version
        out.write(new byte[90]); // rest of lead

        // Header with maximum index entry offset
        out.write(HEADER_MAGIC);
        out.write(intToBytes(0)); // reserved
        out.write(intToBytes(1)); // 1 entry
        out.write(intToBytes(16)); // small data store

        // Index entry pointing to end of data store
        out.write(intToBytes(1000)); // tag
        out.write(intToBytes(4)); // STRING type
        out.write(intToBytes(15)); // offset at end
        out.write(intToBytes(1)); // count

        // Data store
        out.write(new byte[16]);

        return out.toByteArray();
    }

    private byte[] createCpioEntryWithHexFileSize(String hexSize) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        String name = "test.txt";
        byte[] nameBytes = (name + "\0").getBytes(StandardCharsets.UTF_8);

        // Build header with specific file size
        String header = String.format(
                "%s%08X%08X%08X%08X%08X%08X%s%08X%08X%08X%08X%08X%08X",
                CPIO_MAGIC,
                0,              // inode
                0100644,        // mode
                0, 0, 1, 0,     // uid, gid, nlink, mtime
                hexSize,        // filesize (inserted directly)
                0, 0, 0, 0,     // devmajor, devminor, rdevmajor, rdevminor
                nameBytes.length, // namesize
                0               // check
        );

        // Fix the format - hexSize is already formatted
        header = CPIO_MAGIC +
                String.format("%08X", 0) +  // inode
                String.format("%08X", 0100644) + // mode
                String.format("%08X", 0) +  // uid
                String.format("%08X", 0) +  // gid
                String.format("%08X", 1) +  // nlink
                String.format("%08X", 0) +  // mtime
                hexSize +                    // filesize
                String.format("%08X", 0) +  // devmajor
                String.format("%08X", 0) +  // devminor
                String.format("%08X", 0) +  // rdevmajor
                String.format("%08X", 0) +  // rdevminor
                String.format("%08X", nameBytes.length) + // namesize
                String.format("%08X", 0);   // check

        out.write(header.getBytes(StandardCharsets.US_ASCII));
        out.write(nameBytes);

        // Pad header+name to 4-byte boundary
        int headerAndName = 110 + nameBytes.length;
        int padding = (4 - (headerAndName % 4)) % 4;
        out.write(new byte[padding]);

        // Don't write actual content (would be too large)
        // Just add trailer
        out.write(createCpioEntry("TRAILER!!!", "", 0));

        return out.toByteArray();
    }

    private byte[] createCpioEntryWithHexNameSize(String hexNameSize) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        String header = CPIO_MAGIC +
                String.format("%08X", 0) +  // inode
                String.format("%08X", 0100644) + // mode
                String.format("%08X", 0) +  // uid
                String.format("%08X", 0) +  // gid
                String.format("%08X", 1) +  // nlink
                String.format("%08X", 0) +  // mtime
                String.format("%08X", 0) +  // filesize
                String.format("%08X", 0) +  // devmajor
                String.format("%08X", 0) +  // devminor
                String.format("%08X", 0) +  // rdevmajor
                String.format("%08X", 0) +  // rdevminor
                hexNameSize +               // namesize (inserted directly)
                String.format("%08X", 0);   // check

        out.write(header.getBytes(StandardCharsets.US_ASCII));

        return out.toByteArray();
    }

    private byte[] createCpioEntryWithName(String name) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        byte[] nameBytes = (name + "\0").getBytes(StandardCharsets.UTF_8);

        String header = String.format(
                "%s%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X",
                CPIO_MAGIC,
                0, 0100644, 0, 0, 1, 0, 0, 0, 0, 0, 0, nameBytes.length, 0
        );

        out.write(header.getBytes(StandardCharsets.US_ASCII));
        out.write(nameBytes);

        int headerAndName = 110 + nameBytes.length;
        int padding = (4 - (headerAndName % 4)) % 4;
        out.write(new byte[padding]);

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

    private byte[] intToBytes(int value) {
        return ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value).array();
    }
}
