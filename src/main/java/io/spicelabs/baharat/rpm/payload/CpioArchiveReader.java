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
package io.spicelabs.baharat.rpm.payload;

import io.spicelabs.baharat.BaharatStreamException;
import io.spicelabs.baharat.rpm.exception.InvalidFormatException;
import io.spicelabs.baharat.rpm.io.BoundedInputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Reads CPIO archives in SVR4 "newc" format.
 * This is the format used by RPM for payload storage.
 *
 * <p>The CPIO newc header is 110 bytes of ASCII hex:
 * <pre>
 * c_magic[6]      "070701" (newc) or "070702" (CRC)
 * c_ino[8]        inode number
 * c_mode[8]       file mode
 * c_uid[8]        user ID
 * c_gid[8]        group ID
 * c_nlink[8]      number of links
 * c_mtime[8]      modification time
 * c_filesize[8]   file size
 * c_devmajor[8]   device major
 * c_devminor[8]   device minor
 * c_rdevmajor[8]  rdev major
 * c_rdevminor[8]  rdev minor
 * c_namesize[8]   filename length (including null)
 * c_check[8]      checksum (CRC format only)
 * </pre>
 *
 * <p>After the header, the filename follows (null-terminated), then the file data.
 * Both are padded to 4-byte boundaries.
 */
