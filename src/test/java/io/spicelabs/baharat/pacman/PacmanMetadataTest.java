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

import io.spicelabs.baharat.common.Dependency;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link PacmanMetadata}.
 */
class PacmanMetadataTest {

    @Test
    void basicMetadata() {
        Map<String, Object> fields = PkgInfoParser.parse("""
                pkgname = nginx
                pkgver = 1.24.0-1
                arch = x86_64
                """);

        PacmanMetadata metadata = new PacmanMetadata(fields);

        assertThat(metadata.name()).isEqualTo("nginx");
        assertThat(metadata.version()).isEqualTo("1.24.0-1");
        assertThat(metadata.arch()).isEqualTo("x86_64");
    }

    @Test
    void descriptionAndSummary() {
        Map<String, Object> fields = PkgInfoParser.parse("""
                pkgname = test
                pkgver = 1.0
                pkgdesc = A test package description
                """);

        PacmanMetadata metadata = new PacmanMetadata(fields);

        assertThat(metadata.description()).contains("A test package description");
        assertThat(metadata.summary()).contains("A test package description");
    }

    @Test
    void maintainer() {
        Map<String, Object> fields = PkgInfoParser.parse("""
                pkgname = test
                pkgver = 1.0
                packager = Arch Linux Team <team@archlinux.org>
                """);

        PacmanMetadata metadata = new PacmanMetadata(fields);

        assertThat(metadata.maintainer()).contains("Arch Linux Team <team@archlinux.org>");
    }

    @Test
    void url() {
        Map<String, Object> fields = PkgInfoParser.parse("""
                pkgname = test
                pkgver = 1.0
                url = https://example.com/project
                """);

        PacmanMetadata metadata = new PacmanMetadata(fields);

        assertThat(metadata.url()).contains("https://example.com/project");
    }

    @Test
    void license() {
        Map<String, Object> fields = PkgInfoParser.parse("""
                pkgname = test
                pkgver = 1.0
                license = MIT
                license = Apache-2.0
                """);

        PacmanMetadata metadata = new PacmanMetadata(fields);

        assertThat(metadata.license()).contains("MIT, Apache-2.0");
    }

    @Test
    void installedSize() {
        Map<String, Object> fields = PkgInfoParser.parse("""
                pkgname = test
                pkgver = 1.0
                size = 2097152
                """);

        PacmanMetadata metadata = new PacmanMetadata(fields);

        assertThat(metadata.installedSize()).isEqualTo(2097152L);
    }

    @Test
    void installedSizeInvalid() {
        Map<String, Object> fields = PkgInfoParser.parse("""
                pkgname = test
                pkgver = 1.0
                size = invalid
                """);

        PacmanMetadata metadata = new PacmanMetadata(fields);

        assertThat(metadata.installedSize()).isEqualTo(0L);
    }

    @Test
    void installedSizeMissing() {
        Map<String, Object> fields = PkgInfoParser.parse("""
                pkgname = test
                pkgver = 1.0
                """);

        PacmanMetadata metadata = new PacmanMetadata(fields);

        assertThat(metadata.installedSize()).isEqualTo(0L);
    }

    @Test
    void buildTime() {
        Map<String, Object> fields = PkgInfoParser.parse("""
                pkgname = test
                pkgver = 1.0
                builddate = 1699574400
                """);

        PacmanMetadata metadata = new PacmanMetadata(fields);

        assertThat(metadata.buildTime()).isPresent();
        assertThat(metadata.buildTime().get()).isEqualTo(Instant.ofEpochSecond(1699574400L));
    }

    @Test
    void buildTimeInvalid() {
        Map<String, Object> fields = PkgInfoParser.parse("""
                pkgname = test
                pkgver = 1.0
                builddate = invalid
                """);

        PacmanMetadata metadata = new PacmanMetadata(fields);

        assertThat(metadata.buildTime()).isEmpty();
    }

    @Test
    void dependencies() {
        Map<String, Object> fields = PkgInfoParser.parse("""
                pkgname = test
                pkgver = 1.0
                depend = openssl>=1.1.0
                depend = pcre2
                depend = zlib<2.0
                """);

        PacmanMetadata metadata = new PacmanMetadata(fields);

        assertThat(metadata.dependencies()).hasSize(3);

        Dependency openssl = metadata.dependencies().stream()
                .filter(d -> d.name().equals("openssl"))
                .findFirst()
                .orElseThrow();
        assertThat(openssl.operator()).isEqualTo(Dependency.Operator.GREATER_THAN_OR_EQUAL);
        assertThat(openssl.version()).contains("1.1.0");

        Dependency zlib = metadata.dependencies().stream()
                .filter(d -> d.name().equals("zlib"))
                .findFirst()
                .orElseThrow();
        assertThat(zlib.operator()).isEqualTo(Dependency.Operator.LESS_THAN);
        assertThat(zlib.version()).contains("2.0");

        Dependency pcre2 = metadata.dependencies().stream()
                .filter(d -> d.name().equals("pcre2"))
                .findFirst()
                .orElseThrow();
        assertThat(pcre2.operator()).isEqualTo(Dependency.Operator.ANY);
    }

