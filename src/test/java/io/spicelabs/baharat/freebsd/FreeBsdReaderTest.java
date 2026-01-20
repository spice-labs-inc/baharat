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
package io.spicelabs.baharat.freebsd;

import com.google.gson.JsonObject;
import io.spicelabs.baharat.PackageException;
import io.spicelabs.baharat.common.Dependency;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for FreeBSD manifest parsing.
 */
class FreeBsdReaderTest {

    @Test
    void parseManifest() throws Exception {
        String manifest = """
                {
                  "name": "nginx",
                  "version": "1.24.0",
                  "origin": "www/nginx",
                  "comment": "Robust and small HTTP server",
                  "arch": "freebsd:14:x86:64",
                  "maintainer": "maintainer@freebsd.org",
                  "www": "https://nginx.org",
                  "flatsize": 2097152,
                  "deps": {
                    "pcre2": {"origin": "devel/pcre2", "version": "10.42"},
                    "openssl": {"origin": "security/openssl", "version": "3.1.0"}
                  },
                  "provides": ["http-server", "reverse-proxy"],
                  "licenses": ["BSD-2-Clause"],
                  "files": {
                    "/usr/local/sbin/nginx": "sha256:abc123",
                    "/usr/local/etc/nginx/nginx.conf": "sha256:def456"
                  }
                }
                """;

        JsonObject json = ManifestParser.parse(manifest);
        FreeBsdMetadata metadata = new FreeBsdMetadata(json);

        assertThat(metadata.name()).isEqualTo("nginx");
        assertThat(metadata.version()).isEqualTo("1.24.0");
        assertThat(metadata.arch()).isEqualTo("freebsd:14:x86:64");
        assertThat(metadata.summary()).contains("Robust and small HTTP server");
        assertThat(metadata.url()).contains("https://nginx.org");
        assertThat(metadata.maintainer()).contains("maintainer@freebsd.org");
        assertThat(metadata.installedSize()).isEqualTo(2097152L);
        assertThat(metadata.origin()).contains("www/nginx");
        assertThat(metadata.license()).contains("BSD-2-Clause");
    }

    @Test
    void parseManifestDependencies() throws Exception {
        String manifest = """
                {
                  "name": "test",
                  "version": "1.0",
                  "arch": "freebsd:14:x86:64",
                  "deps": {
                    "openssl": {"origin": "security/openssl", "version": "3.0"},
                    "pcre2": {"origin": "devel/pcre2"}
                  },
                  "conflicts": {
                    "test-devel": {"origin": "devel/test-devel"}
                  },
                  "provides": ["libtest.so.1"]
                }
                """;

        JsonObject json = ManifestParser.parse(manifest);
        FreeBsdMetadata metadata = new FreeBsdMetadata(json);

        // Check dependencies
        assertThat(metadata.dependencies()).hasSize(2);

        Dependency openssl = metadata.dependencies().stream()
                .filter(d -> d.name().equals("openssl"))
                .findFirst()
                .orElseThrow();
        assertThat(openssl.version()).contains("3.0");
        assertThat(openssl.operator()).isEqualTo(Dependency.Operator.GREATER_THAN_OR_EQUAL);

        Dependency pcre2 = metadata.dependencies().stream()
                .filter(d -> d.name().equals("pcre2"))
                .findFirst()
                .orElseThrow();
        assertThat(pcre2.version()).isEmpty();

        // Check provides
        assertThat(metadata.provides())
                .extracting(Dependency::name)
                .contains("libtest.so.1");

        // Check conflicts
        assertThat(metadata.conflicts())
                .extracting(Dependency::name)
                .contains("test-devel");
    }

    @Test
    void parseManifestFiles() throws Exception {
        String manifest = """
                {
                  "name": "test",
                  "version": "1.0",
                  "arch": "freebsd:14:x86:64",
                  "files": {
                    "/usr/local/bin/test": "sha256:aabbccdd",
                    "/usr/local/share/test/README": "sha256:11223344"
                  }
                }
                """;

        JsonObject json = ManifestParser.parse(manifest);
        FreeBsdMetadata metadata = new FreeBsdMetadata(json);

        assertThat(metadata.files()).hasSize(2);
        assertThat(metadata.files())
                .extracting(f -> f.path())
                .containsExactlyInAnyOrder("/usr/local/bin/test", "/usr/local/share/test/README");

        assertThat(metadata.files().get(0).digest()).isPresent();
    }

    @Test
    void parseInvalidManifestThrows() {
        String invalid = "{ invalid json }";

        assertThatThrownBy(() -> ManifestParser.parse(invalid))
                .isInstanceOf(PackageException.InvalidPackageException.class);
    }

    @Test
    void parseManifestPrefix() throws Exception {
        String manifest = """
                {
                  "name": "test",
                  "version": "1.0",
                  "arch": "freebsd:14:x86:64",
                  "prefix": "/usr/local",
                  "abi": "FreeBSD:14:amd64",
                  "sum": "sha256:aaaa"
                }
                """;

        JsonObject json = ManifestParser.parse(manifest);
        FreeBsdMetadata metadata = new FreeBsdMetadata(json);

        assertThat(metadata.prefix()).contains("/usr/local");
        assertThat(metadata.abi()).contains("FreeBSD:14:amd64");
        assertThat(metadata.checksum()).contains("sha256:aaaa");
    }
}
