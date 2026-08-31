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
package io.spicelabs.baharat.common;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;

/**
 * An {@link InputStream} that counts decompressed bytes and FAILS LOUDLY (IOException) when
 * a budget is exceeded.
 *
 * <p>Place it between a decompressor and an archive/metadata reader so every decompressed
 * byte — including bytes consumed by tar-skip/header reads — counts against the budget.
 * A budget trip must never silently truncate an archive.
 *
 * <p>{@code skip()} is counted read-and-discard so it cannot bypass the budget; a
 * no-progress delegate fails loudly instead of spinning.
 */
public final class CountedLimitedInputStream extends InputStream {

    private final InputStream delegate;
    private final long cap;
    private final String what;
    private long count;
    private boolean exceeded;

    public CountedLimitedInputStream(@NotNull InputStream delegate, long cap, @NotNull String what) {
        this.delegate = delegate;
        this.cap = cap;
        this.what = what;
    }

    @Override
    public int read() throws IOException {
        if (exceeded) {
            return -1;
        }
        int b = delegate.read();
        if (b != -1) {
            count++;
            check();
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        }
        if (len == 0) {
            return 0;
        }
        if (exceeded) {
            return -1;
        }
        int r = delegate.read(b, off, len);
        if (r > 0) {
            count += r;
            check();
        }
        return r;
    }

    @Override
    public long skip(long n) throws IOException {
        if (exceeded || n <= 0) {
            return 0;
        }
        long skipped = 0;
        byte[] scratch = new byte[8192];
        while (skipped < n) {
            int toRead = (int) Math.min(scratch.length, n - skipped);
            int r = delegate.read(scratch, 0, toRead);
            if (r < 0) {
                break;
            }
            if (r == 0) {
                throw new IOException("No progress reading " + what);
            }
            skipped += r;
            count += r;
            check();
        }
        return skipped;
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    private void check() throws IOException {
        if (count > cap) {
            exceeded = true;
            throw new IOException("Decompressed data exceeds size limit for " + what
                    + ": " + count + " > " + cap + " bytes");
        }
    }

    /** True once the budget has been exceeded. */
    public boolean exceeded() {
        return exceeded;
    }

    /** Total bytes pulled from the delegate. */
    public long count() {
        return count;
    }
}
