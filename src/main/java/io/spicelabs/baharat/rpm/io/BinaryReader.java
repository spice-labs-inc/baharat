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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Utility class for reading big-endian binary data from an input stream.
 *
 * <p>RPM files use big-endian (network) byte order for all multi-byte integers.
 * This class provides methods to read integers of various sizes and tracks
 * the current position in the stream for alignment and error reporting.
 *
 * <p>All read methods throw {@link java.io.EOFException} if the end of stream
 * is reached before the required number of bytes can be read.
 *
 * @see BoundedInputStream
 */
public final class BinaryReader {

    private static final Logger log = LoggerFactory.getLogger(BinaryReader.class);

    private final @NotNull InputStream input;
    private long position;

    /**
     * Creates a new binary reader wrapping the given input stream.
     *
     * @param input the input stream to read from
     */
    public BinaryReader(@NotNull InputStream input) {
        this.input = input;
        this.position = 0;
        log.trace("Created BinaryReader wrapping {}", input.getClass().getSimpleName());
    }

    /**
     * Returns the current read position in bytes from the start.
     *
     * @return the current position
     */
    public long position() {
        return position;
    }

    /**
     * Reads a single unsigned byte.
     *
     * @return the byte value (0-255)
     * @throws IOException if an I/O error occurs
     * @throws EOFException if end of stream is reached
     */
    public int readUnsignedByte() throws IOException {
        int b = input.read();
        if (b < 0) {
            throw new EOFException("Unexpected end of stream at position " + position);
        }
        position++;
        return b;
    }

    /**
     * Reads a signed byte.
     *
     * @return the byte value (-128 to 127)
     * @throws IOException if an I/O error occurs
     * @throws EOFException if end of stream is reached
     */
    public byte readByte() throws IOException {
        return (byte) readUnsignedByte();
    }

    /**
     * Reads a big-endian unsigned 16-bit integer.
     *
     * @return the value (0 to 65535)
     * @throws IOException if an I/O error occurs
     * @throws EOFException if end of stream is reached
     */
    public int readUnsignedShort() throws IOException {
        int b1 = readUnsignedByte();  // High byte (most significant)
        int b2 = readUnsignedByte();  // Low byte (least significant)
        return (b1 << 8) | b2;
    }

    /**
     * Reads a big-endian signed 16-bit integer.
     *
     * @return the value (-32768 to 32767)
     * @throws IOException if an I/O error occurs
     * @throws EOFException if end of stream is reached
     */
    public short readShort() throws IOException {
        return (short) readUnsignedShort();
    }

    /**
     * Reads a big-endian unsigned 32-bit integer.
     *
     * @return the value as a long (0 to 4294967295)
     * @throws IOException if an I/O error occurs
     * @throws EOFException if end of stream is reached
     */
    public long readUnsignedInt() throws IOException {
        // Read bytes in big-endian order (most significant byte first)
        int b1 = readUnsignedByte();
        int b2 = readUnsignedByte();
        int b3 = readUnsignedByte();
        int b4 = readUnsignedByte();
        // Cast to long before shifting to avoid sign extension issues
        return ((long) b1 << 24) | ((long) b2 << 16) | ((long) b3 << 8) | b4;
    }

    /**
     * Reads a big-endian signed 32-bit integer.
     *
     * @return the value
     * @throws IOException if an I/O error occurs
     * @throws EOFException if end of stream is reached
     */
    public int readInt() throws IOException {
        return (int) readUnsignedInt();
    }

    /**
     * Reads a big-endian signed 64-bit integer.
     *
     * @return the value
     * @throws IOException if an I/O error occurs
     * @throws EOFException if end of stream is reached
     */
    public long readLong() throws IOException {
        long b1 = readUnsignedByte();
        long b2 = readUnsignedByte();
        long b3 = readUnsignedByte();
        long b4 = readUnsignedByte();
        long b5 = readUnsignedByte();
        long b6 = readUnsignedByte();
        long b7 = readUnsignedByte();
        long b8 = readUnsignedByte();
        return (b1 << 56) | (b2 << 48) | (b3 << 40) | (b4 << 32) |
               (b5 << 24) | (b6 << 16) | (b7 << 8) | b8;
    }

