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
package io.spicelabs.baharat.deb;

import io.spicelabs.baharat.PackageException;
import io.spicelabs.baharat.PackageFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Reader for ar archives (Unix archive format).
 *
 * <p>The ar format is used by Debian packages (.deb files).
 *
 * <h2>Archive Structure</h2>
 * <pre>
 * +------------------+
 * | Global header    |  8 bytes: "!&lt;arch&gt;\n"
 * +------------------+
 * | File header 1    |  60 bytes
 * +------------------+
 * | File content 1   |  variable (padded to even length)
 * +------------------+
 * | File header 2    |  60 bytes
 * +------------------+
 * | ...              |
 * +------------------+
 * </pre>
 *
 * <h2>File Header Format (60 bytes)</h2>
 * <pre>
 * Name:      16 bytes (space-padded)
 * Timestamp: 12 bytes (decimal seconds)
 * Owner ID:   6 bytes (decimal)
 * Group ID:   6 bytes (decimal)
 * Mode:       8 bytes (octal)
 * Size:      10 bytes (decimal)
 * Magic:      2 bytes ("`\n")
 * </pre>
 */
public final class ArArchiveReader implements AutoCloseable {

    private static final byte[] AR_MAGIC = "!<arch>\n".getBytes(StandardCharsets.US_ASCII);
    private static final int HEADER_SIZE = 60;
    private static final byte[] ENTRY_MAGIC = {0x60, 0x0A}; // "`\n"

    private final @NotNull InputStream input;
    private boolean headerRead = false;
    private long currentEntryRemaining = 0;

    /**
     * Creates an ar archive reader.
     *
     * @param input the input stream
     */
    public ArArchiveReader(@NotNull InputStream input) {
        this.input = input;
    }

    /**
     * Reads and validates the archive header.
     *
     * @throws IOException if an I/O error occurs
     * @throws PackageException if the archive is invalid
     */
    public void readHeader() throws IOException, PackageException {
        if (headerRead) {
            return;
        }

        byte[] magic = new byte[AR_MAGIC.length];
        int read = input.read(magic);
        if (read != AR_MAGIC.length) {
            throw new PackageException.InvalidPackageException("File too small to be ar archive");
        }

        for (int i = 0; i < AR_MAGIC.length; i++) {
            if (magic[i] != AR_MAGIC[i]) {
                throw new PackageException.InvalidPackageException("Invalid ar archive magic");
            }
        }

        headerRead = true;
    }

    /**
     * Reads the next entry header.
     *
     * @return the entry, or null if at end of archive
     * @throws IOException if an I/O error occurs
     * @throws PackageException if the entry header is invalid
     */
    public @Nullable ArEntry nextEntry() throws IOException, PackageException {
        if (!headerRead) {
            readHeader();
        }

        // Skip any remaining data from previous entry
        skipCurrentEntry();

        // Read entry header
        byte[] header = new byte[HEADER_SIZE];
        int read = input.read(header);
        if (read < 0) {
            return null; // Clean end of archive
        }
        if (read == 0) {
            // No-progress stream (catalog §5): fail loud, never spin or misparse.
            throw new PackageException.InvalidPackageException(
                    "No progress reading ar entry header", PackageFormat.DEB);
        }
        if (read != HEADER_SIZE) {
            throw new PackageException.InvalidPackageException(
                    "Truncated ar entry header: expected " + HEADER_SIZE + ", got " + read,
                    PackageFormat.DEB);
        }

        // Validate magic
        if (header[58] != ENTRY_MAGIC[0] || header[59] != ENTRY_MAGIC[1]) {
            throw new PackageException.InvalidPackageException(
                    "Invalid ar entry magic",
                    PackageFormat.DEB);
        }

        // Parse header fields
        String name = new String(header, 0, 16, StandardCharsets.US_ASCII).trim();
        long timestamp = parseLong(header, 16, 12);
        int ownerId = (int) parseLong(header, 28, 6);
        int groupId = (int) parseLong(header, 34, 6);
        int mode = (int) parseOctal(header, 40, 8);
        long size = parseLong(header, 48, 10);
        if (size < 0) {
            // Negative size would make currentEntryRemaining negative and silently skip
            // all content (catalog §4/§6).
            throw new PackageException.InvalidPackageException(
                    "Negative ar entry size: " + size, PackageFormat.DEB);
        }

        // Handle BSD-style long filenames (name starts with #1/)
        if (name.startsWith("#1/")) {
            int nameLen;
            try {
                nameLen = Integer.parseInt(name.substring(3).trim());
            } catch (NumberFormatException e) {
                throw new PackageException.InvalidPackageException(
                        "Invalid BSD-style filename length: " + name.substring(3),
                        PackageFormat.DEB);
            }

            // Security: Bounds checking to prevent negative values, excessive allocations, and underflow
            if (nameLen < 0) {
                throw new PackageException.InvalidPackageException(
                        "Negative BSD-style filename length: " + nameLen,
                        PackageFormat.DEB);
            }
            if (nameLen > size) {
                throw new PackageException.InvalidPackageException(
                        "BSD-style filename length (" + nameLen + ") exceeds entry size (" + size + ")",
                        PackageFormat.DEB);
            }
            // Reasonable upper bound for filename length (4KB should be more than enough)
            if (nameLen > 4096) {
                throw new PackageException.InvalidPackageException(
                        "BSD-style filename length exceeds maximum (4096): " + nameLen,
                        PackageFormat.DEB);
            }

            byte[] nameBytes = new byte[nameLen];
            if (input.read(nameBytes) != nameLen) {
                throw new PackageException.InvalidPackageException(
                        "Truncated BSD-style filename",
                        PackageFormat.DEB);
            }
            name = new String(nameBytes, StandardCharsets.UTF_8).trim();
            // Filename is part of the content, so reduce size
            size -= nameLen;
        }

        // Handle GNU-style long filenames (name ends with /)
        if (name.endsWith("/")) {
            name = name.substring(0, name.length() - 1);
        }

        currentEntryRemaining = size;
        if (size % 2 != 0) {
            currentEntryRemaining++; // Account for padding byte
        }

        return new ArEntry(name, size, timestamp, ownerId, groupId, mode);
    }

