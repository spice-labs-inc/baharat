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
package io.spicelabs.baharat.apk;

import io.spicelabs.baharat.common.SecurityUtils;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser for Alpine Linux .PKGINFO files.
 *
 * <p>APK .PKGINFO format is similar to Pacman but with some differences:
 * <pre>
 * pkgname = nginx
 * pkgver = 1.24.0-r1
 * pkgdesc = HTTP and reverse proxy server
 * url = https://nginx.org
 * size = 1048576
 * arch = x86_64
 * origin = nginx
 * maintainer = Alpine Team
 * license = BSD-2-Clause
 * depend = pcre2
 * depend = openssl
 * </pre>
 */
public final class ApkInfoParser {

    private ApkInfoParser() {
        // Utility class
    }

    /**
     * Parses a .PKGINFO file from an input stream.
     *
     * @param input the input stream
     * @return a map of field names to values
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull Map<String, Object> parse(@NotNull InputStream input) throws IOException {
        return parse(new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8)));
    }

    /**
     * Parses a .PKGINFO file from a string.
     *
     * @param content the string content
     * @return a map of field names to values
     */
    public static @NotNull Map<String, Object> parse(@NotNull String content) {
        try {
            return parse(new BufferedReader(new StringReader(content)));
        } catch (IOException e) {
            // Should not happen with StringReader
            throw new RuntimeException("Unexpected I/O error reading from string", e);
        }
    }

    /**
     * Parses a .PKGINFO file from a reader.
     *
     * @param reader the reader
     * @return a map of field names to values
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull Map<String, Object> parse(@NotNull BufferedReader reader) throws IOException {
        Map<String, Object> fields = new LinkedHashMap<>();

        String line;
        while ((line = reader.readLine()) != null) {
            // Security: Check line length to prevent DoS
            if (!SecurityUtils.isLineLengthValid(line)) {
                continue; // Skip excessively long lines
            }

            line = line.trim();

            // Skip comments and empty lines
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            // Parse key = value
            int eqPos = line.indexOf('=');
            if (eqPos <= 0) {
                continue;
            }

            String key = line.substring(0, eqPos).trim();
            String value = line.substring(eqPos + 1).trim();

            // Multi-valued fields
            if (isMultiValuedKey(key)) {
                @SuppressWarnings("unchecked")
                List<String> list = (List<String>) fields.computeIfAbsent(key, k -> new ArrayList<String>());
                list.add(value);
            } else {
                fields.put(key, value);
            }
        }

        return fields;
    }

    private static boolean isMultiValuedKey(@NotNull String key) {
        return switch (key) {
            case "depend", "provides", "replaces", "install_if",
                 "triggers", "license" -> true;
            default -> false;
        };
    }
}
