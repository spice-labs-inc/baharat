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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Header-parser allocation caps.
 *
 * <p>Uses the package-private capped overload so the boundaries are exercised with small
 * injected values (the production caps are 100k entries / 64 MiB).
 */
class HeaderParserCapTest {

    /** 16-byte header intro only — rejection happens BEFORE index/data are read, so
     *  rejection fixtures never need to materialize the (hostile-sized) index table. */
    private byte[] introBytes(int entryCount, int dataSize) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{(byte) 0x8E, (byte) 0xAD, (byte) 0xE8, 0x01}); // magic
        out.write(new byte[4]);                                     // reserved
        out.write(intToBytes(entryCount));
        out.write(intToBytes(dataSize));
        return out.toByteArray();
    }

    private byte[] fullHeaderBytes(int entryCount, int dataSize) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(introBytes(entryCount, dataSize));
        for (int i = 0; i < entryCount; i++) {
            out.write(new byte[16]); // index entries
        }
        out.write(new byte[dataSize]); // data store
        return out.toByteArray();
    }

    private static byte[] intToBytes(int v) {
        return new byte[]{(byte) (v >> 24), (byte) (v >> 16), (byte) (v >> 8), (byte) v};
    }


    // Theory: entryCount and dataSize are 4-byte hostile fields; they must be rejected
    //         BEFORE `new ArrayList<>(entryCount)` / `readBytes(dataSize)` allocate.
    // Boundaries: cap−1 / cap / cap+1 for both fields.
    // Revert-check: removing the caps makes cap+1 parse (and at production magnitudes OOM).
    @Test
    void entryCountOverCapRejected() throws IOException, InvalidFormatException {
        assertThatThrownBy(() -> HeaderParser.parse(
                new BinaryReader(new ByteArrayInputStream(introBytes(11, 0))), false, 10, 1024))
                .isInstanceOf(InvalidFormatException.class)
                .hasMessageContaining("entry count");
        assertThat(HeaderParser.parse(
                new BinaryReader(new ByteArrayInputStream(fullHeaderBytes(10, 0))), false, 10, 1024))
                .isNotNull();
    }

    @Test
    void dataSizeOverCapRejected() throws IOException, InvalidFormatException {
        assertThatThrownBy(() -> HeaderParser.parse(
                new BinaryReader(new ByteArrayInputStream(introBytes(0, 1025))), false, 10, 1024))
                .isInstanceOf(InvalidFormatException.class)
                .hasMessageContaining("data size");
        assertThat(HeaderParser.parse(
                new BinaryReader(new ByteArrayInputStream(fullHeaderBytes(0, 1024))), false, 10, 1024))
                .isNotNull();
    }

    @Test
    void negativeCountsRejected() throws IOException, InvalidFormatException {
        assertThatThrownBy(() -> HeaderParser.parse(
                new BinaryReader(new ByteArrayInputStream(introBytes(-1, 0))), false, 10, 1024))
                .isInstanceOf(InvalidFormatException.class);
        assertThatThrownBy(() -> HeaderParser.parse(
                new BinaryReader(new ByteArrayInputStream(introBytes(0, -1))), false, 10, 1024))
                .isInstanceOf(InvalidFormatException.class);
    }


    // Theory: Integer.MAX_VALUE entryCount would overflow index arithmetic and allocate;
    //         it must be rejected loudly from the 16-byte intro alone.
    @Test
    void maxValueEntryCountRejected() throws IOException, InvalidFormatException {
        assertThatThrownBy(() -> HeaderParser.parse(
                new BinaryReader(new ByteArrayInputStream(introBytes(Integer.MAX_VALUE, 0))),
                false, HeaderParser.MAX_ENTRY_COUNT, HeaderParser.MAX_DATA_SIZE))
                .isInstanceOf(InvalidFormatException.class);
    }
}
