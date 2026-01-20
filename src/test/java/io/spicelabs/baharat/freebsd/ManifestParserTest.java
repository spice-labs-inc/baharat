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
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ManifestParser}.
 */
class ManifestParserTest {

    @Test
    void parseSimpleManifest() throws Exception {
        String manifest = """
                {
                  "name": "nginx",
                  "version": "1.24.0"
                }
                """;

        JsonObject json = ManifestParser.parse(manifest);

        assertThat(json.get("name").getAsString()).isEqualTo("nginx");
        assertThat(json.get("version").getAsString()).isEqualTo("1.24.0");
    }

    @Test
    void parseManifestWithAllFields() throws Exception {
        String manifest = """
                {
                  "name": "nginx",
                  "version": "1.24.0",
                  "origin": "www/nginx",
                  "comment": "Robust and small HTTP server",
                  "arch": "freebsd:14:x86:64",
                  "maintainer": "maintainer@freebsd.org",
                  "www": "https://nginx.org",
                  "prefix": "/usr/local",
                  "flatsize": 2097152,
                  "abi": "FreeBSD:14:amd64",
                  "sum": "sha256:abcd1234"
                }
                """;

        JsonObject json = ManifestParser.parse(manifest);

        assertThat(json.get("name").getAsString()).isEqualTo("nginx");
        assertThat(json.get("origin").getAsString()).isEqualTo("www/nginx");
        assertThat(json.get("comment").getAsString()).isEqualTo("Robust and small HTTP server");
        assertThat(json.get("arch").getAsString()).isEqualTo("freebsd:14:x86:64");
        assertThat(json.get("flatsize").getAsLong()).isEqualTo(2097152L);
    }

    @Test
    void parseManifestWithDeps() throws Exception {
        String manifest = """
                {
                  "name": "test",
                  "deps": {
                    "openssl": {"origin": "security/openssl", "version": "3.0"},
                    "pcre2": {"origin": "devel/pcre2"}
                  }
                }
                """;

        JsonObject json = ManifestParser.parse(manifest);

        assertThat(json.has("deps")).isTrue();
        JsonObject deps = json.getAsJsonObject("deps");
        assertThat(deps.has("openssl")).isTrue();
        assertThat(deps.getAsJsonObject("openssl").get("version").getAsString()).isEqualTo("3.0");
    }

    @Test
    void parseManifestWithProvides() throws Exception {
        String manifest = """
                {
                  "name": "test",
                  "provides": ["libtest.so.1", "httpd"]
                }
                """;

        JsonObject json = ManifestParser.parse(manifest);

        assertThat(json.has("provides")).isTrue();
        assertThat(json.getAsJsonArray("provides")).hasSize(2);
    }

    @Test
    void parseManifestWithConflicts() throws Exception {
        String manifest = """
                {
                  "name": "test",
                  "conflicts": {
                    "test-devel": {"origin": "devel/test-devel"}
                  }
                }
                """;

        JsonObject json = ManifestParser.parse(manifest);

        assertThat(json.has("conflicts")).isTrue();
        assertThat(json.getAsJsonObject("conflicts").has("test-devel")).isTrue();
    }

    @Test
    void parseManifestWithFiles() throws Exception {
        String manifest = """
                {
                  "name": "test",
                  "files": {
                    "/usr/local/bin/test": "sha256:aabb",
                    "/usr/local/share/test/README": "sha256:ccdd"
                  }
                }
                """;

        JsonObject json = ManifestParser.parse(manifest);

        assertThat(json.has("files")).isTrue();
        JsonObject files = json.getAsJsonObject("files");
        assertThat(files.has("/usr/local/bin/test")).isTrue();
        assertThat(files.get("/usr/local/bin/test").getAsString()).isEqualTo("sha256:aabb");
    }

    @Test
    void parseManifestWithLicenses() throws Exception {
        String manifest = """
                {
                  "name": "test",
                  "licenses": ["BSD-2-Clause", "MIT"]
                }
                """;

        JsonObject json = ManifestParser.parse(manifest);

        assertThat(json.has("licenses")).isTrue();
        assertThat(json.getAsJsonArray("licenses")).hasSize(2);
    }

    @Test
    void parseFromInputStream() throws Exception {
        String manifest = """
                {"name": "test", "version": "1.0"}
                """;
        ByteArrayInputStream in = new ByteArrayInputStream(manifest.getBytes(StandardCharsets.UTF_8));

        JsonObject json = ManifestParser.parse(in);

        assertThat(json.get("name").getAsString()).isEqualTo("test");
    }

    @Test
    void rejectInvalidJson() {
        String invalid = "{ invalid json }";

        assertThatThrownBy(() -> ManifestParser.parse(invalid))
                .isInstanceOf(PackageException.InvalidPackageException.class)
                .hasMessageContaining("Failed to parse FreeBSD manifest");
    }

    @Test
    void rejectEmptyJson() {
        String empty = "";

        assertThatThrownBy(() -> ManifestParser.parse(empty))
                .isInstanceOf(PackageException.InvalidPackageException.class);
    }

    @Test
    void rejectJsonArray() {
        String array = "[1, 2, 3]";

        assertThatThrownBy(() -> ManifestParser.parse(array))
                .isInstanceOf(PackageException.InvalidPackageException.class);
    }

    @Test
    void parseManifestWithNullValue() throws Exception {
        String manifest = """
                {
                  "name": "test",
                  "version": null
                }
                """;

        JsonObject json = ManifestParser.parse(manifest);

        assertThat(json.get("name").getAsString()).isEqualTo("test");
        assertThat(json.get("version").isJsonNull()).isTrue();
    }

    @Test
    void parseMinifiedManifest() throws Exception {
        String manifest = "{\"name\":\"test\",\"version\":\"1.0\",\"deps\":{\"openssl\":{\"version\":\"3.0\"}}}";

        JsonObject json = ManifestParser.parse(manifest);

        assertThat(json.get("name").getAsString()).isEqualTo("test");
        assertThat(json.getAsJsonObject("deps").has("openssl")).isTrue();
    }
}
