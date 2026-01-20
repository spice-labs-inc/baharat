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
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HeaderParserTest {

    @Test
    void parsesValidHeader() throws Exception {
        byte[] headerBytes = createTestHeader();
        BinaryReader reader = new BinaryReader(new ByteArrayInputStream(headerBytes));

        Header header = HeaderParser.parse(reader, false);

        assertThat(header.entries()).hasSize(2);
        assertThat(header.hasTag(1000)).isTrue();
        assertThat(header.hasTag(1001)).isTrue();
    }

    @Test
    void rejectsInvalidMagic() throws Exception {
        byte[] headerBytes = createTestHeader();
        headerBytes[0] = 0x00;  // Corrupt magic

        BinaryReader reader = new BinaryReader(new ByteArrayInputStream(headerBytes));

        assertThatThrownBy(() -> HeaderParser.parse(reader, false))
                .isInstanceOf(InvalidFormatException.class)
                .hasMessageContaining("Invalid header magic");
    }

    @Test
    void getsStringValue() throws Exception {
        byte[] headerBytes = createTestHeader();
        BinaryReader reader = new BinaryReader(new ByteArrayInputStream(headerBytes));

        Header header = HeaderParser.parse(reader, false);

        Optional<String> name = header.getString(1000);
        assertThat(name).isPresent().hasValue("test-package");
    }

    @Test
    void getsIntValue() throws Exception {
        byte[] headerBytes = createTestHeader();
        BinaryReader reader = new BinaryReader(new ByteArrayInputStream(headerBytes));

        Header header = HeaderParser.parse(reader, false);

        Optional<Integer> version = header.getInt(1001);
        assertThat(version).isPresent().hasValue(12345);
    }

    @Test
    void returnsEmptyForMissingTag() throws Exception {
        byte[] headerBytes = createTestHeader();
        BinaryReader reader = new BinaryReader(new ByteArrayInputStream(headerBytes));

        Header header = HeaderParser.parse(reader, false);

        assertThat(header.getString(9999)).isEmpty();
        assertThat(header.getInt(9999)).isEmpty();
    }

    @Test
    void tagTypeEnumValues() {
        assertThat(TagType.STRING.code()).isEqualTo(6);
        assertThat(TagType.INT32.code()).isEqualTo(4);
        assertThat(TagType.BIN.code()).isEqualTo(7);

        assertThat(TagType.STRING.isString()).isTrue();
        assertThat(TagType.INT32.isInteger()).isTrue();
    }

    @Test
    void tagTypeFromCode() {
        assertThat(TagType.fromCode(6)).hasValue(TagType.STRING);
        assertThat(TagType.fromCode(4)).hasValue(TagType.INT32);
        assertThat(TagType.fromCode(999)).isEmpty();
    }

    private byte[] createTestHeader() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // Magic: 0x8EADE801
        out.write(new byte[]{(byte) 0x8E, (byte) 0xAD, (byte) 0xE8, 0x01});

        // Reserved (4 bytes of zeros)
        out.write(new byte[4]);

        // Entry count: 2
        out.write(new byte[]{0, 0, 0, 2});

        // Data size: 20 bytes
        out.write(new byte[]{0, 0, 0, 20});

        // Index entry 1: tag 1000, STRING type, offset 0, count 1
        out.write(intToBytes(1000));
        out.write(intToBytes(TagType.STRING.code()));
        out.write(intToBytes(0));
        out.write(intToBytes(1));

        // Index entry 2: tag 1001, INT32 type, offset 13, count 1
        out.write(intToBytes(1001));
        out.write(intToBytes(TagType.INT32.code()));
        out.write(intToBytes(16));  // Aligned to 4 bytes
        out.write(intToBytes(1));

        // Data store
        byte[] nameData = "test-package\0".getBytes(StandardCharsets.US_ASCII);
        out.write(nameData);  // 13 bytes

        // Padding for alignment (3 bytes to get to 16)
        out.write(new byte[3]);

        // INT32 value: 12345
        out.write(intToBytes(12345));

        return out.toByteArray();
    }

    private byte[] intToBytes(int value) {
        return new byte[]{
                (byte) (value >> 24),
                (byte) (value >> 16),
                (byte) (value >> 8),
                (byte) value
        };
    }
}
