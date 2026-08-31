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

import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Represents a parsed RPM header section.
 *
 * <p>An RPM file contains two headers:
 * <ul>
 *   <li><b>Signature header:</b> Contains checksums and cryptographic signatures</li>
 *   <li><b>Main header:</b> Contains package metadata (name, version, files, etc.)</li>
 * </ul>
 *
 * <p>Each header consists of:
 * <ul>
 *   <li>A list of index entries that describe tag locations</li>
 *   <li>A data store containing the actual tag values</li>
 * </ul>
 *
 * <p>This class provides type-safe access to header tags through methods like
 * {@link #getString(int)}, {@link #getInt(int)}, and {@link #getBinary(int)}.
 * All accessor methods return {@link Optional} to handle missing tags gracefully.
 *
 * <p>Security: This class implements several protections against malformed RPMs:
 * <ul>
 *   <li>Bounds checking for all data store accesses</li>
 *   <li>Array size limits to prevent memory exhaustion</li>
 *   <li>Integer overflow protection in offset calculations</li>
 * </ul>
 *
 * @see IndexEntry
 * @see TagType
 * @see HeaderTag
 */
public final class Header {

    /**
     * The magic number that identifies an RPM header.
     */
    public static final int MAGIC = 0x8EADE801;

    // Security limits to prevent DoS attacks
    // Reduced from 1M to 100K - typical packages have thousands of files, not millions.
    // Package-private so HeaderParser can apply the same bound at PARSE time (finding B1).
    static final int MAX_ARRAY_SIZE = 100_000; // Max elements in any array

    private final List<IndexEntry> entries;
    private final Map<Integer, IndexEntry> entriesByTag;
    private final byte[] dataStore;

    /**
     * Creates a new header with the given entries and data store.
     *
     * @param entries the list of index entries
     * @param dataStore the data store bytes
     */
    public Header(@NotNull List<IndexEntry> entries, byte @NotNull [] dataStore) {
        this.entries = List.copyOf(entries);
        this.dataStore = dataStore.clone();

        Map<Integer, IndexEntry> byTag = new HashMap<>();
        for (IndexEntry entry : this.entries) {
            byTag.put(entry.tag(), entry);
        }
        this.entriesByTag = Collections.unmodifiableMap(byTag);
    }

    /**
     * Returns all index entries in this header.
     *
     * @return an unmodifiable list of index entries
     */
    public @NotNull List<IndexEntry> entries() {
        return entries;
    }

    /**
     * Returns the raw data store bytes.
     *
     * @return a copy of the data store
     */
    public byte @NotNull [] dataStore() {
        return dataStore.clone();
    }

    /**
     * Returns the index entry for the given tag, if present.
     *
     * @param tag the tag number
     * @return an Optional containing the entry, or empty if not found
     */
    public @NotNull Optional<IndexEntry> getEntry(int tag) {
        return Optional.ofNullable(entriesByTag.get(tag));
    }

    /**
     * Returns true if this header contains the given tag.
     *
     * @param tag the tag number
     * @return true if the tag is present
     */
    public boolean hasTag(int tag) {
        return entriesByTag.containsKey(tag);
    }

    /**
     * Gets a string value for the given tag.
     *
     * @param tag the tag number
     * @return an Optional containing the string value, or empty if not found
     */
    public @NotNull Optional<String> getString(int tag) {
        return getEntry(tag).flatMap(entry -> {
            if (entry.type() == TagType.STRING || entry.type() == TagType.I18N_STRING) {
                // Validate offset before reading
                if (entry.offset() < 0 || entry.offset() >= dataStore.length) {
                    return Optional.empty();
                }
                return Optional.of(readNullTerminatedString(entry.offset()));
            }
            return Optional.empty();
        });
    }

    /**
     * Gets a string array value for the given tag.
     *
     * @param tag the tag number
     * @return an Optional containing the string array, or empty if not found
     */
    public @NotNull Optional<List<String>> getStringArray(int tag) {
        return getEntry(tag).flatMap(entry -> {
            if (entry.type() == TagType.STRING_ARRAY || entry.type() == TagType.I18N_STRING) {
                // Validate count to prevent DoS
                if (entry.count() < 0 || entry.count() > MAX_ARRAY_SIZE) {
                    return Optional.empty();
                }
                // Validate initial offset
                if (entry.offset() < 0 || entry.offset() >= dataStore.length) {
                    return Optional.empty();
                }
                List<String> strings = new ArrayList<>(Math.min(entry.count(), 1000));
                int offset = entry.offset();
                for (int i = 0; i < entry.count(); i++) {
                    // Validate offset before reading. A mid-array out-of-bounds means the
                    // entry's declared count does not match the data store — CORRUPT. The
                    // whole array is unavailable, never a silently partial list (catalog §6).
                    if (offset < 0 || offset >= dataStore.length) {
                        return Optional.empty();
                    }
                    String s = readNullTerminatedString(offset);
                    strings.add(s);
                    offset += s.length() + 1; // +1 for null terminator
                }
                return Optional.of(Collections.unmodifiableList(strings));
            } else if (entry.type() == TagType.STRING) {
                if (entry.offset() < 0 || entry.offset() >= dataStore.length) {
                    return Optional.empty();
                }
                return Optional.of(List.of(readNullTerminatedString(entry.offset())));
            }
            return Optional.empty();
        });
    }

    /**
     * Gets an integer value for the given tag.
     *
     * @param tag the tag number
     * @return an Optional containing the integer value, or empty if not found
     */
    public @NotNull Optional<Integer> getInt(int tag) {
        return getEntry(tag).flatMap(entry -> {
            // Validate offset and bounds based on type
            int offset = entry.offset();
            int size = switch (entry.type()) {
                case INT8 -> 1;
                case INT16 -> 2;
                case INT32 -> 4;
                case INT64 -> 8;
                default -> 0;
            };
            if (size == 0 || offset < 0 || !boundsOk(offset, size)) {
                return Optional.empty();
            }
            ByteBuffer buffer = ByteBuffer.wrap(dataStore).order(ByteOrder.BIG_ENDIAN);
            return switch (entry.type()) {
                case INT8 -> Optional.of((int) buffer.get(offset));
                case INT16 -> Optional.of((int) buffer.getShort(offset));
                case INT32 -> Optional.of(buffer.getInt(offset));
                case INT64 -> Optional.of((int) buffer.getLong(offset));
                default -> Optional.empty();
            };
        });
    }

    /**
     * Gets a long value for the given tag.
     *
     * @param tag the tag number
     * @return an Optional containing the long value, or empty if not found
     */
    public @NotNull Optional<Long> getLong(int tag) {
        return getEntry(tag).flatMap(entry -> {
            // Validate offset and bounds based on type
            int offset = entry.offset();
            int size = switch (entry.type()) {
                case INT8 -> 1;
                case INT16 -> 2;
                case INT32 -> 4;
                case INT64 -> 8;
                default -> 0;
            };
            if (size == 0 || offset < 0 || !boundsOk(offset, size)) {
                return Optional.empty();
            }
            ByteBuffer buffer = ByteBuffer.wrap(dataStore).order(ByteOrder.BIG_ENDIAN);
            return switch (entry.type()) {
                case INT8 -> Optional.of((long) buffer.get(offset));
                case INT16 -> Optional.of((long) buffer.getShort(offset));
                case INT32 -> Optional.of((long) buffer.getInt(offset));
                case INT64 -> Optional.of(buffer.getLong(offset));
                default -> Optional.empty();
            };
        });
    }

    /**
     * Gets an integer array value for the given tag.
     *
     * @param tag the tag number
     * @return an Optional containing the integer array, or empty if not found
     */
    public @NotNull Optional<int[]> getIntArray(int tag) {
        return getEntry(tag).flatMap(entry -> {
            if (!entry.type().isInteger()) {
                return Optional.empty();
            }
            // Validate count to prevent DoS
            if (entry.count() < 0 || entry.count() > MAX_ARRAY_SIZE) {
                return Optional.empty();
            }
            // Validate offset
            if (entry.offset() < 0 || entry.offset() >= dataStore.length) {
                return Optional.empty();
            }
            ByteBuffer buffer = ByteBuffer.wrap(dataStore).order(ByteOrder.BIG_ENDIAN);
            int[] values = new int[entry.count()];
            int offset = entry.offset();
            int elementSize = switch (entry.type()) {
                case INT8 -> 1;
                case INT16 -> 2;
                case INT32 -> 4;
                case INT64 -> 8;
                default -> 1;
            };
            for (int i = 0; i < entry.count(); i++) {
                // Use safe arithmetic to prevent overflow
                int elementOffset;
                try {
                    elementOffset = Math.addExact(offset, Math.multiplyExact(i, elementSize));
                } catch (ArithmeticException e) {
                    return Optional.empty(); // Overflow detected
                }
                // Validate element offset is within bounds
                if (elementOffset < 0 || elementOffset + elementSize > dataStore.length) {
                    return Optional.empty();
                }
                values[i] = switch (entry.type()) {
                    case INT8 -> buffer.get(elementOffset);
                    case INT16 -> buffer.getShort(elementOffset);
                    case INT32 -> buffer.getInt(elementOffset);
                    case INT64 -> (int) buffer.getLong(elementOffset);
                    default -> 0;
                };
            }
            return Optional.of(values);
        });
    }

    /**
     * Gets a long array value for the given tag.
     *
     * @param tag the tag number
     * @return an Optional containing the long array, or empty if not found
     */
    public @NotNull Optional<long[]> getLongArray(int tag) {
        return getEntry(tag).flatMap(entry -> {
            if (!entry.type().isInteger()) {
                return Optional.empty();
            }
            // Validate count to prevent DoS
            if (entry.count() < 0 || entry.count() > MAX_ARRAY_SIZE) {
                return Optional.empty();
            }
            // Validate offset
            if (entry.offset() < 0 || entry.offset() >= dataStore.length) {
                return Optional.empty();
            }
            ByteBuffer buffer = ByteBuffer.wrap(dataStore).order(ByteOrder.BIG_ENDIAN);
            long[] values = new long[entry.count()];
            int offset = entry.offset();
            int elementSize = switch (entry.type()) {
                case INT8 -> 1;
                case INT16 -> 2;
                case INT32 -> 4;
                case INT64 -> 8;
                default -> 1;
            };
            for (int i = 0; i < entry.count(); i++) {
                // Use safe arithmetic to prevent overflow
                int elementOffset;
                try {
                    elementOffset = Math.addExact(offset, Math.multiplyExact(i, elementSize));
                } catch (ArithmeticException e) {
                    return Optional.empty(); // Overflow detected
                }
                // Validate element offset is within bounds
                if (elementOffset < 0 || elementOffset + elementSize > dataStore.length) {
                    return Optional.empty();
                }
                values[i] = switch (entry.type()) {
                    case INT8 -> buffer.get(elementOffset);
                    case INT16 -> buffer.getShort(elementOffset);
                    case INT32 -> buffer.getInt(elementOffset);
                    case INT64 -> buffer.getLong(elementOffset);
                    default -> 0;
                };
            }
            return Optional.of(values);
        });
    }

    /**
     * Gets binary data for the given tag.
     *
     * @param tag the tag number
     * @return an Optional containing the binary data, or empty if not found
     */
    public @NotNull Optional<byte[]> getBinary(int tag) {
        return getEntry(tag).flatMap(entry -> {
            if (entry.type() == TagType.BIN) {
                // Validate count to prevent DoS
                if (entry.count() < 0 || entry.count() > MAX_ARRAY_SIZE) {
                    return Optional.empty();
                }
                // Validate offset and bounds
                if (entry.offset() < 0 || entry.offset() >= dataStore.length) {
                    return Optional.empty();
                }
                // Check that offset + count doesn't overflow and is within bounds
                int endOffset;
                try {
                    endOffset = Math.addExact(entry.offset(), entry.count());
                } catch (ArithmeticException e) {
                    return Optional.empty(); // Overflow detected
                }
                if (endOffset > dataStore.length) {
                    return Optional.empty();
                }
                byte[] data = new byte[entry.count()];
                System.arraycopy(dataStore, entry.offset(), data, 0, entry.count());
                return Optional.of(data);
            }
            return Optional.empty();
        });
    }

    /** Overflow-safe bounds check (finding B10): offset + size with raw + wraps negative
     *  for offsets near Integer.MAX_VALUE, passing the check and then throwing an
     *  unchecked IndexOutOfBoundsException from an Optional accessor. */
    private boolean boundsOk(int offset, int size) {
        int end;
        try {
            end = Math.addExact(offset, size);
        } catch (ArithmeticException e) {
            return false;
        }
        return end <= dataStore.length;
    }

    /**
     * Strict variant of {@link #getString(int)}: corruption (type mismatch, out-of-bounds)
     * throws instead of returning empty (Fresh Scent Phase 6, decision D4).
     *
     * @throws InvalidFormatException if the tag exists but is corrupt
     */
    public @NotNull String getStringStrict(int tag) throws io.spicelabs.baharat.rpm.exception.InvalidFormatException {
        IndexEntry entry = entriesByTag.get(tag);
        if (entry == null) {
            return "";
        }
        if (entry.type() != TagType.STRING && entry.type() != TagType.I18N_STRING) {
            throw new io.spicelabs.baharat.rpm.exception.InvalidFormatException(
                    "Tag " + tag + " is not a string tag");
        }
        if (entry.offset() < 0 || entry.offset() >= dataStore.length) {
            throw new io.spicelabs.baharat.rpm.exception.InvalidFormatException(
                    "Tag " + tag + " offset out of bounds: " + entry.offset());
        }
        return readNullTerminatedString(entry.offset());
    }

    /**
     * Strict variant of {@link #getInt(int)}: corruption throws instead of returning empty.
     *
     * @throws InvalidFormatException if the tag exists but is corrupt
     */
    public int getIntStrict(int tag) throws io.spicelabs.baharat.rpm.exception.InvalidFormatException {
        IndexEntry entry = entriesByTag.get(tag);
        if (entry == null) {
            return 0;
        }
        int size = switch (entry.type()) {
            case INT8 -> 1;
            case INT16 -> 2;
            case INT32 -> 4;
            case INT64 -> 8;
            default -> throw new io.spicelabs.baharat.rpm.exception.InvalidFormatException(
                    "Tag " + tag + " is not an integer tag");
        };
        if (entry.offset() < 0 || !boundsOk(entry.offset(), size)) {
            throw new io.spicelabs.baharat.rpm.exception.InvalidFormatException(
                    "Tag " + tag + " offset out of bounds: " + entry.offset());
        }
        ByteBuffer buffer = ByteBuffer.wrap(dataStore).order(ByteOrder.BIG_ENDIAN);
        return switch (entry.type()) {
            case INT8 -> buffer.get(entry.offset());
            case INT16 -> buffer.getShort(entry.offset());
            case INT32 -> buffer.getInt(entry.offset());
            case INT64 -> (int) buffer.getLong(entry.offset());
            default -> throw new io.spicelabs.baharat.rpm.exception.InvalidFormatException(
                    "Tag " + tag + " is not an integer tag");
        };
    }

    private @NotNull String readNullTerminatedString(int offset) {
        // Validate offset bounds
        if (offset < 0 || offset >= dataStore.length) {
            return "";
        }
        int end = offset;
        while (end < dataStore.length && dataStore[end] != 0) {
            end++;
        }
        return new String(dataStore, offset, end - offset, StandardCharsets.UTF_8);
    }
}
