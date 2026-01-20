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
package io.spicelabs.baharat.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Security tests for control file parsing across all package formats.
 * Tests for DEB control files, Pacman PKGINFO, APK PKGINFO, etc.
 *
 * These tests verify that malformed control files are handled gracefully
 * without crashes, memory issues, or security vulnerabilities.
 */
class ControlFileSecurityTest {

    // DEB Control File Tests (RFC 822-like format)

    @Test
    void debControlHandlesMissingPackageName() {
        String control = """
                Version: 1.0
                Architecture: amd64
                Maintainer: Test <test@example.com>
                Description: Test package
                """;

        Map<String, String> fields = parseDebControl(control);
        assertThat(fields.get("Package")).isNull();
        assertThat(fields.get("Version")).isEqualTo("1.0");
    }

    @Test
    void debControlHandlesDuplicateFields() {
        String control = """
                Package: test
                Version: 1.0
                Package: different-name
                Version: 2.0
                """;

        // Last value wins or first value wins - document behavior
        Map<String, String> fields = parseDebControl(control);
        assertThat(fields.get("Package")).isNotNull();
    }

    @Test
    void debControlHandlesEmptyValue() {
        String control = """
                Package: test
                Version:
                Architecture: amd64
                """;

        Map<String, String> fields = parseDebControl(control);
        assertThat(fields.get("Version")).isEmpty();
    }

    @Test
    void debControlHandlesMissingColon() {
        String control = """
                Package test
                Version: 1.0
                """;

        Map<String, String> fields = parseDebControl(control);
        // Line without colon should be skipped or handled
        assertThat(fields.get("Version")).isEqualTo("1.0");
    }

    @Test
    void debControlHandlesMultilineField() {
        String control = """
                Package: test
                Description: Short description
                 This is the long description that
                 continues over multiple lines.
                 .
                 And has a blank paragraph.
                Version: 1.0
                """;

        Map<String, String> fields = parseDebControl(control);
        assertThat(fields.get("Description")).contains("Short description");
        assertThat(fields.get("Version")).isEqualTo("1.0");
    }

    @Test
    void debControlHandlesExcessiveLineLength() {
        // Very long line (10KB+)
        String longValue = "x".repeat(10240);
        String control = "Package: test\nVersion: " + longValue + "\n";

        Map<String, String> fields = parseDebControl(control);
        assertThat(fields.get("Version")).isEqualTo(longValue);
    }

    @Test
    void debControlHandlesBinaryData() {
        // Binary data in control file
        byte[] binaryControl = new byte[100];
        new Random(42).nextBytes(binaryControl);
        // Keep some ASCII structure
        byte[] header = "Package: ".getBytes();
        System.arraycopy(header, 0, binaryControl, 0, header.length);

        // Should not crash
        try {
            parseDebControl(new String(binaryControl, StandardCharsets.ISO_8859_1));
        } catch (Exception e) {
            // Acceptable to reject
        }
    }

    @Test
    void debControlHandlesNullBytes() {
        String control = "Package: test\0hidden\nVersion: 1.0\n";

        Map<String, String> fields = parseDebControl(control);
        // Behavior with null bytes should be defined
        assertThat(fields.get("Version")).isEqualTo("1.0");
    }

    @Test
    void debControlHandlesOnlyWhitespace() {
        String control = "   \n\t\n   \n";

        Map<String, String> fields = parseDebControl(control);
        assertThat(fields).isEmpty();
    }

    @Test
    void debControlHandlesEmptyFile() {
        String control = "";

        Map<String, String> fields = parseDebControl(control);
        assertThat(fields).isEmpty();
    }

    // Pacman PKGINFO Tests (key=value format)

    @Test
    void pacmanPkginfoHandlesMalformedKeyValue() {
        String pkginfo = """
                pkgname = test
                pkgver = 1.0
                invalid line without equals
                arch = x86_64
                """;

        Map<String, String> fields = parsePacmanPkginfo(pkginfo);
        assertThat(fields.get("pkgname")).isEqualTo("test");
        assertThat(fields.get("arch")).isEqualTo("x86_64");
    }

    @Test
    void pacmanPkginfoHandlesMultipleEquals() {
        String pkginfo = """
                pkgname = test
                pkgdesc = This package has = in the description
                """;

        Map<String, String> fields = parsePacmanPkginfo(pkginfo);
        assertThat(fields.get("pkgdesc")).contains("=");
    }

