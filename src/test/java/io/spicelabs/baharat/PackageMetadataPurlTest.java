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
package io.spicelabs.baharat;

import io.spicelabs.coordinates.Purl;
import com.google.gson.JsonObject;
import io.spicelabs.baharat.apk.ApkMetadata;
import io.spicelabs.baharat.deb.DebMetadata;
import io.spicelabs.baharat.freebsd.FreeBsdMetadata;
import io.spicelabs.baharat.openbsd.ContentsParser;
import io.spicelabs.baharat.openbsd.OpenBsdMetadata;
import io.spicelabs.baharat.pacman.PacmanMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link PackageMetadata#purl()} method across all package formats.
 */
class PackageMetadataPurlTest {

    @Nested
    @DisplayName("DEB Package PURL")
    class DebPurlTests {

        @Test
        void basicDebPurl() {
            Map<String, String> fields = new HashMap<>();
            fields.put("Package", "curl");
            fields.put("Version", "7.81.0-1ubuntu1.7");
            fields.put("Architecture", "amd64");

            DebMetadata metadata = new DebMetadata(fields);
            Purl purl = metadata.purl();

            assertThat(purl.type).isEqualTo("deb");
            assertThat(purl.namespace).isEqualTo("debian");
            assertThat(purl.name).isEqualTo("curl");
            assertThat(purl.version).isEqualTo("7.81.0-1ubuntu1.7");
            assertThat(purl.qualifiers).containsEntry("arch", "amd64");
        }

        @Test
        void debPurlWithAllArch() {
            Map<String, String> fields = new HashMap<>();
            fields.put("Package", "bash-completion");
            fields.put("Version", "2.11-5");
            fields.put("Architecture", "all");

            DebMetadata metadata = new DebMetadata(fields);
            Purl purl = metadata.purl();

            // "all" architecture should not include arch qualifier
            assertThat(purl.type).isEqualTo("deb");
            assertThat(purl.name).isEqualTo("bash-completion");
            assertThat(purl.version).isEqualTo("2.11-5");
            assertThat(purl.qualifiers).isNullOrEmpty();
        }

        @Test
        void debPurlEncodesSpecialCharacters() {
            Map<String, String> fields = new HashMap<>();
            fields.put("Package", "lib++-dev");
            fields.put("Version", "1.0");
            fields.put("Architecture", "amd64");

            DebMetadata metadata = new DebMetadata(fields);
            Purl purl = metadata.purl();

            // Coordinates handles encoding on canonicalization
            assertThat(purl.name).isEqualTo("lib++-dev");
            assertThat(purl.version).isEqualTo("1.0");
            assertThat(purl.qualifiers).containsEntry("arch", "amd64");
        }
    }

    @Nested
    @DisplayName("Pacman Package PURL")
    class PacmanPurlTests {

        @Test
        void basicPacmanPurl() {
            Map<String, Object> fields = new HashMap<>();
            fields.put("pkgname", "curl");
            fields.put("pkgver", "8.4.0-1");
            fields.put("arch", "x86_64");

            PacmanMetadata metadata = new PacmanMetadata(fields);
            Purl purl = metadata.purl();

            assertThat(purl.type).isEqualTo("alpm");
            assertThat(purl.namespace).isEqualTo("arch");
            assertThat(purl.name).isEqualTo("curl");
            assertThat(purl.version).isEqualTo("8.4.0-1");
            assertThat(purl.qualifiers).containsEntry("arch", "x86_64");
        }

        @Test
        void pacmanPurlWithAnyArch() {
            Map<String, Object> fields = new HashMap<>();
            fields.put("pkgname", "bash-completion");
            fields.put("pkgver", "2.11-1");
            fields.put("arch", "any");

            PacmanMetadata metadata = new PacmanMetadata(fields);
            Purl purl = metadata.purl();

            // "any" architecture should not include arch qualifier
            assertThat(purl.type).isEqualTo("alpm");
            assertThat(purl.name).isEqualTo("bash-completion");
            assertThat(purl.version).isEqualTo("2.11-1");
            assertThat(purl.qualifiers).isNullOrEmpty();
        }
    }

    @Nested
    @DisplayName("APK Package PURL")
    class ApkPurlTests {

