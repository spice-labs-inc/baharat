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
package io.spicelabs.baharat.rpm.lead;

import io.spicelabs.baharat.rpm.exception.InvalidFormatException;
import io.spicelabs.baharat.rpm.io.BinaryReader;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Parser for the RPM lead section.
 *
 * <p>The lead is 96 bytes with the following structure:
 * <pre>
 * Offset  Size  Field
 * 0       4     Magic: 0xEDABEEDB
 * 4       1     Major version
 * 5       1     Minor version
 * 6       2     Type (0=binary, 1=source)
 * 8       2     Architecture number
 * 10      66    Name (null-terminated)
 * 76      2     OS number
 * 78      2     Signature type
 * 80      16    Reserved
 * </pre>
 */
public final class LeadParser {

    private static final Logger log = LoggerFactory.getLogger(LeadParser.class);

    private static final int NAME_LENGTH = 66;
    private static final int RESERVED_LENGTH = 16;

    private LeadParser() {
        // Utility class
    }

    /**
     * Parses the lead section from the given binary reader.
     *
     * @param reader the binary reader positioned at the start of the lead
     * @return the parsed lead
     * @throws InvalidFormatException if the lead is invalid or malformed
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull Lead parse(@NotNull BinaryReader reader) throws InvalidFormatException, IOException {
        log.trace("Parsing RPM lead at position {}", reader.position());

        // Read and validate magic number
        int magic = reader.readInt();
        if (magic != Lead.MAGIC) {
            log.error("Invalid RPM magic: expected 0x{}, got 0x{}",
                    String.format("%08X", Lead.MAGIC), String.format("%08X", magic));
            throw new InvalidFormatException(String.format(
                    "Invalid RPM magic number: expected 0x%08X, got 0x%08X",
                    Lead.MAGIC, magic));
        }

        // Read version
        int majorVersion = reader.readUnsignedByte();
        int minorVersion = reader.readUnsignedByte();
        log.trace("RPM version: {}.{}", majorVersion, minorVersion);

        // Validate version (must be 3.x or 4.x)
        if (majorVersion < 3 || majorVersion > 4) {
            log.error("Unsupported RPM version: {}.{}", majorVersion, minorVersion);
            throw new InvalidFormatException(
                    "Unsupported RPM version: " + majorVersion + "." + minorVersion);
        }

        // Read type
        int type = reader.readUnsignedShort();
        if (type != Lead.TYPE_BINARY && type != Lead.TYPE_SOURCE) {
            log.error("Invalid RPM type: {}", type);
            throw new InvalidFormatException("Invalid RPM type: " + type);
        }

        // Read architecture
        int architecture = reader.readUnsignedShort();

        // Read name (66 bytes, null-terminated)
        String name = reader.readNullTerminatedString(NAME_LENGTH);
        log.trace("Lead name: {}", name);

        // Read OS number
        int osNumber = reader.readUnsignedShort();

        // Read signature type
        int signatureType = reader.readUnsignedShort();

        // Skip reserved bytes
        reader.skip(RESERVED_LENGTH);

        log.debug("Parsed lead: name={}, version={}.{}, type={}",
                name, majorVersion, minorVersion, type == Lead.TYPE_BINARY ? "binary" : "source");

        return new Lead(majorVersion, minorVersion, type, architecture,
                name, osNumber, signatureType);
    }
}
