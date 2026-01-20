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

import io.spicelabs.baharat.common.Dependency;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ApkMetadata}.
 */
class ApkMetadataTest {

    @Test
    void basicMetadata() {
        Map<String, Object> fields = ApkInfoParser.parse("""
                pkgname = nginx
                pkgver = 1.24.0-r1
                arch = x86_64
                """);

        ApkMetadata metadata = new ApkMetadata(fields);

        assertThat(metadata.name()).isEqualTo("nginx");
        assertThat(metadata.version()).isEqualTo("1.24.0-r1");
        assertThat(metadata.arch()).isEqualTo("x86_64");
    }

    @Test
    void releaseVersion() {
        Map<String, Object> fields = ApkInfoParser.parse("""
                pkgname = test
                pkgver = 1.2.3-r4
                """);

        ApkMetadata metadata = new ApkMetadata(fields);

        assertThat(metadata.version()).isEqualTo("1.2.3-r4");
        assertThat(metadata.release()).contains("r4");
    }

    @Test
    void releaseVersionMissing() {
        Map<String, Object> fields = ApkInfoParser.parse("""
                pkgname = test
                pkgver = 1.2.3
                """);

        ApkMetadata metadata = new ApkMetadata(fields);

        assertThat(metadata.release()).isEmpty();
    }

    @Test
    void descriptionAndSummary() {
        Map<String, Object> fields = ApkInfoParser.parse("""
                pkgname = test
                pkgver = 1.0
                pkgdesc = A test package
                """);

        ApkMetadata metadata = new ApkMetadata(fields);

        assertThat(metadata.description()).contains("A test package");
        assertThat(metadata.summary()).contains("A test package");
    }

    @Test
    void maintainer() {
        Map<String, Object> fields = ApkInfoParser.parse("""
                pkgname = test
                pkgver = 1.0
                maintainer = Alpine Team <team@alpinelinux.org>
                """);

        ApkMetadata metadata = new ApkMetadata(fields);

        assertThat(metadata.maintainer()).contains("Alpine Team <team@alpinelinux.org>");
    }

    @Test
    void url() {
        Map<String, Object> fields = ApkInfoParser.parse("""
                pkgname = test
                pkgver = 1.0
                url = https://alpinelinux.org
                """);

        ApkMetadata metadata = new ApkMetadata(fields);

        assertThat(metadata.url()).contains("https://alpinelinux.org");
    }

    @Test
    void license() {
        Map<String, Object> fields = ApkInfoParser.parse("""
                pkgname = test
                pkgver = 1.0
                license = MIT
                license = Apache-2.0
                """);

        ApkMetadata metadata = new ApkMetadata(fields);

        assertThat(metadata.license()).contains("MIT, Apache-2.0");
    }

    @Test
    void installedSize() {
        Map<String, Object> fields = ApkInfoParser.parse("""
                pkgname = test
                pkgver = 1.0
                size = 2097152
                """);

        ApkMetadata metadata = new ApkMetadata(fields);

        assertThat(metadata.installedSize()).isEqualTo(2097152L);
    }

    @Test
    void dependencies() {
        Map<String, Object> fields = ApkInfoParser.parse("""
                pkgname = test
                pkgver = 1.0
                depend = openssl>=1.1.0
                depend = pcre2
                depend = zlib<2.0
                """);

        ApkMetadata metadata = new ApkMetadata(fields);

        assertThat(metadata.dependencies()).hasSize(3);

        Dependency openssl = metadata.dependencies().stream()
                .filter(d -> d.name().equals("openssl"))
                .findFirst()
                .orElseThrow();
        assertThat(openssl.operator()).isEqualTo(Dependency.Operator.GREATER_THAN_OR_EQUAL);
        assertThat(openssl.version()).contains("1.1.0");
    }

    @Test
    void provides() {
        Map<String, Object> fields = ApkInfoParser.parse("""
                pkgname = test
                pkgver = 1.0
                provides = cmd:test
                provides = pc:libtest
                """);

        ApkMetadata metadata = new ApkMetadata(fields);

        assertThat(metadata.provides())
                .extracting(Dependency::name)
                .containsExactlyInAnyOrder("cmd:test", "pc:libtest");
    }

    @Test
    void replaces() {
        Map<String, Object> fields = ApkInfoParser.parse("""
                pkgname = test
                pkgver = 1.0
                replaces = test-old
                """);

        ApkMetadata metadata = new ApkMetadata(fields);

        assertThat(metadata.replaces())
                .extracting(Dependency::name)
                .contains("test-old");
    }

    @Test
    void installIf() {
        Map<String, Object> fields = ApkInfoParser.parse("""
                pkgname = test
                pkgver = 1.0
                install_if = linux-lts>5.0
                install_if = another-pkg
                """);

        ApkMetadata metadata = new ApkMetadata(fields);

        assertThat(metadata.installIf())
                .extracting(Dependency::name)
                .containsExactlyInAnyOrder("linux-lts", "another-pkg");
    }

    @Test
    void origin() {
        Map<String, Object> fields = ApkInfoParser.parse("""
                pkgname = nginx-module-njs
                pkgver = 1.0
                origin = nginx
                """);

        ApkMetadata metadata = new ApkMetadata(fields);

        assertThat(metadata.origin()).contains("nginx");
    }

    @Test
    void commit() {
        Map<String, Object> fields = ApkInfoParser.parse("""
                pkgname = test
                pkgver = 1.0
                commit = abc123def456
                """);

        ApkMetadata metadata = new ApkMetadata(fields);

        assertThat(metadata.commit()).contains("abc123def456");
    }

    @Test
    void datahash() {
        Map<String, Object> fields = ApkInfoParser.parse("""
                pkgname = test
                pkgver = 1.0
                datahash = sha256:abcd1234
                """);

        ApkMetadata metadata = new ApkMetadata(fields);

        assertThat(metadata.datahash()).contains("sha256:abcd1234");
    }

    @Test
    void triggers() {
        Map<String, Object> fields = ApkInfoParser.parse("""
                pkgname = test
                pkgver = 1.0
                triggers = /usr/share/icons
                """);

        ApkMetadata metadata = new ApkMetadata(fields);

        assertThat(metadata.triggers()).contains("/usr/share/icons");
    }

    @Test
    void emptyLists() {
        Map<String, Object> fields = ApkInfoParser.parse("""
                pkgname = test
                pkgver = 1.0
                """);

        ApkMetadata metadata = new ApkMetadata(fields);

        assertThat(metadata.dependencies()).isEmpty();
        assertThat(metadata.provides()).isEmpty();
        assertThat(metadata.replaces()).isEmpty();
        assertThat(metadata.installIf()).isEmpty();
        assertThat(metadata.triggers()).isEmpty();
    }
}