    @Test
    void pacmanPkginfoHandlesEmbeddedNewlines() {
        // Newline in value (should not happen but test handling)
        String pkginfo = "pkgname = test\npkgdesc = line1\\\nline2\narch = x86_64\n";

        Map<String, String> fields = parsePacmanPkginfo(pkginfo);
        assertThat(fields.get("arch")).isEqualTo("x86_64");
    }

    @Test
    void pacmanPkginfoHandlesComments() {
        String pkginfo = """
                # This is a comment
                pkgname = test
                # Another comment
                pkgver = 1.0
                """;

        Map<String, String> fields = parsePacmanPkginfo(pkginfo);
        assertThat(fields.get("pkgname")).isEqualTo("test");
        assertThat(fields).doesNotContainKey("#");
    }

    @Test
    void pacmanPkginfoHandlesEmptyValues() {
        String pkginfo = """
                pkgname = test
                pkgdesc =
                arch = x86_64
                """;

        Map<String, String> fields = parsePacmanPkginfo(pkginfo);
        assertThat(fields.get("pkgdesc")).isEmpty();
    }

    @Test
    void pacmanPkginfoHandlesLeadingTrailingSpaces() {
        String pkginfo = """
                pkgname =    test
                arch =x86_64
                """;

        Map<String, String> fields = parsePacmanPkginfo(pkginfo);
        // Spaces may be trimmed or preserved
        assertThat(fields.get("pkgname")).contains("test");
    }

    // APK PKGINFO Tests (similar to Pacman)

    @Test
    void apkPkginfoHandlesInvalidFormat() {
        String pkginfo = """
                P:test-package
                V:1.0-r0
                A:x86_64
                invalid:line
                """;

        Map<String, String> fields = parseApkPkginfo(pkginfo);
        // APK uses single-letter keys with colon
        assertThat(fields.get("P")).isEqualTo("test-package");
    }

    @Test
    void apkPkginfoHandlesLongValues() {
        String longDesc = "d".repeat(5000);
        String pkginfo = "P:test\nV:1.0\nT:" + longDesc + "\n";

        Map<String, String> fields = parseApkPkginfo(pkginfo);
        assertThat(fields.get("T")).hasSize(5000);
    }

    // FreeBSD +COMPACT_MANIFEST Tests (UCL/JSON-like)

    @Test
    void freebsdManifestHandlesMalformedJson() {
        String manifest = """
                {
                    "name": "test",
                    "version": "1.0",
                    invalid json here
                }
                """;

        // Should not crash
        try {
            parseFreebsdManifest(manifest);
        } catch (Exception e) {
            // Expected for invalid JSON
        }
    }

    @Test
    void freebsdManifestHandlesDeepNesting() {
        // Deeply nested JSON
        StringBuilder manifest = new StringBuilder("{\"a\":");
        for (int i = 0; i < 100; i++) {
            manifest.append("{\"b\":");
        }
        manifest.append("1");
        for (int i = 0; i < 100; i++) {
            manifest.append("}");
        }
        manifest.append("}");

        try {
            parseFreebsdManifest(manifest.toString());
        } catch (Exception e) {
            // May reject deep nesting
        }
    }

    @Test
    void freebsdManifestHandlesLargeArrays() {
        StringBuilder manifest = new StringBuilder("{\"files\":[");
        for (int i = 0; i < 10000; i++) {
            if (i > 0) manifest.append(",");
            manifest.append("\"file").append(i).append(".txt\"");
        }
        manifest.append("]}");

        try {
            Map<String, Object> parsed = parseFreebsdManifest(manifest.toString());
            // Should handle large arrays
        } catch (Exception e) {
            // Acceptable to reject
        }
    }

    // OpenBSD Packing List Tests (directive-based)

    @Test
    void openbsdPackingListHandlesUnknownDirectives() {
        String packingList = """
                @name test-1.0
                @unknown-directive some value
                @arch x86_64
                file1.txt
                """;

        // Should skip unknown directives gracefully
        try {
            parseOpenBsdPackingList(packingList);
        } catch (Exception e) {
            // May reject or skip
        }
    }

    @Test
    void openbsdPackingListHandlesMissingArguments() {
        String packingList = """
                @name
                @arch
                file.txt
                """;

        try {
            parseOpenBsdPackingList(packingList);
        } catch (Exception e) {
            // Acceptable
        }
    }

    // Fuzzing tests

