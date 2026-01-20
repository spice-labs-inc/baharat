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
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link DebControlParser}.
 */
class DebControlParserTest {

    @Test
    void parseSimpleField() throws Exception {
        String control = "Package: test\n";
        Map<String, String> fields = DebControlParser.parse(control);

        assertThat(fields).hasSize(1);
        assertThat(fields.get("Package")).isEqualTo("test");
    }

    @Test
    void parseMultipleFields() throws Exception {
        String control = """
                Package: nginx
                Version: 1.24.0
                Architecture: amd64
                """;

        Map<String, String> fields = DebControlParser.parse(control);

        assertThat(fields).hasSize(3);
        assertThat(fields.get("Package")).isEqualTo("nginx");
        assertThat(fields.get("Version")).isEqualTo("1.24.0");
        assertThat(fields.get("Architecture")).isEqualTo("amd64");
    }

    @Test
    void parseContinuationLines() throws Exception {
        String control = """
                Package: test
                Description: Short description
                 This is a continuation line
                 And another one
                """;

        Map<String, String> fields = DebControlParser.parse(control);

        assertThat(fields.get("Description")).contains("Short description");
        assertThat(fields.get("Description")).contains("This is a continuation line");
        assertThat(fields.get("Description")).contains("And another one");
    }

    @Test
    void parseBlankLineInDescription() throws Exception {
        String control = """
                Package: test
                Description: Short
                 .
                 After blank line
                """;

        Map<String, String> fields = DebControlParser.parse(control);

        assertThat(fields.get("Description")).contains("\n\n");
        assertThat(fields.get("Description")).contains("After blank line");
    }

    @Test
    void parseFieldWithColonInValue() throws Exception {
        String control = "Homepage: https://example.com:8080/path\n";
        Map<String, String> fields = DebControlParser.parse(control);

        assertThat(fields.get("Homepage")).isEqualTo("https://example.com:8080/path");
    }

    @Test
    void parseEmptyValue() throws Exception {
        String control = "Package: \n";
        Map<String, String> fields = DebControlParser.parse(control);

        assertThat(fields.get("Package")).isEmpty();
    }

    @Test
    void parseTabContinuation() throws Exception {
        String control = """
                Package: test
                Description: Short
                \tTabbed continuation
                """;

        Map<String, String> fields = DebControlParser.parse(control);

        assertThat(fields.get("Description")).contains("Tabbed continuation");
    }

    @Test
    void parseFromInputStream() throws Exception {
        String control = "Package: test\nVersion: 1.0\n";
        ByteArrayInputStream input = new ByteArrayInputStream(control.getBytes(StandardCharsets.UTF_8));

        Map<String, String> fields = DebControlParser.parse(input);

        assertThat(fields.get("Package")).isEqualTo("test");
        assertThat(fields.get("Version")).isEqualTo("1.0");
    }

    @Test
    void parseTrimValues() throws Exception {
        String control = "Package:   test   \n";
        Map<String, String> fields = DebControlParser.parse(control);

        assertThat(fields.get("Package")).isEqualTo("test");
    }

    @Test
    void parsePreservesFieldOrder() throws Exception {
        String control = """
                Package: test
                Version: 1.0
                Architecture: all
                Maintainer: Test
                """;

        Map<String, String> fields = DebControlParser.parse(control);

        // LinkedHashMap preserves insertion order
        assertThat(fields.keySet().stream().toList())
                .containsExactly("Package", "Version", "Architecture", "Maintainer");
    }

    @Test
    void parseMultipleStanzas() throws Exception {
        String control = """
                Package: test
                Version: 1.0

                Package: another
                Version: 2.0
                """;

        Map<String, String> fields = DebControlParser.parse(control);

        // Empty line ends first stanza, second stanza overwrites
        assertThat(fields.get("Package")).isEqualTo("another");
        assertThat(fields.get("Version")).isEqualTo("2.0");
    }

    @Test
    void rejectInvalidLineWithoutColon() {
        String control = """
                Package: test
                invalid line
                Version: 1.0
                """;

        assertThatThrownBy(() -> DebControlParser.parse(control))
                .isInstanceOf(PackageException.InvalidPackageException.class)
                .hasMessageContaining("Invalid line");
    }

    @Test
    void rejectContinuationWithoutField() {
        String control = " continuation at start\n";

        assertThatThrownBy(() -> DebControlParser.parse(control))
                .isInstanceOf(PackageException.InvalidPackageException.class)
                .hasMessageContaining("Continuation line without field");
    }

    @Test
    void rejectColonAtStartOfLine() {
        String control = ": value\n";

        assertThatThrownBy(() -> DebControlParser.parse(control))
                .isInstanceOf(PackageException.InvalidPackageException.class)
                .hasMessageContaining("Invalid line");
    }

    @Test
    void parseEmptyInput() throws Exception {
        String control = "";
        Map<String, String> fields = DebControlParser.parse(control);

        assertThat(fields).isEmpty();
    }

    @Test
    void parseOnlyBlankLines() throws Exception {
        String control = "\n\n\n";
        Map<String, String> fields = DebControlParser.parse(control);

        assertThat(fields).isEmpty();
    }
}
