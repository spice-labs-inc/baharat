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
package io.spicelabs.baharat.rpm.io;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.EOFException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BinaryReaderTest {

    @Test
    void readsUnsignedByte() throws Exception {
        byte[] data = {(byte) 0xFF, 0x00, 0x7F};
        BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data));

        assertThat(reader.readUnsignedByte()).isEqualTo(255);
        assertThat(reader.readUnsignedByte()).isEqualTo(0);
        assertThat(reader.readUnsignedByte()).isEqualTo(127);
        assertThat(reader.position()).isEqualTo(3);
    }

    @Test
    void readsSignedByte() throws Exception {
        byte[] data = {(byte) 0xFF, 0x00, 0x7F};
        BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data));

        assertThat(reader.readByte()).isEqualTo((byte) -1);
        assertThat(reader.readByte()).isEqualTo((byte) 0);
        assertThat(reader.readByte()).isEqualTo((byte) 127);
    }

    @Test
    void readsUnsignedShort() throws Exception {
        byte[] data = {(byte) 0xFF, (byte) 0xFF, 0x00, 0x01};
        BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data));

        assertThat(reader.readUnsignedShort()).isEqualTo(65535);
        assertThat(reader.readUnsignedShort()).isEqualTo(1);
        assertThat(reader.position()).isEqualTo(4);
    }

    @Test
    void readsSignedShort() throws Exception {
        byte[] data = {(byte) 0xFF, (byte) 0xFF, 0x00, 0x01};
        BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data));

        assertThat(reader.readShort()).isEqualTo((short) -1);
        assertThat(reader.readShort()).isEqualTo((short) 1);
    }

    @Test
    void readsUnsignedInt() throws Exception {
        byte[] data = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data));

        assertThat(reader.readUnsignedInt()).isEqualTo(4294967295L);
    }

    @Test
    void readsSignedInt() throws Exception {
        byte[] data = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x00, 0x00, 0x00, 0x01};
        BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data));

        assertThat(reader.readInt()).isEqualTo(-1);
        assertThat(reader.readInt()).isEqualTo(1);
    }

    @Test
    void readsLong() throws Exception {
        byte[] data = {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01};
        BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data));

        assertThat(reader.readLong()).isEqualTo(1L);
    }

    @Test
    void readsBytes() throws Exception {
        byte[] data = {0x01, 0x02, 0x03, 0x04, 0x05};
        BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data));

        byte[] result = reader.readBytes(3);
        assertThat(result).containsExactly(0x01, 0x02, 0x03);
        assertThat(reader.position()).isEqualTo(3);
    }

    @Test
    void readsBytesZeroLength() throws Exception {
        byte[] data = {0x01, 0x02, 0x03};
        BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data));

        byte[] result = reader.readBytes(0);
        assertThat(result).isEmpty();
        assertThat(reader.position()).isEqualTo(0);
    }

    @Test
    void readBytesRejectsNegativeLength() {
        BinaryReader reader = new BinaryReader(new ByteArrayInputStream(new byte[0]));

        assertThatThrownBy(() -> reader.readBytes(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void readsNullTerminatedStringWithMaxLength() throws Exception {
        byte[] data = {'H', 'e', 'l', 'l', 'o', 0, 'X', 'X'};
        BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data));

        String result = reader.readNullTerminatedString(8);
        assertThat(result).isEqualTo("Hello");
        assertThat(reader.position()).isEqualTo(8); // Full length consumed
    }

    @Test
    void readsNullTerminatedStringWithoutMaxLength() throws Exception {
        byte[] data = {'H', 'i', 0, 'X'};
        BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data));

        String result = reader.readNullTerminatedString();
        assertThat(result).isEqualTo("Hi");
        assertThat(reader.position()).isEqualTo(3); // Including null terminator
    }

    @Test
    void skipsBytes() throws Exception {
        byte[] data = {0x01, 0x02, 0x03, 0x04, 0x05};
        BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data));

        reader.skip(3);
        assertThat(reader.position()).isEqualTo(3);
        assertThat(reader.readUnsignedByte()).isEqualTo(4);
    }

    @Test
    void skipRejectsNegativeCount() {
        BinaryReader reader = new BinaryReader(new ByteArrayInputStream(new byte[0]));

        assertThatThrownBy(() -> reader.skip(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void alignsToFourByteBoundary() throws Exception {
        byte[] data = new byte[10];
        BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data));

        reader.readBytes(1);
        assertThat(reader.position()).isEqualTo(1);

        reader.align(4);
        assertThat(reader.position()).isEqualTo(4);
    }

    @Test
    void alignDoesNothingWhenAlreadyAligned() throws Exception {
        byte[] data = new byte[10];
        BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data));

        reader.readBytes(4);
        reader.align(4);
        assertThat(reader.position()).isEqualTo(4);
    }

    @Test
    void alignRejectsInvalidAlignment() {
        BinaryReader reader = new BinaryReader(new ByteArrayInputStream(new byte[0]));

        assertThatThrownBy(() -> reader.align(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reader.align(3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reader.align(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsEOFExceptionWhenNotEnoughData() {
        byte[] data = {0x01};
        BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data));

        assertThatThrownBy(() -> reader.readBytes(5))
                .isInstanceOf(EOFException.class);
    }

    @Test
    void throwsEOFExceptionOnEmptyStream() {
        BinaryReader reader = new BinaryReader(new ByteArrayInputStream(new byte[0]));

        assertThatThrownBy(reader::readUnsignedByte)
                .isInstanceOf(EOFException.class);
    }

    @Test
    void throwsEOFExceptionOnSkipPastEnd() {
        byte[] data = {0x01, 0x02};
        BinaryReader reader = new BinaryReader(new ByteArrayInputStream(data));

        assertThatThrownBy(() -> reader.skip(10))
                .isInstanceOf(EOFException.class);
    }

    @Test
    void returnsInputStream() {
        ByteArrayInputStream stream = new ByteArrayInputStream(new byte[0]);
        BinaryReader reader = new BinaryReader(stream);

        assertThat(reader.getInputStream()).isSameAs(stream);
    }

}