public final class CpioArchiveReader implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(CpioArchiveReader.class);

    private static final String MAGIC_NEWC = "070701";
    private static final String MAGIC_CRC = "070702";
    private static final String TRAILER = "TRAILER!!!";
    private static final int HEADER_SIZE = 110;

    // Security limits to prevent DoS attacks
    private static final int MAX_NAME_SIZE = 64 * 1024; // 64 KB max filename
    private static final long MAX_FILE_SIZE = 8L * 1024 * 1024 * 1024; // 8 GB max file size
    private static final int MAX_SYMLINK_SIZE = 64 * 1024; // 64 KB max symlink target

    // Mode masks
    private static final int S_IFMT = 0170000;
    private static final int S_IFLNK = 0120000;
    private static final int S_IFREG = 0100000;
    private static final int S_IFDIR = 0040000;

    private final @NotNull InputStream input;
    private boolean finished = false;
    private BoundedInputStream currentEntryStream = null;
    private long currentEntrySize = 0;

    /**
     * Creates a new CPIO archive reader.
     *
     * @param input the input stream containing the CPIO archive
     */
    public CpioArchiveReader(@NotNull InputStream input) {
        this.input = input;
        log.trace("Created CPIO archive reader");
    }

    /**
     * Reads the next entry from the archive.
     *
     * @return the next entry, or null if the end of archive is reached
     * @throws InvalidFormatException if the archive format is invalid
     * @throws IOException if an I/O error occurs
     */
    public @Nullable CpioEntry nextEntry() throws InvalidFormatException, IOException {
        if (finished) {
            return null;
        }

        // Skip any remaining data from the previous entry
        if (currentEntryStream != null) {
            currentEntryStream.skipRemaining();
            // Align to 4-byte boundary after file data
            skipDataPadding(currentEntrySize);
            currentEntryStream = null;
            currentEntrySize = 0;
        }

        // Read header
        byte[] headerBytes = new byte[HEADER_SIZE];
        int read = readFully(input, headerBytes);
        if (read < HEADER_SIZE) {
            // Strict truncation (catalog §5/§6): a CPIO archive whose header is cut off
            // is CORRUPT — the trailer entry is mandatory, so a clean end-of-stream can
            // only occur right after TRAILER!!! (handled by the `finished` flag above).
            finished = true;
            throw new InvalidFormatException(
                    "Truncated CPIO archive: expected " + HEADER_SIZE + "-byte header, got "
                            + read + " bytes");
        }

        String header = new String(headerBytes, StandardCharsets.US_ASCII);

        // Check magic
        String magic = header.substring(0, 6);
        if (!magic.equals(MAGIC_NEWC) && !magic.equals(MAGIC_CRC)) {
            throw new InvalidFormatException("Invalid CPIO magic: " + magic);
        }

        // Parse header fields
        long inode = parseHex(header, 6, 8);
        int mode = (int) parseHex(header, 14, 8);
        int uid = (int) parseHex(header, 22, 8);
        int gid = (int) parseHex(header, 30, 8);
        int nlink = (int) parseHex(header, 38, 8);
        long mtime = parseHex(header, 46, 8);
        long fileSize = parseHex(header, 54, 8);
        int devMajor = (int) parseHex(header, 62, 8);
        int devMinor = (int) parseHex(header, 70, 8);
        int rdevMajor = (int) parseHex(header, 78, 8);
        int rdevMinor = (int) parseHex(header, 86, 8);
        long nameSizeLong = parseHex(header, 94, 8);
        long check = parseHex(header, 102, 8);

        // Validate nameSize to prevent integer overflow and DoS
        if (nameSizeLong <= 0 || nameSizeLong > MAX_NAME_SIZE) {
            throw new InvalidFormatException("Invalid CPIO name size: " + nameSizeLong +
                    " (must be 1-" + MAX_NAME_SIZE + ")");
        }
        int nameSize = (int) nameSizeLong;

        // Validate fileSize
        if (fileSize < 0 || fileSize > MAX_FILE_SIZE) {
            throw new InvalidFormatException("Invalid CPIO file size: " + fileSize +
                    " (must be 0-" + MAX_FILE_SIZE + ")");
        }

        // Read filename
        byte[] nameBytes = new byte[nameSize];
        read = readFully(input, nameBytes);
        if (read < nameSize) {
            throw new InvalidFormatException("Unexpected end of CPIO archive reading filename");
        }

        // Remove null terminator
        String name = new String(nameBytes, 0, nameSize - 1, StandardCharsets.UTF_8);

        // Check for trailer
        if (name.equals(TRAILER)) {
            log.debug("Reached CPIO archive trailer");
            finished = true;
            return null;
        }

        // Align to 4-byte boundary after header + name
        int headerAndName = HEADER_SIZE + nameSize;
        int padding = (4 - (headerAndName % 4)) % 4;
        if (padding > 0) {
            skipBytes(input, padding);
        }

        // Create bounded stream for file content
        currentEntryStream = new BoundedInputStream(input, fileSize);
        currentEntrySize = fileSize;

        log.trace("Read CPIO entry: {} ({} bytes, mode {:06o})", name, fileSize, mode);

        return new CpioEntry(
                name,
                mode,
                uid,
                gid,
                nlink,
                Instant.ofEpochSecond(mtime),
                fileSize,
                devMajor,
                devMinor,
                rdevMajor,
                rdevMinor,
                inode,
                currentEntryStream
        );
    }

    /**
     * Returns a stream of all entries in this archive.
     *
     * @return a stream of CPIO entries
     */
    public @NotNull Stream<CpioEntry> stream() {
        Iterator<CpioEntry> iterator = new Iterator<>() {
            private CpioEntry next = null;
            private boolean hasNext = false;
            private boolean needsAdvance = true;

            @Override
            public boolean hasNext() {
                if (needsAdvance) {
                    advance();
                }
                return hasNext;
            }

            @Override
            public CpioEntry next() {
                if (needsAdvance) {
                    advance();
                }
                if (!hasNext) {
                    throw new NoSuchElementException();
                }
                needsAdvance = true;
                return next;
            }

            private void advance() {
                try {
                    next = nextEntry();
                    hasNext = next != null;
                    needsAdvance = false;
                } catch (IOException | InvalidFormatException e) {
                    // Stream-lambda boundary (catalog §7): checked corruption must not be
                    // wrapped in a bare RuntimeException — use the documented wrapper.
                    throw new BaharatStreamException("Error reading CPIO archive", e);
                }
            }
        };

        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED | Spliterator.NONNULL),
                false
        );
    }

    @Override
    public void close() throws IOException {
        input.close();
    }

    private long parseHex(String header, int offset, int length) throws InvalidFormatException {
        try {
            return Long.parseLong(header.substring(offset, offset + length), 16);
        } catch (NumberFormatException e) {
            throw new InvalidFormatException("Invalid hex in CPIO header at offset " + offset, e);
        }
    }

    private int readFully(InputStream in, byte[] buffer) throws IOException {
        int offset = 0;
        while (offset < buffer.length) {
            int read = in.read(buffer, offset, buffer.length - offset);
            if (read <= 0) {
                // read == 0 is no-progress (catalog §5): break so the caller's short-read
                // check reports truncation instead of spinning forever.
                break;
            }
            offset += read;
        }
        return offset;
    }

    private void skipBytes(InputStream in, int count) throws IOException {
        int remaining = count;
        while (remaining > 0) {
            long skipped = in.skip(remaining);
            if (skipped <= 0) {
                if (in.read() < 0) {
                    // Mid-skip EOF misaligns every subsequent parse — loud error instead
                    // of silently producing garbage (catalog §5/§6).
                    throw new IOException(
                            "Truncated CPIO archive: unexpected end of stream while skipping "
                                    + count + " padding bytes");
                }
                skipped = 1;
            }
            remaining -= skipped;
        }
    }

    private void skipDataPadding(long dataSize) throws IOException {
        // After file data, align to 4-byte boundary
        if (dataSize > 0) {
            int padding = (int) ((4 - (dataSize % 4)) % 4);
            if (padding > 0) {
                skipBytes(input, padding);
            }
        }
    }

    /**
     * Represents a single entry in a CPIO archive.
     */
    public record CpioEntry(
            @NotNull String name,
            int mode,
            int uid,
            int gid,
            int nlink,
            @NotNull Instant mtime,
            long size,
            int devMajor,
            int devMinor,
            int rdevMajor,
            int rdevMinor,
            long inode,
            @NotNull InputStream dataStream
    ) {
        /**
         * Returns true if this is a regular file.
         */
        public boolean isFile() {
            return (mode & S_IFMT) == S_IFREG;
        }

        /**
         * Returns true if this is a directory.
         */
        public boolean isDirectory() {
            return (mode & S_IFMT) == S_IFDIR;
        }

        /**
         * Returns true if this is a symbolic link.
         */
        public boolean isSymlink() {
            return (mode & S_IFMT) == S_IFLNK;
        }

        /**
         * Returns the permission bits.
         */
        public int permissions() {
            return mode & 07777;
        }

        /**
         * Reads the symlink target (only valid for symlinks).
         *
         * @throws IOException if an I/O error occurs
         * @throws IllegalStateException if this entry is not a symlink or the target is too large
         */
        public @NotNull String readLinkTarget() throws IOException {
            if (!isSymlink()) {
                throw new IllegalStateException("Not a symlink");
            }
            if (size > MAX_SYMLINK_SIZE) {
                throw new IllegalStateException("Symlink target too large: " + size +
                        " (max " + MAX_SYMLINK_SIZE + ")");
            }
            byte[] data = dataStream.readNBytes((int) size);
            return new String(data, StandardCharsets.UTF_8);
        }
    }
}
