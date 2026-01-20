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

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;

/**
 * An input stream that limits the number of bytes that can be read from
 * an underlying stream. This is useful for reading fixed-size sections
 * of a file without consuming more data than expected.
 */
public final class BoundedInputStream extends InputStream {

    private final @NotNull InputStream source;
    private long remaining;
    private long mark = -1;

    /**
     * Creates a new bounded input stream.
     *
     * @param source the underlying input stream
     * @param limit the maximum number of bytes to read
     * @throws IllegalArgumentException if limit is negative
     */
    public BoundedInputStream(@NotNull InputStream source, long limit) {
        this.source = source;
        if (limit < 0) {
            throw new IllegalArgumentException("limit must not be negative: " + limit);
        }
        this.remaining = limit;
    }

    @Override
    public int read() throws IOException {
        if (remaining <= 0) {
            return -1;
        }
        int result = source.read();
        if (result >= 0) {
            remaining--;
        }
        return result;
    }

    @Override
    public int read(byte @NotNull [] b, int off, int len) throws IOException {
        if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        }
        if (len == 0) {
            return 0;
        }
        if (remaining <= 0) {
            return -1;
        }
        int toRead = (int) Math.min(len, remaining);
        int read = source.read(b, off, toRead);
        if (read > 0) {
            remaining -= read;
        }
        return read;
    }

    @Override
    public long skip(long n) throws IOException {
        if (n <= 0 || remaining <= 0) {
            return 0;
        }
        long toSkip = Math.min(n, remaining);
        long skipped = source.skip(toSkip);
        remaining -= skipped;
        return skipped;
    }

    @Override
    public int available() throws IOException {
        int available = source.available();
        return (int) Math.min(available, remaining);
    }

    @Override
    public void close() throws IOException {
        // Do not close the underlying stream, just exhaust remaining bytes
        // This allows the parent stream to continue reading after this section
    }

    /**
     * Skips any remaining bytes in this bounded section.
     * This should be called to ensure the underlying stream is positioned
     * at the end of this section.
     *
     * @throws IOException if an I/O error occurs
     */
    public void skipRemaining() throws IOException {
        while (remaining > 0) {
            long skipped = source.skip(remaining);
            if (skipped <= 0) {
                // skip() returned 0, try reading
                if (source.read() < 0) {
                    break;
                }
                skipped = 1;
            }
            remaining -= skipped;
        }
    }

    @Override
    public boolean markSupported() {
        return source.markSupported();
    }

    @Override
    public synchronized void mark(int readlimit) {
        source.mark(readlimit);
        mark = remaining;
    }

    @Override
    public synchronized void reset() throws IOException {
        if (mark < 0) {
            throw new IOException("mark not set");
        }
        source.reset();
        remaining = mark;
    }

    /**
     * Returns the number of bytes remaining in this bounded section.
     *
     * @return the number of bytes remaining
     */
    public long getRemaining() {
        return remaining;
    }
}
