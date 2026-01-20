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
import io.spicelabs.baharat.common.SecurityUtils;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parser for Debian control files.
 *
 * <p>Control files use RFC 822-like format:
 * <pre>
 * Field-Name: value
 * Another-Field: value
 *  continuation line (starts with space or tab)
 * </pre>
 *
 * <p>Multi-line values have continuation lines that start with whitespace.
 * The Description field commonly uses this format.
 */
public final class DebControlParser {

    private DebControlParser() {
        // Utility class
    }

    /**
     * Result of parsing a control file, containing both parsed fields and raw content.
     *
     * @param fields the parsed field name-value pairs
     * @param rawContent the original raw content of the control file
     */
    public record ParseResult(
            @NotNull Map<String, String> fields,
            @NotNull String rawContent
    ) {
    }

    /**
     * Parses a control file from an input stream.
     *
     * @param input the input stream
     * @return a map of field names to values
     * @throws IOException if an I/O error occurs
     * @throws PackageException if the control file is malformed
     */
    public static @NotNull Map<String, String> parse(@NotNull InputStream input)
            throws IOException, PackageException {
        return parse(new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8)));
    }

    /**
     * Parses a control file from a reader.
     *
     * @param reader the reader
     * @return a map of field names to values
     * @throws IOException if an I/O error occurs
     * @throws PackageException if the control file is malformed
     */
    public static @NotNull Map<String, String> parse(@NotNull BufferedReader reader)
            throws IOException, PackageException {
        Map<String, String> fields = new LinkedHashMap<>();
        String currentField = null;
        StringBuilder currentValue = new StringBuilder();

        String line;
        while ((line = reader.readLine()) != null) {
            // Security: Check line length to prevent DoS
            if (!SecurityUtils.isLineLengthValid(line)) {
                throw new PackageException.InvalidPackageException(
                        "Line exceeds maximum length in control file");
            }

            // Empty line ends a stanza
            if (line.isEmpty()) {
                if (currentField != null) {
                    fields.put(currentField, currentValue.toString().trim());
                    currentField = null;
                    currentValue.setLength(0);
                }
                continue;
            }

            // Continuation line (starts with space or tab)
            if (line.charAt(0) == ' ' || line.charAt(0) == '\t') {
                if (currentField == null) {
                    throw new PackageException.InvalidPackageException(
                            "Continuation line without field in control file");
                }
                // Handle continuation - if line is just ".", it's a blank line in description
                if (line.trim().equals(".")) {
                    currentValue.append("\n\n");
                } else {
                    currentValue.append("\n").append(line.substring(1));
                }
                continue;
            }

            // New field
            int colonPos = line.indexOf(':');
            if (colonPos <= 0) {
                throw new PackageException.InvalidPackageException(
                        "Invalid line in control file: " + line);
            }

            // Save previous field if exists
            if (currentField != null) {
                fields.put(currentField, currentValue.toString().trim());
            }

            currentField = line.substring(0, colonPos);
            currentValue.setLength(0);
            if (colonPos + 1 < line.length()) {
                currentValue.append(line.substring(colonPos + 1).trim());
            }
        }

        // Save last field
        if (currentField != null) {
            fields.put(currentField, currentValue.toString().trim());
        }

        return fields;
    }

    /**
     * Parses a control file from a string.
     *
     * @param content the control file content
     * @return a map of field names to values
     * @throws PackageException if the control file is malformed
     */
    public static @NotNull Map<String, String> parse(@NotNull String content) throws PackageException {
        try {
            return parse(new BufferedReader(new java.io.StringReader(content)));
        } catch (IOException e) {
            throw new PackageException.InvalidPackageException("Failed to parse control file", e);
        }
    }

    /**
     * Parses a control file from an input stream, returning both parsed fields and raw content.
     *
     * <p>This method reads the entire control file content and returns it along with
     * the parsed fields. This is useful for systems that need to store or process
     * the original control file text.
     *
     * @param input the input stream
     * @return a ParseResult containing both parsed fields and raw content
     * @throws IOException if an I/O error occurs
     * @throws PackageException if the control file is malformed
     */
    public static @NotNull ParseResult parseWithRaw(@NotNull InputStream input)
            throws IOException, PackageException {
        // Read all content first
        StringBuilder rawContent = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (rawContent.length() > 0) {
                    rawContent.append("\n");
                }
                rawContent.append(line);
            }
        }

        String content = rawContent.toString();
        Map<String, String> fields = parse(content);
        return new ParseResult(fields, content);
    }
}