        @Test
        void basicApkPurl() {
            Map<String, Object> fields = new HashMap<>();
            fields.put("pkgname", "curl");
            fields.put("pkgver", "8.4.0-r0");
            fields.put("arch", "x86_64");

            ApkMetadata metadata = new ApkMetadata(fields);
            Purl purl = metadata.purl();

            assertThat(purl.type).isEqualTo("apk");
            assertThat(purl.namespace).isEqualTo("alpine");
            assertThat(purl.name).isEqualTo("curl");
            assertThat(purl.version).isEqualTo("8.4.0-r0");
            assertThat(purl.qualifiers).containsEntry("arch", "x86_64");
        }

        @Test
        void apkPurlWithNoarch() {
            Map<String, Object> fields = new HashMap<>();
            fields.put("pkgname", "ca-certificates");
            fields.put("pkgver", "20230506-r0");
            fields.put("arch", "noarch");

            ApkMetadata metadata = new ApkMetadata(fields);
            Purl purl = metadata.purl();

            // "noarch" architecture should not include arch qualifier
            assertThat(purl.type).isEqualTo("apk");
            assertThat(purl.name).isEqualTo("ca-certificates");
            assertThat(purl.version).isEqualTo("20230506-r0");
            assertThat(purl.qualifiers).isNullOrEmpty();
        }
    }

    @Nested
    @DisplayName("FreeBSD Package PURL")
    class FreeBsdPurlTests {

        @Test
        void basicFreeBsdPurl() {
            JsonObject manifest = new JsonObject();
            manifest.addProperty("name", "curl");
            manifest.addProperty("version", "8.4.0");
            manifest.addProperty("arch", "amd64");

            FreeBsdMetadata metadata = new FreeBsdMetadata(manifest);
            Purl purl = metadata.purl();

            assertThat(purl.type).isEqualTo("freebsd");
            assertThat(purl.name).isEqualTo("curl");
            assertThat(purl.version).isEqualTo("8.4.0");
            assertThat(purl.qualifiers).containsEntry("arch", "amd64");
        }

        @Test
        void freebsdPurlWithStarArch() {
            JsonObject manifest = new JsonObject();
            manifest.addProperty("name", "pkg-config");
            manifest.addProperty("version", "0.29.2");
            manifest.addProperty("arch", "*");

            FreeBsdMetadata metadata = new FreeBsdMetadata(manifest);
            Purl purl = metadata.purl();

            // "*" architecture should not include arch qualifier
            assertThat(purl.type).isEqualTo("freebsd");
            assertThat(purl.name).isEqualTo("pkg-config");
            assertThat(purl.version).isEqualTo("0.29.2");
            assertThat(purl.qualifiers).isNullOrEmpty();
        }
    }

    @Nested
    @DisplayName("OpenBSD Package PURL")
    class OpenBsdPurlTests {

        @Test
        void basicOpenBsdPurl() {
            Map<String, String> metadataMap = new HashMap<>();
            metadataMap.put("name", "curl-8.4.0");
            metadataMap.put("arch", "amd64");

            ContentsParser.ParseResult parseResult = new ContentsParser.ParseResult(
                    metadataMap,
                    new ArrayList<>(),
                    new ArrayList<>()
            );

            OpenBsdMetadata metadata = new OpenBsdMetadata(parseResult, "HTTP fetching library");
            Purl purl = metadata.purl();

            assertThat(purl.type).isEqualTo("openbsd");
            assertThat(purl.name).isEqualTo("curl");
            assertThat(purl.version).isEqualTo("8.4.0");
            assertThat(purl.qualifiers).containsEntry("arch", "amd64");
        }

        @Test
        void openBsdPurlWithoutArch() {
            Map<String, String> metadataMap = new HashMap<>();
            metadataMap.put("name", "quirks-7.14");
            // No arch specified

            ContentsParser.ParseResult parseResult = new ContentsParser.ParseResult(
                    metadataMap,
                    new ArrayList<>(),
                    new ArrayList<>()
            );

            OpenBsdMetadata metadata = new OpenBsdMetadata(parseResult, "System quirks");
            Purl purl = metadata.purl();

            // No arch should not include arch qualifier
            assertThat(purl.type).isEqualTo("openbsd");
            assertThat(purl.name).isEqualTo("quirks");
            assertThat(purl.version).isEqualTo("7.14");
            assertThat(purl.qualifiers).isNullOrEmpty();
        }
    }