    @Test
    void dependencyOperators() {
        Map<String, Object> fields = PkgInfoParser.parse("""
                pkgname = test
                pkgver = 1.0
                depend = pkg1>=1.0
                depend = pkg2<=2.0
                depend = pkg3>3.0
                depend = pkg4<4.0
                depend = pkg5=5.0
                """);

        PacmanMetadata metadata = new PacmanMetadata(fields);

        assertThat(metadata.dependencies()).hasSize(5);

        Dependency pkg1 = findDep(metadata, "pkg1");
        assertThat(pkg1.operator()).isEqualTo(Dependency.Operator.GREATER_THAN_OR_EQUAL);

        Dependency pkg2 = findDep(metadata, "pkg2");
        assertThat(pkg2.operator()).isEqualTo(Dependency.Operator.LESS_THAN_OR_EQUAL);

        Dependency pkg3 = findDep(metadata, "pkg3");
        assertThat(pkg3.operator()).isEqualTo(Dependency.Operator.GREATER_THAN);

        Dependency pkg4 = findDep(metadata, "pkg4");
        assertThat(pkg4.operator()).isEqualTo(Dependency.Operator.LESS_THAN);

        Dependency pkg5 = findDep(metadata, "pkg5");
        assertThat(pkg5.operator()).isEqualTo(Dependency.Operator.EQUAL);
    }

    @Test
    void provides() {
        Map<String, Object> fields = PkgInfoParser.parse("""
                pkgname = test
                pkgver = 1.0
                provides = libtest.so
                provides = httpd
                """);

        PacmanMetadata metadata = new PacmanMetadata(fields);

        assertThat(metadata.provides())
                .extracting(Dependency::name)
                .containsExactlyInAnyOrder("libtest.so", "httpd");
    }

    @Test
    void conflicts() {
        Map<String, Object> fields = PkgInfoParser.parse("""
                pkgname = test
                pkgver = 1.0
                conflict = test-git
                conflict = test-old
                """);

        PacmanMetadata metadata = new PacmanMetadata(fields);

        assertThat(metadata.conflicts())
                .extracting(Dependency::name)
                .containsExactlyInAnyOrder("test-git", "test-old");
    }

    @Test
    void replaces() {
        Map<String, Object> fields = PkgInfoParser.parse("""
                pkgname = test
                pkgver = 1.0
                replaces = test-old
                """);

        PacmanMetadata metadata = new PacmanMetadata(fields);

        assertThat(metadata.replaces())
                .extracting(Dependency::name)
                .contains("test-old");
    }

    @Test
    void makedepends() {
        Map<String, Object> fields = PkgInfoParser.parse("""
                pkgname = test
                pkgver = 1.0
                makedepend = gcc
                makedepend = cmake
                """);

        PacmanMetadata metadata = new PacmanMetadata(fields);

        assertThat(metadata.makedepends())
                .extracting(Dependency::name)
                .containsExactlyInAnyOrder("gcc", "cmake");
    }

    @Test
    void optdepends() {
        Map<String, Object> fields = PkgInfoParser.parse("""
                pkgname = test
                pkgver = 1.0
                optdepend = imagemagick: for image conversion
                optdepend = ffmpeg: for video support
                """);

        PacmanMetadata metadata = new PacmanMetadata(fields);

        assertThat(metadata.optdepends())
                .containsExactlyInAnyOrder(
                        "imagemagick: for image conversion",
                        "ffmpeg: for video support");
    }

    @Test
    void backup() {
        Map<String, Object> fields = PkgInfoParser.parse("""
                pkgname = test
                pkgver = 1.0
                backup = etc/test.conf
                backup = etc/test.d/default.conf
                """);

        PacmanMetadata metadata = new PacmanMetadata(fields);

        assertThat(metadata.backup())
                .containsExactlyInAnyOrder("etc/test.conf", "etc/test.d/default.conf");
    }

    @Test
    void group() {
        Map<String, Object> fields = PkgInfoParser.parse("""
                pkgname = test
                pkgver = 1.0
                group = base
                """);

        PacmanMetadata metadata = new PacmanMetadata(fields);

        assertThat(metadata.group()).contains("base");
    }

    @Test
    void pkgbase() {
        Map<String, Object> fields = PkgInfoParser.parse("""
                pkgname = nginx-module
                pkgver = 1.0
                pkgbase = nginx
                """);

        PacmanMetadata metadata = new PacmanMetadata(fields);

        assertThat(metadata.pkgbase()).contains("nginx");
    }

    @Test
    void emptyLists() {
        Map<String, Object> fields = PkgInfoParser.parse("""
                pkgname = test
                pkgver = 1.0
                """);

        PacmanMetadata metadata = new PacmanMetadata(fields);

        assertThat(metadata.dependencies()).isEmpty();
        assertThat(metadata.provides()).isEmpty();
        assertThat(metadata.conflicts()).isEmpty();
        assertThat(metadata.replaces()).isEmpty();
        assertThat(metadata.makedepends()).isEmpty();
        assertThat(metadata.optdepends()).isEmpty();
        assertThat(metadata.backup()).isEmpty();
    }

    private Dependency findDep(PacmanMetadata metadata, String name) {
        return metadata.dependencies().stream()
                .filter(d -> d.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Dependency not found: " + name));
    }
}
