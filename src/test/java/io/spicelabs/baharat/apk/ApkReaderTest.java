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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for APK .PKGINFO parsing.
 */
class ApkReaderTest {

    @Test
    void parseApkInfo() {
        String pkgInfo = """
                pkgname = nginx
                pkgver = 1.24.0-r1
                pkgdesc = HTTP and reverse proxy server
                url = https://nginx.org
                size = 1048576
                arch = x86_64
                origin = nginx
                maintainer = Alpine Team
                license = BSD-2-Clause
                depend = pcre2
                depend = openssl
                provides = cmd:nginx
                """;

        Map<String, Object> fields = ApkInfoParser.parse(pkgInfo);

        assertThat(fields.get("pkgname")).isEqualTo("nginx");
        assertThat(fields.get("pkgver")).isEqualTo("1.24.0-r1");
        assertThat(fields.get("pkgdesc")).isEqualTo("HTTP and reverse proxy server");
        assertThat(fields.get("url")).isEqualTo("https://nginx.org");
        assertThat(fields.get("size")).isEqualTo("1048576");
        assertThat(fields.get("arch")).isEqualTo("x86_64");
        assertThat(fields.get("origin")).isEqualTo("nginx");
        assertThat(fields.get("maintainer")).isEqualTo("Alpine Team");

        @SuppressWarnings("unchecked")
        List<String> depends = (List<String>) fields.get("depend");
        assertThat(depends).containsExactly("pcre2", "openssl");
    }

    @Test
    void apkMetadataReleaseVersion() {
        String pkgInfo = """
                pkgname = test
                pkgver = 1.2.3-r4
                arch = x86_64
                """;

        Map<String, Object> fields = ApkInfoParser.parse(pkgInfo);
        ApkMetadata metadata = new ApkMetadata(fields);

        assertThat(metadata.name()).isEqualTo("test");
        assertThat(metadata.version()).isEqualTo("1.2.3-r4");
        assertThat(metadata.release()).contains("r4");
    }

    @Test
    void apkMetadataDependencies() {
        String pkgInfo = """
                pkgname = test
                pkgver = 1.0
                arch = x86_64
                depend = openssl>=1.1.0
                depend = pcre2
                provides = libtest.so
                replaces = test-old
                install_if = linux-lts>5.0
                """;

        Map<String, Object> fields = ApkInfoParser.parse(pkgInfo);
        ApkMetadata metadata = new ApkMetadata(fields);

        // Check dependencies
        assertThat(metadata.dependencies()).hasSize(2);

        Dependency openssl = metadata.dependencies().stream()
                .filter(d -> d.name().equals("openssl"))
                .findFirst()
                .orElseThrow();
        assertThat(openssl.operator()).isEqualTo(Dependency.Operator.GREATER_THAN_OR_EQUAL);
        assertThat(openssl.version()).contains("1.1.0");

        // Check provides
        assertThat(metadata.provides())
                .extracting(Dependency::name)
                .contains("libtest.so");

        // Check replaces
        assertThat(metadata.replaces())
                .extracting(Dependency::name)
                .contains("test-old");

        // Check install_if
        assertThat(metadata.installIf())
                .extracting(Dependency::name)
                .contains("linux-lts");
    }

    @Test
    void apkMetadataOrigin() {
        String pkgInfo = """
                pkgname = nginx-module-njs
                pkgver = 1.24.0-r1
                arch = x86_64
                origin = nginx
                commit = abc123def456
                datahash = sha256:abcd1234
                """;

        Map<String, Object> fields = ApkInfoParser.parse(pkgInfo);
        ApkMetadata metadata = new ApkMetadata(fields);

        assertThat(metadata.origin()).contains("nginx");
        assertThat(metadata.commit()).contains("abc123def456");
        assertThat(metadata.datahash()).contains("sha256:abcd1234");
    }

    @Test
    void apkMetadataInstalledSize() {
        String pkgInfo = """
                pkgname = test
                pkgver = 1.0
                arch = x86_64
                size = 2097152
                """;

        Map<String, Object> fields = ApkInfoParser.parse(pkgInfo);
        ApkMetadata metadata = new ApkMetadata(fields);

        assertThat(metadata.installedSize()).isEqualTo(2097152L);
    }
}
