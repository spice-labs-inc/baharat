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
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Header strict-accessor and int-overflow tests.
 */
class HeaderStrictAccessorTest {

    private static Header headerWith(TagType type, int offset, int count, byte[] dataStore) {
        IndexEntry entry = new IndexEntry(HeaderTag.NAME.tag(), type, offset, count);
        return new Header(List.of(entry), dataStore);
    }

    private static byte[] intStore(int value) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{(byte) (value >> 24), (byte) (value >> 16),
                (byte) (value >> 8), (byte) value});
        return out.toByteArray();
    }


    // Theory: offset = Integer.MAX_VALUE with size 4 wraps NEGATIVE in raw arithmetic,
    //         passed the bounds check, then threw an unchecked IndexOutOfBoundsException
    //         from an Optional accessor. Math.addExact must reject it (empty / strict throw).
    // Revert-check: reverting to raw `offset + size` makes getInt throw IOBE.
    @Test
    void overflowedOffsetRejectedByIntAccessors() throws InvalidFormatException {
        Header header = headerWith(TagType.INT32, Integer.MAX_VALUE, 1, new byte[16]);
        assertThat(header.getInt(HeaderTag.NAME.tag())).isEmpty();
        assertThatThrownBy(() -> header.getIntStrict(HeaderTag.NAME.tag()))
                .isInstanceOf(InvalidFormatException.class);
    }

    // Requirement: (strict accessors)
    // Theory: strict accessors report corruption loudly; lenient accessors return empty.
    @Test
    void strictAccessorsThrowOnCorruptData() throws InvalidFormatException {
        byte[] store = "hello\0".getBytes(StandardCharsets.UTF_8);
        Header ok = headerWith(TagType.STRING, 0, 1, store);
        assertThat(ok.getStringStrict(HeaderTag.NAME.tag())).isEqualTo("hello");
        assertThat(ok.getString(HeaderTag.NAME.tag())).contains("hello");

        Header corrupt = headerWith(TagType.STRING, 100, 1, store);
        assertThat(corrupt.getString(HeaderTag.NAME.tag())).isEmpty();
        assertThatThrownBy(() -> corrupt.getStringStrict(HeaderTag.NAME.tag()))
                .isInstanceOf(InvalidFormatException.class);
    }

    // Requirement: (type mismatch)
    @Test
    void strictAccessorsRejectTypeMismatch() throws InvalidFormatException, java.io.IOException {
        Header header = headerWith(TagType.INT32, 0, 1, intStore(7));
        assertThatThrownBy(() -> header.getStringStrict(HeaderTag.NAME.tag()))
                .isInstanceOf(InvalidFormatException.class);
        assertThat(header.getIntStrict(HeaderTag.NAME.tag())).isEqualTo(7);
    }

    // Requirement: (partial-array fix)
    // Theory: a STRING_ARRAY whose count exceeds the data store is CORRUPT — the whole
    //         array is unavailable, never a silently partial list.
    // Revert-check: restoring the `break` returns a partial list.
    @Test
    void stringArrayOobReturnsEmptyNotPartial() {
        byte[] store = "only-one\0".getBytes(StandardCharsets.UTF_8);
        Header header = headerWith(TagType.STRING_ARRAY, 0, 5, store);
        Optional<List<String>> result = header.getStringArray(HeaderTag.NAME.tag());
        assertThat(result).isEmpty();
    }
}