    /**
     * Reads exactly the specified number of bytes.
     *
     * @param length the number of bytes to read
     * @return a byte array containing the read bytes
     * @throws IOException if an I/O error occurs
     * @throws EOFException if end of stream is reached before reading all bytes
     * @throws IllegalArgumentException if length is negative
     */
    public byte @NotNull [] readBytes(int length) throws IOException {
        if (length < 0) {
            throw new IllegalArgumentException("length must not be negative: " + length);
        }
        if (length == 0) {
            return new byte[0];
        }
        byte[] buffer = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = input.read(buffer, offset, length - offset);
            if (read < 0) {
                throw new EOFException("Unexpected end of stream at position " + position +
                        ", expected " + length + " bytes but got " + offset);
            }
            offset += read;
            position += read;
        }
        return buffer;
    }

    /**
     * Reads a null-terminated ASCII string with a maximum length.
     * The full length is always consumed from the stream, even if
     * the null terminator appears earlier.
     *
     * @param maxLength the maximum length including null terminator
     * @return the string (without null terminator)
     * @throws IOException if an I/O error occurs
     */
    public @NotNull String readNullTerminatedString(int maxLength) throws IOException {
        byte[] buffer = readBytes(maxLength);
        int nullIndex = 0;
        while (nullIndex < buffer.length && buffer[nullIndex] != 0) {
            nullIndex++;
        }
        return new String(buffer, 0, nullIndex, StandardCharsets.US_ASCII);
    }

    /**
     * Reads a null-terminated string from the current position.
     * Reads bytes until a null byte is encountered.
     *
     * @return the string (without null terminator)
     * @throws IOException if an I/O error occurs
     */
    public @NotNull String readNullTerminatedString() throws IOException {
        StringBuilder sb = new StringBuilder();
        int b;
        while ((b = readUnsignedByte()) != 0) {
            sb.append((char) b);
        }
        return sb.toString();
    }

    /**
     * Skips exactly the specified number of bytes.
     *
     * @param count the number of bytes to skip
     * @throws IOException if an I/O error occurs
     * @throws EOFException if end of stream is reached before skipping all bytes
     * @throws IllegalArgumentException if count is negative
     */
    public void skip(long count) throws IOException {
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative: " + count);
        }
        long remaining = count;
        while (remaining > 0) {
            long skipped = input.skip(remaining);
            if (skipped <= 0) {
                // skip() returned 0, try reading a byte
                if (input.read() < 0) {
                    throw new EOFException("Unexpected end of stream at position " + position +
                            ", needed to skip " + count + " bytes but only skipped " + (count - remaining));
                }
                skipped = 1;
            }
            remaining -= skipped;
            position += skipped;
        }
    }

    /**
     * Aligns the current position to the specified boundary by skipping bytes.
     *
     * @param alignment the alignment boundary (must be a power of 2)
     * @throws IOException if an I/O error occurs
     * @throws IllegalArgumentException if alignment is not a positive power of 2
     */
    public void align(int alignment) throws IOException {
        if (alignment <= 0 || (alignment & (alignment - 1)) != 0) {
            throw new IllegalArgumentException("alignment must be a positive power of 2: " + alignment);
        }
        long remainder = position % alignment;
        if (remainder != 0) {
            long skipBytes = alignment - remainder;
            log.trace("Aligning to {}-byte boundary at position {}, skipping {} bytes",
                    alignment, position, skipBytes);
            skip(skipBytes);
        }
    }

    /**
     * Returns the underlying input stream.
     *
     * @return the input stream
     */
    public @NotNull InputStream getInputStream() {
        return input;
    }
}