    @Nested
    @DisplayName("PURL Encoding")
    class PurlEncodingTests {

        @Test
        void handlesAtSign() {
            Map<String, String> fields = new HashMap<>();
            fields.put("Package", "pkg@2");
            fields.put("Version", "1.0");
            fields.put("Architecture", "amd64");

            DebMetadata metadata = new DebMetadata(fields);
            Purl purl = metadata.purl();

            // Purl stores the raw value and encodes on canonicalization
            assertThat(purl.name).isEqualTo("pkg@2");
            assertThat(purl.version).isEqualTo("1.0");
        }

        @Test
        void handlesSpaces() {
            Map<String, Object> fields = new HashMap<>();
            fields.put("pkgname", "my package");
            fields.put("pkgver", "1.0");
            fields.put("arch", "x86_64");

            PacmanMetadata metadata = new PacmanMetadata(fields);
            Purl purl = metadata.purl();

            // Purl stores the raw value
            assertThat(purl.name).isEqualTo("my package");
            assertThat(purl.version).isEqualTo("1.0");
        }

        @Test
        void preservesSafeCharacters() {
            Map<String, Object> fields = new HashMap<>();
            fields.put("pkgname", "lib-test_pkg.so");
            fields.put("pkgver", "1.0-beta~1");
            fields.put("arch", "x86_64");

            ApkMetadata metadata = new ApkMetadata(fields);
            Purl purl = metadata.purl();

            // Hyphens, underscores, dots, and tildes are preserved
            assertThat(purl.name).isEqualTo("lib-test_pkg.so");
            assertThat(purl.version).isEqualTo("1.0-beta~1");
        }

        @Test
        void handlesColons() {
            JsonObject manifest = new JsonObject();
            manifest.addProperty("name", "py:test");
            manifest.addProperty("version", "1.0");
            manifest.addProperty("arch", "amd64");

            FreeBsdMetadata metadata = new FreeBsdMetadata(manifest);
            Purl purl = metadata.purl();

            // Purl stores the raw value
            assertThat(purl.name).isEqualTo("py:test");
            assertThat(purl.version).isEqualTo("1.0");
        }

        @Test
        void handlesSlashes() {
            Map<String, String> fields = new HashMap<>();
            fields.put("Package", "pkg/subpkg");
            fields.put("Version", "1.0");
            fields.put("Architecture", "amd64");

            DebMetadata metadata = new DebMetadata(fields);
            Purl purl = metadata.purl();

            // Purl stores the raw value
            assertThat(purl.name).isEqualTo("pkg/subpkg");
            assertThat(purl.version).isEqualTo("1.0");
        }
    }

    @Nested
    @DisplayName("PURL Format Consistency")
    class PurlFormatConsistencyTests {

        @Test
        void allFormatsReturnValidPurl() {
            // DEB
            Map<String, String> debFields = new HashMap<>();
            debFields.put("Package", "test");
            debFields.put("Version", "1.0");
            debFields.put("Architecture", "amd64");
            assertThat(new DebMetadata(debFields).purl()).isNotNull();

            // Pacman
            Map<String, Object> pacmanFields = new HashMap<>();
            pacmanFields.put("pkgname", "test");
            pacmanFields.put("pkgver", "1.0");
            pacmanFields.put("arch", "x86_64");
            assertThat(new PacmanMetadata(pacmanFields).purl()).isNotNull();

            // APK
            Map<String, Object> apkFields = new HashMap<>();
            apkFields.put("pkgname", "test");
            apkFields.put("pkgver", "1.0");
            apkFields.put("arch", "x86_64");
            assertThat(new ApkMetadata(apkFields).purl()).isNotNull();

            // FreeBSD
            JsonObject freebsdManifest = new JsonObject();
            freebsdManifest.addProperty("name", "test");
            freebsdManifest.addProperty("version", "1.0");
            freebsdManifest.addProperty("arch", "amd64");
            assertThat(new FreeBsdMetadata(freebsdManifest).purl()).isNotNull();

            // OpenBSD
            Map<String, String> openbsdMeta = new HashMap<>();
            openbsdMeta.put("name", "test-1.0");
            openbsdMeta.put("arch", "amd64");
            ContentsParser.ParseResult parseResult = new ContentsParser.ParseResult(
                    openbsdMeta, new ArrayList<>(), new ArrayList<>());
            assertThat(new OpenBsdMetadata(parseResult, "").purl()).isNotNull();
        }

