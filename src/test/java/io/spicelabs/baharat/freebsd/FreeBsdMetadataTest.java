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
import io.spicelabs.baharat.common.Dependency;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link FreeBsdMetadata}.
 */
class FreeBsdMetadataTest {

    @Test
    void basicMetadata() throws Exception {
        JsonObject json = ManifestParser.parse("""
                {
                  "name": "nginx",
                  "version": "1.24.0",
                  "arch": "freebsd:14:x86:64"
                }
                """);

        FreeBsdMetadata metadata = new FreeBsdMetadata(json);

        assertThat(metadata.name()).isEqualTo("nginx");
        assertThat(metadata.version()).isEqualTo("1.24.0");
        assertThat(metadata.arch()).isEqualTo("freebsd:14:x86:64");
    }

    @Test
    void summaryAndDescription() throws Exception {
        JsonObject json = ManifestParser.parse("""
                {
                  "name": "test",
                  "comment": "Short description",
                  "desc": "Long description with more details"
                }
                """);

        FreeBsdMetadata metadata = new FreeBsdMetadata(json);

        assertThat(metadata.summary()).contains("Short description");
        assertThat(metadata.description()).contains("Long description with more details");
    }

    @Test
    void descriptionFallsBackToComment() throws Exception {
        JsonObject json = ManifestParser.parse("""
                {
                  "name": "test",
                  "comment": "Only comment, no desc"
                }
                """);

        FreeBsdMetadata metadata = new FreeBsdMetadata(json);

        assertThat(metadata.description()).contains("Only comment, no desc");
    }

    @Test
    void maintainer() throws Exception {
        JsonObject json = ManifestParser.parse("""
                {
                  "name": "test",
                  "maintainer": "maintainer@freebsd.org"
                }
                """);

        FreeBsdMetadata metadata = new FreeBsdMetadata(json);

        assertThat(metadata.maintainer()).contains("maintainer@freebsd.org");
    }

    @Test
    void url() throws Exception {
        JsonObject json = ManifestParser.parse("""
                {
                  "name": "test",
                  "www": "https://freebsd.org"
                }
                """);

        FreeBsdMetadata metadata = new FreeBsdMetadata(json);

        assertThat(metadata.url()).contains("https://freebsd.org");
    }

    @Test
    void license() throws Exception {
        JsonObject json = ManifestParser.parse("""
                {
                  "name": "test",
                  "licenses": ["BSD-2-Clause", "MIT"]
                }
                """);

        FreeBsdMetadata metadata = new FreeBsdMetadata(json);

        assertThat(metadata.license()).contains("BSD-2-Clause, MIT");
    }

    @Test
    void installedSize() throws Exception {
        JsonObject json = ManifestParser.parse("""
                {
                  "name": "test",
                  "flatsize": 2097152
                }
                """);

        FreeBsdMetadata metadata = new FreeBsdMetadata(json);

        assertThat(metadata.installedSize()).isEqualTo(2097152L);
    }

    @Test
    void dependencies() throws Exception {
        JsonObject json = ManifestParser.parse("""
                {
                  "name": "test",
                  "deps": {
                    "openssl": {"origin": "security/openssl", "version": "3.0"},
                    "pcre2": {"origin": "devel/pcre2"}
                  }
                }
                """);

        FreeBsdMetadata metadata = new FreeBsdMetadata(json);

        assertThat(metadata.dependencies()).hasSize(2);

        Dependency openssl = metadata.dependencies().stream()
                .filter(d -> d.name().equals("openssl"))
                .findFirst()
                .orElseThrow();
        assertThat(openssl.operator()).isEqualTo(Dependency.Operator.GREATER_THAN_OR_EQUAL);
        assertThat(openssl.version()).contains("3.0");

        Dependency pcre2 = metadata.dependencies().stream()
                .filter(d -> d.name().equals("pcre2"))
                .findFirst()
                .orElseThrow();
        assertThat(pcre2.version()).isEmpty();
    }

    @Test
    void provides() throws Exception {
        JsonObject json = ManifestParser.parse("""
                {
                  "name": "test",
                  "provides": ["libtest.so.1", "httpd"]
                }
                """);

        FreeBsdMetadata metadata = new FreeBsdMetadata(json);

        assertThat(metadata.provides())
                .extracting(Dependency::name)
                .containsExactlyInAnyOrder("libtest.so.1", "httpd");
    }

