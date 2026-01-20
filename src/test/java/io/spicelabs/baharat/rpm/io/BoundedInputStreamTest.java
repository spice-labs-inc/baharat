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
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoundedInputStreamTest {

    @Test
    void readsSingleBytesUpToLimit() throws Exception {
        byte[] data = {1, 2, 3, 4, 5};
        BoundedInputStream stream = new BoundedInputStream(new ByteArrayInputStream(data), 3);

        assertThat(stream.read()).isEqualTo(1);
        assertThat(stream.read()).isEqualTo(2);
        assertThat(stream.read()).isEqualTo(3);
        assertThat(stream.read()).isEqualTo(-1); // Limit reached
        assertThat(stream.getRemaining()).isEqualTo(0);
    }

    @Test
    void readsByteArrayUpToLimit() throws Exception {
        byte[] data = {1, 2, 3, 4, 5};
        BoundedInputStream stream = new BoundedInputStream(new ByteArrayInputStream(data), 3);

        byte[] buffer = new byte[10];
        int read = stream.read(buffer, 0, 10);

        assertThat(read).isEqualTo(3);
        assertThat(buffer[0]).isEqualTo((byte) 1);
        assertThat(buffer[1]).isEqualTo((byte) 2);
        assertThat(buffer[2]).isEqualTo((byte) 3);
    }

    @Test
    void readReturnsMinusOneAfterLimit() throws Exception {
        byte[] data = {1, 2, 3};
        BoundedInputStream stream = new BoundedInputStream(new ByteArrayInputStream(data), 2);

        stream.read(new byte[2], 0, 2);
        int read = stream.read(new byte[1], 0, 1);

        assertThat(read).isEqualTo(-1);
    }

    @Test
    void readZeroBytesReturnsZero() throws Exception {
        byte[] data = {1, 2, 3};
        BoundedInputStream stream = new BoundedInputStream(new ByteArrayInputStream(data), 3);

        int read = stream.read(new byte[10], 0, 0);
        assertThat(read).isEqualTo(0);
    }

    @Test
    void skipsUpToLimit() throws Exception {
        byte[] data = {1, 2, 3, 4, 5};
        BoundedInputStream stream = new BoundedInputStream(new ByteArrayInputStream(data), 3);

        long skipped = stream.skip(10);
        assertThat(skipped).isEqualTo(3);
        assertThat(stream.getRemaining()).isEqualTo(0);
    }

    @Test
    void skipZeroOrNegativeReturnsZero() throws Exception {
        byte[] data = {1, 2, 3};
        BoundedInputStream stream = new BoundedInputStream(new ByteArrayInputStream(data), 3);

        assertThat(stream.skip(0)).isEqualTo(0);
        assertThat(stream.skip(-1)).isEqualTo(0);
    }

    @Test
    void availableReturnsMinimum() throws Exception {
        byte[] data = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        BoundedInputStream stream = new BoundedInputStream(new ByteArrayInputStream(data), 3);

        assertThat(stream.available()).isEqualTo(3);
    }

    @Test
    void closeDoesNotCloseUnderlying() throws Exception {
        byte[] data = {1, 2, 3, 4, 5};
        ByteArrayInputStream underlying = new ByteArrayInputStream(data);
        BoundedInputStream stream = new BoundedInputStream(underlying, 2);

        stream.read();
        stream.read();
        stream.close();

        // Underlying stream should still work
        assertThat(underlying.read()).isEqualTo(3);
    }

    @Test
    void skipRemainingExhaustsStream() throws Exception {
        byte[] data = {1, 2, 3, 4, 5};
        ByteArrayInputStream underlying = new ByteArrayInputStream(data);
        BoundedInputStream stream = new BoundedInputStream(underlying, 3);

        stream.read(); // Read 1 byte
        stream.skipRemaining();

        assertThat(stream.getRemaining()).isEqualTo(0);
        // Underlying stream should be at position 3
        assertThat(underlying.read()).isEqualTo(4);
    }

    @Test
    void markAndResetWork() throws Exception {
        byte[] data = {1, 2, 3, 4, 5};
        ByteArrayInputStream underlying = new ByteArrayInputStream(data);
        BoundedInputStream stream = new BoundedInputStream(underlying, 5);

        assertThat(stream.markSupported()).isTrue();

        stream.read(); // 1
        stream.mark(10);
        stream.read(); // 2
        stream.read(); // 3
        stream.reset();

        assertThat(stream.read()).isEqualTo(2);
    }

    @Test
    void resetWithoutMarkThrows() throws Exception {
        byte[] data = {1, 2, 3};
        BoundedInputStream stream = new BoundedInputStream(new ByteArrayInputStream(data), 3);

        assertThatThrownBy(stream::reset)
                .isInstanceOf(IOException.class)
                .hasMessageContaining("mark not set");
    }

    @Test
    void rejectsNegativeLimit() {
        assertThatThrownBy(() -> new BoundedInputStream(new ByteArrayInputStream(new byte[0]), -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void readWithNullBufferThrows() throws Exception {
        BoundedInputStream stream = new BoundedInputStream(new ByteArrayInputStream(new byte[10]), 5);

        assertThatThrownBy(() -> stream.read(null, 0, 5))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void readWithInvalidOffsetsThrows() throws Exception {
        BoundedInputStream stream = new BoundedInputStream(new ByteArrayInputStream(new byte[10]), 5);
        byte[] buffer = new byte[5];

        assertThatThrownBy(() -> stream.read(buffer, -1, 3))
                .isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> stream.read(buffer, 0, -1))
                .isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> stream.read(buffer, 3, 5))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void zeroLimitReturnsEofImmediately() throws Exception {
        byte[] data = {1, 2, 3};
        BoundedInputStream stream = new BoundedInputStream(new ByteArrayInputStream(data), 0);

        assertThat(stream.read()).isEqualTo(-1);
        assertThat(stream.read(new byte[1], 0, 1)).isEqualTo(-1);
    }
}
