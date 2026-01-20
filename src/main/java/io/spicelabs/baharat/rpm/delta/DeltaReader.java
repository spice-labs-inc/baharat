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
package io.spicelabs.baharat.rpm.delta;

import io.spicelabs.baharat.rpm.exception.InvalidFormatException;
import io.spicelabs.baharat.rpm.exception.UnsupportedFormatException;
import io.spicelabs.baharat.rpm.io.BinaryReader;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Reads Delta RPM (DRPM) files.
 *
 * <p>Delta RPMs have a different structure than regular RPMs. The format
 * was developed by the deltarpm project and is used to distribute package
 * updates more efficiently.
 *
 * <p>DRPM file structure:
 * <pre>
 * +------------------+
 * |  Magic "drpm"    |  4 bytes
 * +------------------+
 * |  Version         |  4 bytes (typically "0003")
 * +------------------+
 * |  Source NEVRA    |  null-terminated string
 * +------------------+
 * |  Sequence        |  null-terminated hex string
 * +------------------+
 * |  Target NEVRA    |  null-terminated string
 * +------------------+
 * |  Target size     |  4 bytes
 * +------------------+
 * |  Target MD5      |  16 bytes (optional)
 * +------------------+
 * |  Delta payload   |  remaining bytes
 * +------------------+
 * </pre>
 *
 * <p>Example usage:
 * <pre>{@code
 * // Check if a file is a delta RPM
 * if (DeltaReader.isDelta(path)) {
 *     Delta drpm = DeltaReader.read(path);
 *     System.out.println("Delta from " + drpm.sourceNevra() + " to " + drpm.targetNevra());
 * }
 * }</pre>
 *
 * @see Delta
 */
public final class DeltaReader {

    private static final Logger log = LoggerFactory.getLogger(DeltaReader.class);

    // Delta RPM magic bytes: "drpm" or "DLT3"
    private static final byte[] DRPM_MAGIC = {'d', 'r', 'p', 'm'};
    private static final byte[] DLT3_MAGIC = {'D', 'L', 'T', '3'};

    // Maximum sizes for security
    private static final int MAX_STRING_LENGTH = 4096;
    private static final int MAX_SEQUENCE_LENGTH = 1024;

    private DeltaReader() {
        // Utility class
    }