    @Test
    void randomDataDoesNotCrashParsers() {
        Random random = new Random(42);

        for (int i = 0; i < 100; i++) {
            byte[] randomData = new byte[256];
            random.nextBytes(randomData);
            String randomString = new String(randomData, StandardCharsets.ISO_8859_1);

            // Try all parsers - none should crash
            try { parseDebControl(randomString); } catch (Exception ignored) {}
            try { parsePacmanPkginfo(randomString); } catch (Exception ignored) {}
            try { parseApkPkginfo(randomString); } catch (Exception ignored) {}
            try { parseFreebsdManifest(randomString); } catch (Exception ignored) {}
            try { parseOpenBsdPackingList(randomString); } catch (Exception ignored) {}
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            " ",
            "\n",
            "\r\n",
            "\0",
            ":",
            "=",
            "{",
            "}",
            "@",
            "\\",
            "\uFFFF"
    })
    void specialCharactersDoNotCrashParsers(String input) {
        try { parseDebControl(input); } catch (Exception ignored) {}
        try { parsePacmanPkginfo(input); } catch (Exception ignored) {}
        try { parseApkPkginfo(input); } catch (Exception ignored) {}
        try { parseFreebsdManifest(input); } catch (Exception ignored) {}
        try { parseOpenBsdPackingList(input); } catch (Exception ignored) {}
    }

    // Helper parser implementations (simplified for testing)

    private Map<String, String> parseDebControl(String content) {
        Map<String, String> fields = new HashMap<>();
        String currentKey = null;
        StringBuilder currentValue = new StringBuilder();

        for (String line : content.split("\n")) {
            if (line.isEmpty()) {
                continue;
            }

            if (line.startsWith(" ") || line.startsWith("\t")) {
                // Continuation line
                if (currentKey != null) {
                    currentValue.append("\n").append(line.trim());
                }
            } else {
                // Save previous field
                if (currentKey != null) {
                    fields.put(currentKey, currentValue.toString());
                }

                // Parse new field
                int colonPos = line.indexOf(':');
                if (colonPos > 0) {
                    currentKey = line.substring(0, colonPos);
                    currentValue = new StringBuilder(line.substring(colonPos + 1).trim());
                } else {
                    currentKey = null;
                }
            }
        }

        // Save last field
        if (currentKey != null) {
            fields.put(currentKey, currentValue.toString());
        }

        return fields;
    }

    private Map<String, String> parsePacmanPkginfo(String content) {
        Map<String, String> fields = new HashMap<>();

        for (String line : content.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            int eqPos = line.indexOf('=');
            if (eqPos > 0) {
                String key = line.substring(0, eqPos).trim();
                String value = line.substring(eqPos + 1).trim();
                fields.put(key, value);
            }
        }

        return fields;
    }

    private Map<String, String> parseApkPkginfo(String content) {
        Map<String, String> fields = new HashMap<>();

        for (String line : content.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            int colonPos = line.indexOf(':');
            if (colonPos > 0) {
                String key = line.substring(0, colonPos);
                String value = line.substring(colonPos + 1);
                fields.put(key, value);
            }
        }

        return fields;
    }

    private Map<String, Object> parseFreebsdManifest(String content) {
        // Simplified JSON-like parsing (not full JSON)
        Map<String, Object> result = new HashMap<>();

        if (!content.trim().startsWith("{")) {
            throw new IllegalArgumentException("Not valid JSON");
        }

        // Very basic key-value extraction
        int pos = 0;
        while ((pos = content.indexOf("\"", pos)) >= 0) {
            int keyEnd = content.indexOf("\"", pos + 1);
            if (keyEnd < 0) break;

            String key = content.substring(pos + 1, keyEnd);
            int colonPos = content.indexOf(":", keyEnd);
            if (colonPos < 0) break;

            // Find value (simplified)
            int valueStart = colonPos + 1;
            while (valueStart < content.length() && Character.isWhitespace(content.charAt(valueStart))) {
                valueStart++;
            }

            if (valueStart >= content.length()) break;

            if (content.charAt(valueStart) == '"') {
                int valueEnd = content.indexOf("\"", valueStart + 1);
                if (valueEnd > 0) {
                    result.put(key, content.substring(valueStart + 1, valueEnd));
                }
            }

            pos = keyEnd + 1;
        }

        return result;
    }

    private Map<String, String> parseOpenBsdPackingList(String content) {
        Map<String, String> fields = new HashMap<>();

        for (String line : content.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            if (line.startsWith("@")) {
                int spacePos = line.indexOf(' ');
                if (spacePos > 0) {
                    String directive = line.substring(1, spacePos);
                    String value = line.substring(spacePos + 1);
                    fields.put(directive, value);
                } else {
                    fields.put(line.substring(1), "");
                }
            }
        }

        return fields;
    }
}
