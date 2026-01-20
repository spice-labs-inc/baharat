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

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ApkInfoParser}.
 */
class ApkInfoParserTest {

    @Test
    void parseSimpleField() {
        String pkgInfo = "pkgname = test\n";
        Map<String, Object> fields = ApkInfoParser.parse(pkgInfo);

        assertThat(fields.get("pkgname")).isEqualTo("test");
    }

    @Test
    void parseMultipleFields() {
        String pkgInfo = """
                pkgname = nginx
                pkgver = 1.24.0-r1
                pkgdesc = HTTP and reverse proxy server
                arch = x86_64
                """;

        Map<String, Object> fields = ApkInfoParser.parse(pkgInfo);

        assertThat(fields.get("pkgname")).isEqualTo("nginx");
        assertThat(fields.get("pkgver")).isEqualTo("1.24.0-r1");
        assertThat(fields.get("pkgdesc")).isEqualTo("HTTP and reverse proxy server");
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

        Map<String, Object> fields = ApkInfoParser.parse(pkgInfo);

        @SuppressWarnings("unchecked")
        List<String> depends = (List<String>) fields.get("depend");
        assertThat(depends).containsExactly("openssl", "pcre2", "zlib");
    }

    @Test
    void parseProvides() {
        String pkgInfo = """
                pkgname = test
                provides = cmd:test
                provides = pc:libtest
                """;

        Map<String, Object> fields = ApkInfoParser.parse(pkgInfo);

        @SuppressWarnings("unchecked")
        List<String> provides = (List<String>) fields.get("provides");
        assertThat(provides).containsExactly("cmd:test", "pc:libtest");
    }

    @Test
    void parseInstallIf() {
        String pkgInfo = """
                pkgname = test
                install_if = linux-lts>5.0
                install_if = another-pkg
                """;

        Map<String, Object> fields = ApkInfoParser.parse(pkgInfo);

        @SuppressWarnings("unchecked")
        List<String> installIf = (List<String>) fields.get("install_if");
        assertThat(installIf).containsExactly("linux-lts>5.0", "another-pkg");
    }

    @Test
    void parseReplaces() {
        String pkgInfo = """
                pkgname = test
                replaces = test-old
                replaces = test-deprecated
                """;

        Map<String, Object> fields = ApkInfoParser.parse(pkgInfo);

        @SuppressWarnings("unchecked")
        List<String> replaces = (List<String>) fields.get("replaces");
        assertThat(replaces).containsExactly("test-old", "test-deprecated");
    }

    @Test
    void parseTriggers() {
        String pkgInfo = """
                pkgname = test
                triggers = /usr/share/icons
                triggers = /usr/share/applications
                """;

        Map<String, Object> fields = ApkInfoParser.parse(pkgInfo);

        @SuppressWarnings("unchecked")
        List<String> triggers = (List<String>) fields.get("triggers");
        assertThat(triggers).containsExactly("/usr/share/icons", "/usr/share/applications");
    }

    @Test
    void skipCommentLines() {
        String pkgInfo = """
                # Alpine Linux APK package info
                pkgname = test
                # Another comment
                pkgver = 1.0
                """;

        Map<String, Object> fields = ApkInfoParser.parse(pkgInfo);

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

        Map<String, Object> fields = ApkInfoParser.parse(pkgInfo);

        assertThat(fields).hasSize(2);
    }

    @Test
    void skipInvalidLines() {
        String pkgInfo = """
                pkgname = test
                invalid line
                pkgver = 1.0
                """;

        Map<String, Object> fields = ApkInfoParser.parse(pkgInfo);

        assertThat(fields.get("pkgname")).isEqualTo("test");
        assertThat(fields.get("pkgver")).isEqualTo("1.0");
    }

    @Test
    void trimWhitespace() {
        String pkgInfo = "  pkgname   =   test   \n";
        Map<String, Object> fields = ApkInfoParser.parse(pkgInfo);

        assertThat(fields.get("pkgname")).isEqualTo("test");
    }

    @Test
    void parseFromInputStream() throws Exception {
        String pkgInfo = "pkgname = test\npkgver = 1.0\n";
        ByteArrayInputStream in = new ByteArrayInputStream(pkgInfo.getBytes(StandardCharsets.UTF_8));

        Map<String, Object> fields = ApkInfoParser.parse(in);

        assertThat(fields.get("pkgname")).isEqualTo("test");
        assertThat(fields.get("pkgver")).isEqualTo("1.0");
    }

    @Test
    void parseEmptyInput() {
        Map<String, Object> fields = ApkInfoParser.parse("");

        assertThat(fields).isEmpty();
    }

    @Test
    void parseApkSpecificFields() {
        String pkgInfo = """
                pkgname = test
                pkgver = 1.0
                origin = test
                commit = abc123def456
                datahash = sha256:1234abcd
                """;

        Map<String, Object> fields = ApkInfoParser.parse(pkgInfo);

        assertThat(fields.get("origin")).isEqualTo("test");
        assertThat(fields.get("commit")).isEqualTo("abc123def456");
        assertThat(fields.get("datahash")).isEqualTo("sha256:1234abcd");
    }

    @Test
    void parseMultipleLicenses() {
        String pkgInfo = """
                pkgname = test
                license = MIT
                license = Apache-2.0
                """;

        Map<String, Object> fields = ApkInfoParser.parse(pkgInfo);

        @SuppressWarnings("unchecked")
        List<String> licenses = (List<String>) fields.get("license");
        assertThat(licenses).containsExactly("MIT", "Apache-2.0");
    }

    @Test
    void parseUrl() {
        String pkgInfo = """
                pkgname = test
                url = https://example.com?foo=bar&baz=qux
                """;

        Map<String, Object> fields = ApkInfoParser.parse(pkgInfo);

        assertThat(fields.get("url")).isEqualTo("https://example.com?foo=bar&baz=qux");
    }
}
