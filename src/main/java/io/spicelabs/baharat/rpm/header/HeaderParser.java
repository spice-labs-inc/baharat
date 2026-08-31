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
package io.spicelabs.baharat.rpm.header;

import io.spicelabs.baharat.rpm.exception.InvalidFormatException;
import io.spicelabs.baharat.rpm.io.BinaryReader;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for RPM header sections.
 *
 * <p>The header structure is:
 * <pre>
 * Offset  Size    Field
 * 0       4       Magic: 0x8EADE801
 * 4       4       Reserved (zeros)
 * 8       4       Index entry count
 * 12      4       Data store size
 * 16      16×N    Index entries
 * ...     ...     Data store
 * </pre>
 *
 * <p>After the header, padding is added to align to an 8-byte boundary
 * (for the signature header only).
 */
public final class HeaderParser {

    private static final Logger log = LoggerFactory.getLogger(HeaderParser.class);

    private static final int HEADER_INTRO_SIZE = 16;
    private static final int RESERVED_VALUE = 0;
    private static final int INDEX_ENTRY_SIZE = 16;

    /**
     * Maximum index entries accepted in any header (matches {@link Header#MAX_ARRAY_SIZE};
     * real RPMs have thousands, never 100k).
     */
    static final int MAX_ENTRY_COUNT = Header.MAX_ARRAY_SIZE;

    /**
     * Maximum data-store bytes accepted in any header. 64 MiB is far above any legitimate
     * RPM (even 100k-entry packages stay below ~15 MiB) while blocking multi-GiB hostile
     * allocations.
     */
    static final int MAX_DATA_SIZE = 64 * 1024 * 1024;

    private HeaderParser() {
        // Utility class
    }

    /**
     * Parses a header section from the given binary reader.
     *
     * @param reader the binary reader positioned at the start of the header
     * @param alignAfter if true, align to 8-byte boundary after reading
     * @return the parsed header
     * @throws InvalidFormatException if the header is invalid or malformed
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull Header parse(@NotNull BinaryReader reader, boolean alignAfter)
            throws InvalidFormatException, IOException {
        return parse(reader, alignAfter, MAX_ENTRY_COUNT, MAX_DATA_SIZE);
    }

    /**
     * Package-private overload with explicit caps so
     * boundary tests can exercise the limits with small injected values.
     */
    static @NotNull Header parse(@NotNull BinaryReader reader, boolean alignAfter,
                                 int maxEntryCount, int maxDataSize)
            throws InvalidFormatException, IOException {

        long startPosition = reader.position();
        log.trace("Parsing header at position {}", startPosition);

        // Read and validate magic number
        int magic = reader.readInt();
        if (magic != Header.MAGIC) {
            throw new InvalidFormatException(String.format(
                    "Invalid header magic number: expected 0x%08X, got 0x%08X",
                    Header.MAGIC, magic));
        }

        // Read and validate reserved bytes
        int reserved = reader.readInt();
        if (reserved != RESERVED_VALUE) {
            throw new InvalidFormatException(
                    "Invalid header reserved value: expected 0, got " + reserved);
        }

        // Read counts
        int entryCount = reader.readInt();
        int dataSize = reader.readInt();
        log.trace("Header entry count: {}, data size: {}", entryCount, dataSize);

        // Validate-BEFORE-allocate: both fields are hostile;
        // unbounded allocations from a 4-byte field are the OOM class. The caps are
        // generous for any legitimate RPM (100k entries / 64 MiB data store).
        if (entryCount < 0 || entryCount > maxEntryCount) {
            throw new InvalidFormatException(String.format(
                    "Invalid entry count: %d (max %d)", entryCount, maxEntryCount));
        }
        if (dataSize < 0 || dataSize > maxDataSize) {
            throw new InvalidFormatException(String.format(
                    "Invalid data size: %d (max %d)", dataSize, maxDataSize));
        }
        // Overflow-check the index-table arithmetic before allocating.
        try {
            Math.multiplyExact(entryCount, INDEX_ENTRY_SIZE);
        } catch (ArithmeticException e) {
            throw new InvalidFormatException(
                    "Invalid entry count (index table size overflow): " + entryCount);
        }

        // Read index entries
        List<IndexEntry> entries = new ArrayList<>(entryCount);
        for (int i = 0; i < entryCount; i++) {
            entries.add(parseIndexEntry(reader));
        }

        // Read data store
        byte[] dataStore = reader.readBytes(dataSize);

        // Align to 8-byte boundary if requested (for signature header)
        if (alignAfter) {
            reader.align(8);
        }

        log.debug("Parsed header: {} entries, {} bytes data store", entries.size(), dataStore.length);
        return new Header(entries, dataStore);
    }

    /**
     * Parses a single index entry.
     *
     * @param reader the binary reader
     * @return the parsed index entry
     * @throws InvalidFormatException if the entry is invalid
     * @throws IOException if an I/O error occurs
     */
    private static @NotNull IndexEntry parseIndexEntry(@NotNull BinaryReader reader)
            throws InvalidFormatException, IOException {

        int tag = reader.readInt();
        int typeCode = reader.readInt();
        int offset = reader.readInt();
        int count = reader.readInt();

        TagType type = TagType.fromCode(typeCode).orElseThrow(() ->
                new InvalidFormatException("Unknown tag type code: " + typeCode));

        return new IndexEntry(tag, type, offset, count);
    }
}
