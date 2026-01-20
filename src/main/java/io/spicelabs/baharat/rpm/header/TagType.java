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

import java.util.Optional;

/**
 * Enumeration of RPM header tag data types.
 * These types define how the data associated with a tag should be interpreted.
 */
public enum TagType {
    /**
     * Null type, no data.
     */
    NULL(0, 0),

    /**
     * Single character (1 byte).
     */
    CHAR(1, 1),

    /**
     * 8-bit signed integer (1 byte).
     */
    INT8(2, 1),

    /**
     * 16-bit signed integer (2 bytes, big-endian).
     */
    INT16(3, 2),

    /**
     * 32-bit signed integer (4 bytes, big-endian).
     */
    INT32(4, 4),

    /**
     * 64-bit signed integer (8 bytes, big-endian).
     */
    INT64(5, 8),

    /**
     * Null-terminated string.
     */
    STRING(6, 1),

    /**
     * Binary data (byte array).
     */
    BIN(7, 1),

    /**
     * Array of null-terminated strings.
     */
    STRING_ARRAY(8, 1),

    /**
     * Internationalized string (locale-specific).
     */
    I18N_STRING(9, 1);

    private final int code;
    private final int alignment;

    TagType(int code, int alignment) {
        this.code = code;
        this.alignment = alignment;
    }

    /**
     * Returns the numeric code for this tag type.
     *
     * @return the type code
     */
    public int code() {
        return code;
    }

    /**
     * Returns the alignment requirement for this type in bytes.
     *
     * @return the alignment in bytes
     */
    public int alignment() {
        return alignment;
    }

    /**
     * Returns true if this type represents a string type.
     *
     * @return true if string, string array, or i18n string
     */
    public boolean isString() {
        return this == STRING || this == STRING_ARRAY || this == I18N_STRING;
    }

    /**
     * Returns true if this type represents an integer type.
     *
     * @return true if any integer type
     */
    public boolean isInteger() {
        return this == INT8 || this == INT16 || this == INT32 || this == INT64;
    }

    /**
     * Looks up a tag type by its numeric code.
     *
     * @param code the type code
     * @return an Optional containing the tag type, or empty if unknown
     */
    public static @NotNull Optional<TagType> fromCode(int code) {
        for (TagType type : values()) {
            if (type.code == code) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
