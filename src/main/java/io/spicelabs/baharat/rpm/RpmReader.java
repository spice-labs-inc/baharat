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
package io.spicelabs.baharat.rpm;

import io.spicelabs.baharat.rpm.exception.FormatException;
import io.spicelabs.baharat.rpm.header.Header;
import io.spicelabs.baharat.rpm.header.HeaderParser;
import io.spicelabs.baharat.rpm.io.BinaryReader;
import io.spicelabs.baharat.rpm.lead.Lead;
import io.spicelabs.baharat.rpm.lead.LeadParser;
import io.spicelabs.baharat.rpm.metadata.PackageMetadata;
import io.spicelabs.baharat.rpm.payload.PayloadEntry;
import io.spicelabs.baharat.rpm.payload.PayloadReader;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Main entry point for reading RPM package files.
 *
 * <p>This class provides static methods for:
 * <ul>
 *   <li>Reading package metadata ({@link #read(Path)}, {@link #readMetadata(Path)})</li>
 *   <li>Streaming payload contents ({@link #streamPayload(Path)})</li>
 *   <li>Validating RPM files ({@link #isRpm(Path)})</li>
 * </ul>
 *
 * <h2>RPM File Structure</h2>
 * <p>An RPM file consists of four sections:
 * <pre>
 * +------------------+
 * |      Lead        |  96 bytes - Legacy header (mostly obsolete)
 * +------------------+
 * | Signature Header |  Variable - Contains checksums and signatures
 * +------------------+
 * |   Main Header    |  Variable - Contains all package metadata
 * +------------------+
 * |     Payload      |  Variable - Compressed CPIO archive with files
 * +------------------+
 * </pre>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Read package metadata
 * RpmPackage rpm = RpmReader.read(Path.of("package.rpm"));
 * System.out.println("Name: " + rpm.name());
 * System.out.println("Version: " + rpm.version());
 * System.out.println("License: " + rpm.metadata().license());
 *
 * // List files in the package
 * for (FileInfo file : rpm.metadata().files()) {
 *     System.out.println(file.path() + " (" + file.size() + " bytes)");
 * }
 *
 * // Stream and extract payload entries
 * try (Stream<PayloadEntry> entries = RpmReader.streamPayload(Path.of("package.rpm"))) {
 *     entries.forEach(entry -> {
 *         System.out.println(entry.path());
 *         if (entry.isFile()) {
 *             // Access file content via ((PayloadEntry.FileEntry) entry).content()
 *         }
 *     });
 * }
 * }</pre>
 *
 * @see RpmPackage
 * @see io.spicelabs.baharat.rpm.metadata.PackageMetadata
 * @see io.spicelabs.baharat.rpm.payload.PayloadEntry
 */
public final class RpmReader {

    private static final Logger log = LoggerFactory.getLogger(RpmReader.class);

    private RpmReader() {
        // Utility class
    }

    /**
     * Reads an RPM package from a file path.
     *
     * @param path the path to the RPM file
     * @return the parsed RPM package
     * @throws FormatException if the RPM cannot be read or parsed
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull RpmPackage read(@NotNull Path path) throws FormatException, IOException {
        log.debug("Reading RPM package from: {}", path);
        try (InputStream in = Files.newInputStream(path)) {
            RpmPackage rpm = read(in);
            log.info("Read RPM package: {}-{}-{}", rpm.name(), rpm.version(), rpm.release());
            // Re-wrap with the source path so Package.payload() can stream lazily
            // (mirrors DebReader.read(Path) and the other four readers).
            return new RpmPackage(rpm.lead(), rpm.signatureHeader(), rpm.header(),
                    rpm.payloadOffset(), path);
        }
    }

    /**
     * Reads an RPM package from an input stream.
     *
     * @param stream the input stream containing the RPM data
     * @return the parsed RPM package
     * @throws FormatException if the RPM cannot be read or parsed
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull RpmPackage read(@NotNull InputStream stream) throws FormatException, IOException {
        BufferedInputStream buffered = stream instanceof BufferedInputStream
                ? (BufferedInputStream) stream
                : new BufferedInputStream(stream);

        BinaryReader reader = new BinaryReader(buffered);

        // Parse lead
        log.trace("Parsing lead section");
        Lead lead = LeadParser.parse(reader);
        log.trace("Lead parsed: name={}, type={}", lead.name(), lead.type());

        // Parse signature header (aligned to 8-byte boundary)
        log.trace("Parsing signature header");
        Header signatureHeader = HeaderParser.parse(reader, true);
        log.trace("Signature header parsed: {} entries", signatureHeader.entries().size());

        // Parse main header
        log.trace("Parsing main header");
        Header header = HeaderParser.parse(reader, false);
        log.trace("Main header parsed: {} entries", header.entries().size());

        // Critical-tag validation: a header whose
        // name/version/arch tags are missing or corrupt is NOT a usable package — fail
        // loud at open instead of surfacing empty metadata silently.
        requireCriticalTags(header);

        // Record payload offset
        long payloadOffset = reader.position();
        log.trace("Payload starts at offset: {}", payloadOffset);

        return new RpmPackage(lead, signatureHeader, header, payloadOffset);
    }

    /**
     * Streams the payload entries from an RPM file.
     * The returned stream must be closed after use to release resources.
     *
     * @param path the path to the RPM file
     * @return a stream of payload entries
     * @throws FormatException if the RPM cannot be read or parsed
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull Stream<PayloadEntry> streamPayload(@NotNull Path path) throws FormatException, IOException {
        log.debug("Opening payload stream from: {}", path);
        InputStream fileStream = Files.newInputStream(path);
        try {
            BufferedInputStream buffered = new BufferedInputStream(fileStream);
            BinaryReader reader = new BinaryReader(buffered);

            // Parse headers to get to payload
            Lead lead = LeadParser.parse(reader);
            Header signatureHeader = HeaderParser.parse(reader, true);
            Header header = HeaderParser.parse(reader, false);

            requireCriticalTags(header);
            PackageMetadata metadata = new PackageMetadata(header);
            log.debug("Streaming payload for: {}-{}", metadata.name(), metadata.version());

            // Create payload reader
            PayloadReader payloadReader = new PayloadReader(buffered, metadata);

            // Return stream that closes resources when done
            return payloadReader.entries().onClose(() -> {
                try {
                    payloadReader.close();
                    log.trace("Payload stream closed");
                } catch (IOException e) {
                    log.debug("Error closing payload stream", e);
                }
            });

        } catch (Exception e) {
            fileStream.close();
            throw e;
        }
    }

    /**
     * Streams the payload entries from an RPM package.
     * The input stream is consumed starting from the current position.
     *
     * @param stream the input stream positioned at the start of the RPM
     * @return a stream of payload entries
     * @throws FormatException if the RPM cannot be read or parsed
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull Stream<PayloadEntry> streamPayload(@NotNull InputStream stream) throws FormatException, IOException {
        BufferedInputStream buffered = stream instanceof BufferedInputStream
                ? (BufferedInputStream) stream
                : new BufferedInputStream(stream);

        BinaryReader reader = new BinaryReader(buffered);

        // Parse headers to get to payload
        Lead lead = LeadParser.parse(reader);
        Header signatureHeader = HeaderParser.parse(reader, true);
        Header header = HeaderParser.parse(reader, false);

        requireCriticalTags(header);
        PackageMetadata metadata = new PackageMetadata(header);

        // Create payload reader
        PayloadReader payloadReader = new PayloadReader(buffered, metadata);

        return payloadReader.entries();
    }

    /**
     * Creates a PayloadReader for reading individual entries from an RPM file.
     * The caller is responsible for closing the returned reader.
     *
     * @param path the path to the RPM file
     * @return a payload reader
     * @throws FormatException if the RPM cannot be read or parsed
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull PayloadReader openPayload(@NotNull Path path) throws FormatException, IOException {
        log.debug("Opening payload reader for: {}", path);
        InputStream fileStream = Files.newInputStream(path);
        try {
            BufferedInputStream buffered = new BufferedInputStream(fileStream);
            BinaryReader reader = new BinaryReader(buffered);

            // Parse headers to get to payload
            Lead lead = LeadParser.parse(reader);
            Header signatureHeader = HeaderParser.parse(reader, true);
            Header header = HeaderParser.parse(reader, false);

            requireCriticalTags(header);
            PackageMetadata metadata = new PackageMetadata(header);
            log.debug("PayloadReader ready for: {}-{}", metadata.name(), metadata.version());

            return new PayloadReader(buffered, metadata);

        } catch (Exception e) {
            fileStream.close();
            throw e;
        }
    }

    /**
     * Reads just the package metadata without processing the payload.
     * This is more efficient when you only need header information.
     *
     * @param path the path to the RPM file
     * @return the package metadata
     * @throws FormatException if the RPM cannot be read or parsed
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull PackageMetadata readMetadata(@NotNull Path path) throws FormatException, IOException {
        return read(path).rpmMetadata();
    }

    /** Loud rejection when the main header lacks the mandatory NAME/VERSION/ARCH tags. */
    private static void requireCriticalTags(@NotNull Header header) throws FormatException {
        if (header.getString(io.spicelabs.baharat.rpm.header.HeaderTag.NAME.tag()).isEmpty()) {
            throw new io.spicelabs.baharat.rpm.exception.InvalidFormatException(
                    "RPM header missing required NAME tag");
        }
        if (header.getString(io.spicelabs.baharat.rpm.header.HeaderTag.VERSION.tag()).isEmpty()) {
            throw new io.spicelabs.baharat.rpm.exception.InvalidFormatException(
                    "RPM header missing required VERSION tag");
        }
        if (header.getString(io.spicelabs.baharat.rpm.header.HeaderTag.ARCH.tag()).isEmpty()) {
            throw new io.spicelabs.baharat.rpm.exception.InvalidFormatException(
                    "RPM header missing required ARCH tag");
        }
    }

    /**
     * Checks if a file appears to be a valid RPM by checking the magic number.
     *
     * @param path the path to the file
     * @return true if the file has a valid RPM magic number
     * @throws IOException if an I/O error occurs
     */
    public static boolean isRpm(@NotNull Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            byte[] magic = new byte[4];
            int read = in.read(magic);
            if (read < 4) {
                log.trace("File too small to be RPM: {}", path);
                return false;
            }

            // Check for RPM magic: 0xEDABEEDB (big-endian)
            boolean isRpm = magic[0] == (byte) 0xED
                    && magic[1] == (byte) 0xAB
                    && magic[2] == (byte) 0xEE
                    && magic[3] == (byte) 0xDB;
            log.trace("RPM magic check for {}: {}", path, isRpm);
            return isRpm;
        }
    }
}