    /**
     * Returns an input stream for reading the current entry's content.
     * Must be called after nextEntry() and before calling nextEntry() again.
     *
     * @param entry the current entry
     * @return the content input stream
     */
    public @NotNull InputStream getEntryInputStream(@NotNull ArEntry entry) {
        return new TrackingBoundedInputStream(input, entry.size());
    }

    /**
     * Skips the current entry's remaining content.
     *
     * @throws IOException if an I/O error occurs
     */
    public void skipCurrentEntry() throws IOException {
        while (currentEntryRemaining > 0) {
            long skipped = input.skip(currentEntryRemaining);
            if (skipped <= 0) {
                // Read and discard if skip doesn't work
                int toRead = (int) Math.min(currentEntryRemaining, 8192);
                byte[] buf = new byte[toRead];
                int read = input.read(buf);
                if (read < 0) {
                    // Premature EOF mid-member misaligns the archive (catalog §5/§6) —
                    // loud instead of silently producing garbage later.
                    throw new IOException("Truncated ar archive: unexpected end of stream "
                            + "with " + currentEntryRemaining + " bytes remaining");
                }
                if (read == 0) {
                    throw new IOException("No progress skipping ar member content");
                }
                currentEntryRemaining -= read;
            } else {
                currentEntryRemaining -= skipped;
            }
        }
        currentEntryRemaining = 0;
    }

    private static long parseLong(byte[] data, int offset, int length) throws PackageException {
        String str = new String(data, offset, length, StandardCharsets.US_ASCII).trim();
        if (str.isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(str);
        } catch (NumberFormatException e) {
            // Hostile non-numeric field: checked PackageException, never an unchecked
            // NumberFormatException escaping the IOException/PackageException contract
            // (catalog §7).
            throw new PackageException.InvalidPackageException(
                    "Invalid numeric ar field: '" + str + "'", PackageFormat.DEB, e);
        }
    }

    private static long parseOctal(byte[] data, int offset, int length) throws PackageException {
        String str = new String(data, offset, length, StandardCharsets.US_ASCII).trim();
        if (str.isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(str, 8);
        } catch (NumberFormatException e) {
            throw new PackageException.InvalidPackageException(
                    "Invalid octal ar field: '" + str + "'", PackageFormat.DEB, e);
        }
    }

    @Override
    public void close() throws IOException {
        input.close();
    }

    /**
     * Represents an entry in an ar archive.
     */
    public record ArEntry(
            @NotNull String name,
            long size,
            long timestamp,
            int ownerId,
            int groupId,
            int mode
    ) {
    }

    /**
     * Input stream that reads only a limited number of bytes and tracks bytes
     * consumed to update the parent ArArchiveReader's currentEntryRemaining.
     */
    private class TrackingBoundedInputStream extends InputStream {
        private final InputStream delegate;
        private long remaining;

        TrackingBoundedInputStream(InputStream delegate, long size) {
            this.delegate = delegate;
            this.remaining = size;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int b = delegate.read();
            if (b >= 0) {
                remaining--;
                currentEntryRemaining--;
            } else {
                throwTruncated();
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int toRead = (int) Math.min(len, remaining);
            int read = delegate.read(b, off, toRead);
            if (read > 0) {
                remaining -= read;
                currentEntryRemaining -= read;
            } else if (read < 0) {
                throwTruncated();
            }
            return read;
        }

        /** Truncated member content must surface loudly (catalog §6). */
        private void throwTruncated() throws IOException {
            throw new IOException("Truncated ar member: " + remaining + " bytes missing");
        }

        @Override
        public long skip(long n) throws IOException {
            long toSkip = Math.min(n, remaining);
            long skipped = delegate.skip(toSkip);
            remaining -= skipped;
            currentEntryRemaining -= skipped;
            return skipped;
        }

        @Override
        public int available() throws IOException {
            return (int) Math.min(delegate.available(), remaining);
        }
    }
}