    @Test
    void conflicts() throws Exception {
        JsonObject json = ManifestParser.parse("""
                {
                  "name": "test",
                  "conflicts": {
                    "test-devel": {"origin": "devel/test-devel"},
                    "test-old": {}
                  }
                }
                """);

        FreeBsdMetadata metadata = new FreeBsdMetadata(json);

        assertThat(metadata.conflicts())
                .extracting(Dependency::name)
                .containsExactlyInAnyOrder("test-devel", "test-old");
    }

    @Test
    void files() throws Exception {
        JsonObject json = ManifestParser.parse("""
                {
                  "name": "test",
                  "files": {
                    "/usr/local/bin/test": "sha256:aabb",
                    "/usr/local/share/test/README": "sha256:ccdd"
                  }
                }
                """);

        FreeBsdMetadata metadata = new FreeBsdMetadata(json);

        assertThat(metadata.files()).hasSize(2);
        assertThat(metadata.files())
                .extracting(f -> f.path())
                .containsExactlyInAnyOrder("/usr/local/bin/test", "/usr/local/share/test/README");

        assertThat(metadata.files().get(0).digest()).isPresent();
    }

    @Test
    void origin() throws Exception {
        JsonObject json = ManifestParser.parse("""
                {
                  "name": "nginx",
                  "origin": "www/nginx"
                }
                """);

        FreeBsdMetadata metadata = new FreeBsdMetadata(json);

        assertThat(metadata.origin()).contains("www/nginx");
    }

    @Test
    void prefix() throws Exception {
        JsonObject json = ManifestParser.parse("""
                {
                  "name": "test",
                  "prefix": "/usr/local"
                }
                """);

        FreeBsdMetadata metadata = new FreeBsdMetadata(json);

        assertThat(metadata.prefix()).contains("/usr/local");
    }

    @Test
    void abi() throws Exception {
        JsonObject json = ManifestParser.parse("""
                {
                  "name": "test",
                  "abi": "FreeBSD:14:amd64"
                }
                """);

        FreeBsdMetadata metadata = new FreeBsdMetadata(json);

        assertThat(metadata.abi()).contains("FreeBSD:14:amd64");
    }

    @Test
    void checksum() throws Exception {
        JsonObject json = ManifestParser.parse("""
                {
                  "name": "test",
                  "sum": "sha256:abcd1234"
                }
                """);

        FreeBsdMetadata metadata = new FreeBsdMetadata(json);

        assertThat(metadata.checksum()).contains("sha256:abcd1234");
    }

    @Test
    void emptyLists() throws Exception {
        JsonObject json = ManifestParser.parse("""
                {
                  "name": "test",
                  "version": "1.0"
                }
                """);

        FreeBsdMetadata metadata = new FreeBsdMetadata(json);

        assertThat(metadata.dependencies()).isEmpty();
        assertThat(metadata.provides()).isEmpty();
        assertThat(metadata.conflicts()).isEmpty();
        assertThat(metadata.files()).isEmpty();
    }

    @Test
    void missingOptionalFields() throws Exception {
        JsonObject json = ManifestParser.parse("""
                {
                  "name": "test"
                }
                """);

        FreeBsdMetadata metadata = new FreeBsdMetadata(json);

        assertThat(metadata.version()).isEmpty();
        assertThat(metadata.arch()).isEmpty();
        assertThat(metadata.maintainer()).isEmpty();
        assertThat(metadata.url()).isEmpty();
        assertThat(metadata.license()).isEmpty();
        assertThat(metadata.origin()).isEmpty();
        assertThat(metadata.prefix()).isEmpty();
        assertThat(metadata.abi()).isEmpty();
        assertThat(metadata.checksum()).isEmpty();
        assertThat(metadata.installedSize()).isEqualTo(0L);
    }

    @Test
    void getManifest() throws Exception {
        JsonObject json = ManifestParser.parse("""
                {
                  "name": "test",
                  "custom_field": "custom_value"
                }
                """);

        FreeBsdMetadata metadata = new FreeBsdMetadata(json);

        assertThat(metadata.getManifest().has("custom_field")).isTrue();
        assertThat(metadata.getManifest().get("custom_field").getAsString()).isEqualTo("custom_value");
    }
}
