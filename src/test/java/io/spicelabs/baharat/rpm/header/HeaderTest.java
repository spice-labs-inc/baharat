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

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HeaderTest {

    @Test
    void headerMagic() {
        assertThat(Header.MAGIC).isEqualTo(0x8EADE801);
    }

    @Test
    void emptyHeader() {
        Header header = new Header(List.of(), new byte[0]);

        assertThat(header.entries()).isEmpty();
        assertThat(header.dataStore()).isEmpty();
        assertThat(header.hasTag(1000)).isFalse();
        assertThat(header.getEntry(1000)).isEmpty();
    }

    @Test
    void headerWithStringEntry() throws Exception {
        byte[] data = "test-value\0".getBytes(StandardCharsets.US_ASCII);
        List<IndexEntry> entries = List.of(
                new IndexEntry(1000, TagType.STRING, 0, 1)
        );

        Header header = new Header(entries, data);

        assertThat(header.hasTag(1000)).isTrue();
        assertThat(header.getString(1000)).hasValue("test-value");
    }

    @Test
    void headerWithInt32Entry() throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(12345);
        List<IndexEntry> entries = List.of(
                new IndexEntry(1001, TagType.INT32, 0, 1)
        );

        Header header = new Header(entries, buffer.array());

        assertThat(header.getInt(1001)).hasValue(12345);
        assertThat(header.getLong(1001)).hasValue(12345L);
    }

    @Test
    void headerWithInt8Entry() throws Exception {
        byte[] data = {42};
        List<IndexEntry> entries = List.of(
                new IndexEntry(1002, TagType.INT8, 0, 1)
        );

        Header header = new Header(entries, data);

        assertThat(header.getInt(1002)).hasValue(42);
    }

    @Test
    void headerWithInt16Entry() throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN);
        buffer.putShort((short) 1000);
        List<IndexEntry> entries = List.of(
                new IndexEntry(1003, TagType.INT16, 0, 1)
        );

        Header header = new Header(entries, buffer.array());

        assertThat(header.getInt(1003)).hasValue(1000);
    }

    @Test
    void headerWithInt64Entry() throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN);
        buffer.putLong(9876543210L);
        List<IndexEntry> entries = List.of(
                new IndexEntry(1004, TagType.INT64, 0, 1)
        );

        Header header = new Header(entries, buffer.array());

        assertThat(header.getLong(1004)).hasValue(9876543210L);
        assertThat(header.getInt(1004)).isPresent(); // Truncated
    }

    @Test
    void headerWithStringArrayEntry() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("first\0".getBytes(StandardCharsets.US_ASCII));
        out.write("second\0".getBytes(StandardCharsets.US_ASCII));
        out.write("third\0".getBytes(StandardCharsets.US_ASCII));

        List<IndexEntry> entries = List.of(
                new IndexEntry(1005, TagType.STRING_ARRAY, 0, 3)
        );

        Header header = new Header(entries, out.toByteArray());

        Optional<List<String>> result = header.getStringArray(1005);
        assertThat(result).isPresent();
        assertThat(result.get()).containsExactly("first", "second", "third");
    }

    @Test
    void headerWithIntArrayEntry() throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(100);
        buffer.putInt(200);
        buffer.putInt(300);

        List<IndexEntry> entries = List.of(
                new IndexEntry(1006, TagType.INT32, 0, 3)
        );

        Header header = new Header(entries, buffer.array());

        Optional<int[]> result = header.getIntArray(1006);
        assertThat(result).isPresent();
        assertThat(result.get()).containsExactly(100, 200, 300);
    }

    @Test
    void headerWithLongArrayEntry() throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN);
        buffer.putLong(1000000000L);
        buffer.putLong(2000000000L);

        List<IndexEntry> entries = List.of(
                new IndexEntry(1007, TagType.INT64, 0, 2)
        );

        Header header = new Header(entries, buffer.array());

        Optional<long[]> result = header.getLongArray(1007);
        assertThat(result).isPresent();
        assertThat(result.get()).containsExactly(1000000000L, 2000000000L);
    }

    @Test
    void headerWithBinaryEntry() throws Exception {
        byte[] data = {0x01, 0x02, 0x03, 0x04, 0x05};

        List<IndexEntry> entries = List.of(
                new IndexEntry(1008, TagType.BIN, 0, 5)
        );

        Header header = new Header(entries, data);

        Optional<byte[]> result = header.getBinary(1008);
        assertThat(result).isPresent();
        assertThat(result.get()).containsExactly(0x01, 0x02, 0x03, 0x04, 0x05);
    }

    @Test
    void getStringReturnsEmptyForWrongType() {
        ByteBuffer buffer = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(12345);
        List<IndexEntry> entries = List.of(
                new IndexEntry(1000, TagType.INT32, 0, 1)
        );

        Header header = new Header(entries, buffer.array());

        assertThat(header.getString(1000)).isEmpty();
    }

    @Test
    void getIntReturnsEmptyForStringType() {
        byte[] data = "test\0".getBytes(StandardCharsets.US_ASCII);
        List<IndexEntry> entries = List.of(
                new IndexEntry(1000, TagType.STRING, 0, 1)
        );

        Header header = new Header(entries, data);

        assertThat(header.getInt(1000)).isEmpty();
    }

    @Test
    void getIntArrayReturnsEmptyForStringType() {
        byte[] data = "test\0".getBytes(StandardCharsets.US_ASCII);
        List<IndexEntry> entries = List.of(
                new IndexEntry(1000, TagType.STRING, 0, 1)
        );

        Header header = new Header(entries, data);

        assertThat(header.getIntArray(1000)).isEmpty();
    }

    @Test
    void getBinaryReturnsEmptyForWrongType() {
        byte[] data = "test\0".getBytes(StandardCharsets.US_ASCII);
        List<IndexEntry> entries = List.of(
                new IndexEntry(1000, TagType.STRING, 0, 1)
        );

        Header header = new Header(entries, data);

        assertThat(header.getBinary(1000)).isEmpty();
    }

    // Security validation tests

    @Test
    void rejectsNegativeOffset() {
        // IndexEntry validates offset at construction, so we test that
        assertThatThrownBy(() -> new IndexEntry(1000, TagType.STRING, -1, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void rejectsOffsetBeyondDataStore() {
        List<IndexEntry> entries = List.of(
                new IndexEntry(1000, TagType.STRING, 100, 1)
        );

        Header header = new Header(entries, new byte[10]);

        assertThat(header.getString(1000)).isEmpty();
    }

    @Test
    void rejectsNegativeCount() {
        // IndexEntry validates count at construction, so we test that
        assertThatThrownBy(() -> new IndexEntry(1000, TagType.INT32, 0, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void rejectsExcessiveCount() {
        List<IndexEntry> entries = List.of(
                new IndexEntry(1000, TagType.INT32, 0, 2_000_000)
        );

        Header header = new Header(entries, new byte[100]);

        assertThat(header.getIntArray(1000)).isEmpty();
    }

    @Test
    void handlesOverflowInOffsetCalculation() {
        // Create an entry that would cause integer overflow in offset + i * 4
        List<IndexEntry> entries = List.of(
                new IndexEntry(1000, TagType.INT32, Integer.MAX_VALUE - 10, 100)
        );

        Header header = new Header(entries, new byte[100]);

        // Should return empty rather than crash
        assertThat(header.getIntArray(1000)).isEmpty();
    }

    @Test
    void dataStoreReturnsCopy() {
        byte[] original = {1, 2, 3, 4, 5};
        Header header = new Header(List.of(), original);

        byte[] copy = header.dataStore();
        copy[0] = 99;

        // Original should be unchanged
        assertThat(header.dataStore()[0]).isEqualTo((byte) 1);
    }

    @Test
    void entriesReturnsUnmodifiableList() {
        List<IndexEntry> entries = List.of(
                new IndexEntry(1000, TagType.STRING, 0, 1)
        );
        Header header = new Header(entries, new byte[10]);

        assertThatThrownBy(() -> header.entries().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // IndexEntry tests

    @Test
    void indexEntryCreation() {
        IndexEntry entry = new IndexEntry(1000, TagType.STRING, 50, 1);

        assertThat(entry.tag()).isEqualTo(1000);
        assertThat(entry.type()).isEqualTo(TagType.STRING);
        assertThat(entry.offset()).isEqualTo(50);
        assertThat(entry.count()).isEqualTo(1);
    }

    // TagType tests

    @Test
    void tagTypeFromCode() {
        assertThat(TagType.fromCode(0)).hasValue(TagType.NULL);
        assertThat(TagType.fromCode(1)).hasValue(TagType.CHAR);
        assertThat(TagType.fromCode(2)).hasValue(TagType.INT8);
        assertThat(TagType.fromCode(3)).hasValue(TagType.INT16);
        assertThat(TagType.fromCode(4)).hasValue(TagType.INT32);
        assertThat(TagType.fromCode(5)).hasValue(TagType.INT64);
        assertThat(TagType.fromCode(6)).hasValue(TagType.STRING);
        assertThat(TagType.fromCode(7)).hasValue(TagType.BIN);
        assertThat(TagType.fromCode(8)).hasValue(TagType.STRING_ARRAY);
        assertThat(TagType.fromCode(9)).hasValue(TagType.I18N_STRING);
        assertThat(TagType.fromCode(999)).isEmpty();
    }

    @Test
    void tagTypeProperties() {
        assertThat(TagType.STRING.isString()).isTrue();
        assertThat(TagType.STRING.isInteger()).isFalse();
        assertThat(TagType.STRING_ARRAY.isString()).isTrue();
        assertThat(TagType.I18N_STRING.isString()).isTrue();

        assertThat(TagType.INT8.isInteger()).isTrue();
        assertThat(TagType.INT16.isInteger()).isTrue();
        assertThat(TagType.INT32.isInteger()).isTrue();
        assertThat(TagType.INT64.isInteger()).isTrue();
        assertThat(TagType.INT32.isString()).isFalse();

        assertThat(TagType.BIN.isString()).isFalse();
        assertThat(TagType.BIN.isInteger()).isFalse();
    }

    // SignatureTag tests

    @Test
    @SuppressWarnings("deprecation") // Testing deprecated PGP tag for backward compatibility
    void signatureTagValues() {
        assertThat(SignatureTag.SHA1.tag()).isEqualTo(269);
        assertThat(SignatureTag.SHA256.tag()).isEqualTo(273);
        assertThat(SignatureTag.RSA.tag()).isEqualTo(268);
        assertThat(SignatureTag.DSA.tag()).isEqualTo(267);
        assertThat(SignatureTag.PGP.tag()).isEqualTo(1002);
        assertThat(SignatureTag.GPG.tag()).isEqualTo(1005);
    }

    @Test
    void signatureTagFromTag() {
        assertThat(SignatureTag.fromTag(269)).hasValue(SignatureTag.SHA1);
        assertThat(SignatureTag.fromTag(268)).hasValue(SignatureTag.RSA);
        assertThat(SignatureTag.fromTag(99999)).isEmpty();
    }

    // HeaderTag tests

    @Test
    void headerTagValues() {
        assertThat(HeaderTag.NAME.tag()).isEqualTo(1000);
        assertThat(HeaderTag.VERSION.tag()).isEqualTo(1001);
        assertThat(HeaderTag.RELEASE.tag()).isEqualTo(1002);
        assertThat(HeaderTag.ARCH.tag()).isEqualTo(1022);
    }

    @Test
    void headerTagFromTag() {
        assertThat(HeaderTag.fromTag(1000)).hasValue(HeaderTag.NAME);
        assertThat(HeaderTag.fromTag(1001)).hasValue(HeaderTag.VERSION);
        assertThat(HeaderTag.fromTag(99999)).isEmpty();
    }
}