    /**
     * Reads a delta RPM from a file path.
     *
     * @param path the path to the delta RPM file
     * @return the parsed delta RPM
     * @throws InvalidFormatException if the file is not a valid delta RPM
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull Delta read(@NotNull Path path) throws InvalidFormatException, IOException {
        log.debug("Reading delta RPM from: {}", path);
        try (InputStream in = Files.newInputStream(path)) {
            return read(in, Files.size(path));
        }
    }

    /**
     * Reads a delta RPM from an input stream.
     *
     * @param stream the input stream containing the delta RPM data
     * @param totalSize the total size of the delta RPM (for delta size calculation)
     * @return the parsed delta RPM
     * @throws InvalidFormatException if the stream does not contain a valid delta RPM
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull Delta read(@NotNull InputStream stream, long totalSize)
            throws InvalidFormatException, IOException {
        BufferedInputStream buffered = stream instanceof BufferedInputStream
                ? (BufferedInputStream) stream
                : new BufferedInputStream(stream);

        BinaryReader reader = new BinaryReader(buffered);
        long startPosition = reader.position();

        // Read and verify magic
        byte[] magic = reader.readBytes(4);
        String magicType = detectMagicType(magic);
        if (magicType == null) {
            throw new InvalidFormatException(String.format(
                    "Invalid delta RPM magic: expected 'drpm' or 'DLT3', got 0x%02X%02X%02X%02X",
                    magic[0], magic[1], magic[2], magic[3]));
        }
        log.trace("Delta RPM magic type: {}", magicType);

        // Read version
        String version = new String(reader.readBytes(4), StandardCharsets.US_ASCII);
        log.trace("Delta RPM version: {}", version);

        // Read source NEVRA (null-terminated)
        String sourceNevra = readNullTerminatedString(reader, MAX_STRING_LENGTH);
        log.debug("Source NEVRA: {}", sourceNevra);

        // Read sequence (null-terminated hex string)
        String sequence = readNullTerminatedString(reader, MAX_SEQUENCE_LENGTH);
        log.trace("Sequence: {}", sequence);

        // Read target NEVRA (null-terminated)
        String targetNevra = readNullTerminatedString(reader, MAX_STRING_LENGTH);
        log.debug("Target NEVRA: {}", targetNevra);

        // Read target size (4 bytes, big-endian)
        long targetSize = reader.readUnsignedInt();
        log.trace("Target size: {} bytes", targetSize);

        // Determine delta type and compression based on version
        Delta.DeltaType deltaType = parseDeltaType(version);
        Delta.CompressionMethod compression = Delta.CompressionMethod.UNKNOWN;

        // Try to detect compression from payload
        buffered.mark(16);
        byte[] compressionMagic = new byte[4];
        int read = buffered.read(compressionMagic);
        buffered.reset();
        if (read >= 4) {
            compression = detectCompression(compressionMagic);
        }

        // Calculate delta size from remaining bytes
        long headerSize = reader.position() - startPosition;
        long deltaSize = totalSize - headerSize;

        // Try to extract digests if present (depends on version)
        Optional<String> sourceDigest = Optional.empty();
        Optional<String> targetDigest = Optional.empty();

        log.info("Read delta RPM: {} -> {}", sourceNevra, targetNevra);

        return new Delta(
                version,
                sourceNevra,
                targetNevra,
                sequence,
                sourceDigest,
                targetDigest,
                deltaSize,
                targetSize,
                deltaType,
                compression
        );
    }

    /**
     * Checks if a file is a delta RPM by examining its magic number.
     *
     * @param path the path to the file
     * @return true if the file appears to be a delta RPM
     * @throws IOException if an I/O error occurs
     */
    public static boolean isDelta(@NotNull Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            byte[] magic = new byte[4];
            int read = in.read(magic);
            if (read < 4) {
                return false;
            }
            return detectMagicType(magic) != null;
        }
    }

    /**
     * Checks if a file is a delta RPM by examining its magic number.
     *
     * @param stream the input stream (must support mark/reset)
     * @return true if the stream appears to contain a delta RPM
     * @throws IOException if an I/O error occurs
     */
    public static boolean isDelta(@NotNull InputStream stream) throws IOException {
        if (!stream.markSupported()) {
            throw new IllegalArgumentException("Stream must support mark/reset");
        }
        stream.mark(4);
        byte[] magic = new byte[4];
        int read = stream.read(magic);
        stream.reset();
        if (read < 4) {
            return false;
        }
        return detectMagicType(magic) != null;
    }

    private static String detectMagicType(byte[] magic) {
        if (magic[0] == DRPM_MAGIC[0] && magic[1] == DRPM_MAGIC[1] &&
                magic[2] == DRPM_MAGIC[2] && magic[3] == DRPM_MAGIC[3]) {
            return "drpm";
        }
        if (magic[0] == DLT3_MAGIC[0] && magic[1] == DLT3_MAGIC[1] &&
                magic[2] == DLT3_MAGIC[2] && magic[3] == DLT3_MAGIC[3]) {
            return "DLT3";
        }
        return null;
    }

    private static String readNullTerminatedString(BinaryReader reader, int maxLength)
            throws IOException, InvalidFormatException {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        while (count < maxLength) {
            int b = reader.readUnsignedByte();
            if (b == 0) {
                break;
            }
            sb.append((char) b);
            count++;
        }
        if (count >= maxLength) {
            throw new InvalidFormatException("String exceeds maximum length: " + maxLength);
        }
        return sb.toString();
    }

    private static Delta.DeltaType parseDeltaType(String version) {
        if (version.startsWith("0003") || version.startsWith("0004")) {
            return Delta.DeltaType.STANDARD;
        }
        return Delta.DeltaType.UNKNOWN;
    }

    private static Delta.CompressionMethod detectCompression(byte[] magic) {
        // gzip: 1F 8B
        if (magic[0] == (byte) 0x1F && magic[1] == (byte) 0x8B) {
            return Delta.CompressionMethod.GZIP;
        }
        // bzip2: BZ (42 5A)
        if (magic[0] == (byte) 0x42 && magic[1] == (byte) 0x5A) {
            return Delta.CompressionMethod.BZIP2;
        }
        // xz: FD 37 7A 58
        if (magic[0] == (byte) 0xFD && magic[1] == (byte) 0x37 &&
                magic[2] == (byte) 0x7A && magic[3] == (byte) 0x58) {
            return Delta.CompressionMethod.XZ;
        }
        // zstd: 28 B5 2F FD
        if (magic[0] == (byte) 0x28 && magic[1] == (byte) 0xB5 &&
                magic[2] == (byte) 0x2F && magic[3] == (byte) 0xFD) {
            return Delta.CompressionMethod.ZSTD;
        }
        return Delta.CompressionMethod.UNKNOWN;
    }
}