        @Test
        void allFormatsContainNameAndVersion() {
            String name = "mypackage";
            String version = "2.5.1";

            // DEB
            Map<String, String> debFields = new HashMap<>();
            debFields.put("Package", name);
            debFields.put("Version", version);
            debFields.put("Architecture", "amd64");
            Purl debPurl = new DebMetadata(debFields).purl();
            assertThat(debPurl.name).isEqualTo(name);
            assertThat(debPurl.version).isEqualTo(version);

            // Pacman
            Map<String, Object> pacmanFields = new HashMap<>();
            pacmanFields.put("pkgname", name);
            pacmanFields.put("pkgver", version);
            pacmanFields.put("arch", "x86_64");
            Purl pacmanPurl = new PacmanMetadata(pacmanFields).purl();
            assertThat(pacmanPurl.name).isEqualTo(name);
            assertThat(pacmanPurl.version).isEqualTo(version);

            // APK
            Map<String, Object> apkFields = new HashMap<>();
            apkFields.put("pkgname", name);
            apkFields.put("pkgver", version);
            apkFields.put("arch", "x86_64");
            Purl apkPurl = new ApkMetadata(apkFields).purl();
            assertThat(apkPurl.name).isEqualTo(name);
            assertThat(apkPurl.version).isEqualTo(version);

            // FreeBSD
            JsonObject freebsdManifest = new JsonObject();
            freebsdManifest.addProperty("name", name);
            freebsdManifest.addProperty("version", version);
            freebsdManifest.addProperty("arch", "amd64");
            Purl freebsdPurl = new FreeBsdMetadata(freebsdManifest).purl();
            assertThat(freebsdPurl.name).isEqualTo(name);
            assertThat(freebsdPurl.version).isEqualTo(version);

            // OpenBSD
            Map<String, String> openbsdMeta = new HashMap<>();
            openbsdMeta.put("name", name + "-" + version);
            openbsdMeta.put("arch", "amd64");
            ContentsParser.ParseResult parseResult = new ContentsParser.ParseResult(
                    openbsdMeta, new ArrayList<>(), new ArrayList<>());
            Purl openbsdPurl = new OpenBsdMetadata(parseResult, "").purl();
            assertThat(openbsdPurl.name).isEqualTo(name);
            assertThat(openbsdPurl.version).isEqualTo(version);
        }

        @Test
        void correctTypeForEachFormat() {
            // DEB
            Map<String, String> debFields = new HashMap<>();
            debFields.put("Package", "test");
            debFields.put("Version", "1.0");
            debFields.put("Architecture", "amd64");
            assertThat(new DebMetadata(debFields).purl().type).isEqualTo("deb");

            // Pacman
            Map<String, Object> pacmanFields = new HashMap<>();
            pacmanFields.put("pkgname", "test");
            pacmanFields.put("pkgver", "1.0");
            pacmanFields.put("arch", "x86_64");
            assertThat(new PacmanMetadata(pacmanFields).purl().type).isEqualTo("alpm");

            // APK
            Map<String, Object> apkFields = new HashMap<>();
            apkFields.put("pkgname", "test");
            apkFields.put("pkgver", "1.0");
            apkFields.put("arch", "x86_64");
            assertThat(new ApkMetadata(apkFields).purl().type).isEqualTo("apk");

            // FreeBSD
            JsonObject freebsdManifest = new JsonObject();
            freebsdManifest.addProperty("name", "test");
            freebsdManifest.addProperty("version", "1.0");
            freebsdManifest.addProperty("arch", "amd64");
            assertThat(new FreeBsdMetadata(freebsdManifest).purl().type).isEqualTo("freebsd");

            // OpenBSD
            Map<String, String> openbsdMeta = new HashMap<>();
            openbsdMeta.put("name", "test-1.0");
            openbsdMeta.put("arch", "amd64");
            ContentsParser.ParseResult parseResult = new ContentsParser.ParseResult(
                    openbsdMeta, new ArrayList<>(), new ArrayList<>());
            assertThat(new OpenBsdMetadata(parseResult, "").purl().type).isEqualTo("openbsd");
        }
    }
}
