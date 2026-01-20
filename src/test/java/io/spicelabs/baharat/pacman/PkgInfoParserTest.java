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
package io.spicelabs.baharat.pacman;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link PkgInfoParser}.
 */
class PkgInfoParserTest {

    @Test
    void parseSimpleField() {
        String pkgInfo = "pkgname = test\n";
        Map<String, Object> fields = PkgInfoParser.parse(pkgInfo);

        assertThat(fields.get("pkgname")).isEqualTo("test");
    }

    @Test
    void parseMultipleFields() {
        String pkgInfo = """
                pkgname = nginx
                pkgver = 1.24.0-1
                pkgdesc = Lightweight HTTP server
                arch = x86_64
                """;

        Map<String, Object> fields = PkgInfoParser.parse(pkgInfo);

        assertThat(fields.get("pkgname")).isEqualTo("nginx");
        assertThat(fields.get("pkgver")).isEqualTo("1.24.0-1");
        assertThat(fields.get("pkgdesc")).isEqualTo("Lightweight HTTP server");
        assertThat(fields.get("arch")).isEqualTo("x86_64");
    }

    @Test
    void parseMultiValuedField() {
        String pkgInfo = """
                pkgname = test
                depend = openssl
                depend = pcre2
                depend = zlib
                """;

        Map<String, Object> fields = PkgInfoParser.parse(pkgInfo);

        @SuppressWarnings("unchecked")
        List<String> depends = (List<String>) fields.get("depend");
        assertThat(depends).containsExactly("openssl", "pcre2", "zlib");
    }

    @Test
    void parseMultipleLicenses() {
        String pkgInfo = """
                pkgname = test
                license = MIT
                license = Apache-2.0
                license = BSD-3-Clause
                """;

        Map<String, Object> fields = PkgInfoParser.parse(pkgInfo);

        @SuppressWarnings("unchecked")
        List<String> licenses = (List<String>) fields.get("license");
        assertThat(licenses).containsExactly("MIT", "Apache-2.0", "BSD-3-Clause");
    }

    @Test
    void skipCommentLines() {
        String pkgInfo = """
                # This is a comment
                pkgname = test
                # Another comment
                pkgver = 1.0
                """;

        Map<String, Object> fields = PkgInfoParser.parse(pkgInfo);

        assertThat(fields.get("pkgname")).isEqualTo("test");
        assertThat(fields.get("pkgver")).isEqualTo("1.0");
        assertThat(fields).doesNotContainKey("#");
    }

    @Test
    void skipEmptyLines() {
        String pkgInfo = """
                pkgname = test

                pkgver = 1.0

                """;

        Map<String, Object> fields = PkgInfoParser.parse(pkgInfo);

        assertThat(fields).hasSize(2);
        assertThat(fields.get("pkgname")).isEqualTo("test");
        assertThat(fields.get("pkgver")).isEqualTo("1.0");
    }

    @Test
    void skipInvalidLines() {
        String pkgInfo = """
                pkgname = test
                invalid line without equals
                pkgver = 1.0
                """;

        Map<String, Object> fields = PkgInfoParser.parse(pkgInfo);

        assertThat(fields.get("pkgname")).isEqualTo("test");
        assertThat(fields.get("pkgver")).isEqualTo("1.0");
    }

    @Test
    void trimWhitespace() {
        String pkgInfo = "  pkgname   =   test   \n";
        Map<String, Object> fields = PkgInfoParser.parse(pkgInfo);

        assertThat(fields.get("pkgname")).isEqualTo("test");
    }

    @Test
    void parseFromInputStream() throws Exception {
        String pkgInfo = "pkgname = test\npkgver = 1.0\n";
        ByteArrayInputStream in = new ByteArrayInputStream(pkgInfo.getBytes(StandardCharsets.UTF_8));

        Map<String, Object> fields = PkgInfoParser.parse(in);

        assertThat(fields.get("pkgname")).isEqualTo("test");
        assertThat(fields.get("pkgver")).isEqualTo("1.0");
    }

    @Test
    void preserveFieldOrder() {
        String pkgInfo = """
                pkgname = test
                pkgver = 1.0
                pkgdesc = desc
                arch = x86_64
                """;

        Map<String, Object> fields = PkgInfoParser.parse(pkgInfo);

        // LinkedHashMap preserves insertion order
        assertThat(fields.keySet().stream().toList())
                .containsExactly("pkgname", "pkgver", "pkgdesc", "arch");
    }

    @Test
    void parseAllMultiValuedFields() {
        String pkgInfo = """
                pkgname = test
                depend = dep1
                makedepend = mkdep1
                checkdepend = chkdep1
                optdepend = optdep1: description
                conflict = conflict1
                provides = prov1
                replaces = repl1
                backup = etc/test.conf
                group = base
                """;

        Map<String, Object> fields = PkgInfoParser.parse(pkgInfo);

        assertThat(fields.get("depend")).isInstanceOf(List.class);
        assertThat(fields.get("makedepend")).isInstanceOf(List.class);
        assertThat(fields.get("checkdepend")).isInstanceOf(List.class);
        assertThat(fields.get("optdepend")).isInstanceOf(List.class);
        assertThat(fields.get("conflict")).isInstanceOf(List.class);
        assertThat(fields.get("provides")).isInstanceOf(List.class);
        assertThat(fields.get("replaces")).isInstanceOf(List.class);
        assertThat(fields.get("backup")).isInstanceOf(List.class);
        assertThat(fields.get("group")).isInstanceOf(List.class);
    }

    @Test
    void parseEmptyInput() {
        String pkgInfo = "";
        Map<String, Object> fields = PkgInfoParser.parse(pkgInfo);

        assertThat(fields).isEmpty();
    }

    @Test
    void parseOnlyComments() {
        String pkgInfo = """
                # Comment 1
                # Comment 2
                """;

        Map<String, Object> fields = PkgInfoParser.parse(pkgInfo);

        assertThat(fields).isEmpty();
    }

    @Test
    void parseFieldWithEqualsInValue() {
        String pkgInfo = "url = https://example.com?a=1&b=2\n";
        Map<String, Object> fields = PkgInfoParser.parse(pkgInfo);

        assertThat(fields.get("url")).isEqualTo("https://example.com?a=1&b=2");
    }

    @Test
    void parseBuildMetadata() {
        String pkgInfo = """
                pkgname = test
                builddate = 1699574400
                packager = Arch Linux Team <team@archlinux.org>
                buildhost = build.archlinux.org
                """;

        Map<String, Object> fields = PkgInfoParser.parse(pkgInfo);

        assertThat(fields.get("builddate")).isEqualTo("1699574400");
        assertThat(fields.get("packager")).isEqualTo("Arch Linux Team <team@archlinux.org>");
        assertThat(fields.get("buildhost")).isEqualTo("build.archlinux.org");
    }

    @Test
    void parseSizeField() {
        String pkgInfo = """
                pkgname = test
                size = 1048576
                """;

        Map<String, Object> fields = PkgInfoParser.parse(pkgInfo);

        assertThat(fields.get("size")).isEqualTo("1048576");
    }

    @Test
    void parsePkgbaseField() {
        String pkgInfo = """
                pkgname = nginx-module
                pkgbase = nginx
                """;

        Map<String, Object> fields = PkgInfoParser.parse(pkgInfo);

        assertThat(fields.get("pkgname")).isEqualTo("nginx-module");
        assertThat(fields.get("pkgbase")).isEqualTo("nginx");
    }
}
