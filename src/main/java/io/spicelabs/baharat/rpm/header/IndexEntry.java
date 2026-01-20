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

/**
 * Represents an index entry in an RPM header.
 * Each index entry describes a single tag and points to its data in the data store.
 *
 * <p>Index entries are 16 bytes each:
 * <pre>
 * Offset  Size  Field
 * 0       4     Tag number
 * 4       4     Data type
 * 8       4     Offset in data store
 * 12      4     Count (number of elements)
 * </pre>
 *
 * @param tag the tag number
 * @param type the data type
 * @param offset the offset into the data store
 * @param count the number of elements
 */
public record IndexEntry(
        int tag,
        @NotNull TagType type,
        int offset,
        int count
) {
    /**
     * The size of an index entry in bytes.
     */
    public static final int SIZE = 16;

    /**
     * Creates a new index entry with validation.
     */
    public IndexEntry {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative: " + offset);
        }
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative: " + count);
        }
    }
}
