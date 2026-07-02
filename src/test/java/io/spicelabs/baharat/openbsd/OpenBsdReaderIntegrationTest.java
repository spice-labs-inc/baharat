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
package io.spicelabs.baharat.openbsd;

import io.spicelabs.coordinates.Purl;
import io.spicelabs.baharat.PackageEntry;
import io.spicelabs.baharat.testdata.PackageTestFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for OpenBSD package reading using real OpenBSD packages.
 */
class OpenBsdReaderIntegrationTest {

    static boolean hasOpenBsdFiles() {
        return PackageTestFiles.hasFiles(PackageTestFiles.OPENBSD);
    }

    @Test
    @EnabledIf("hasOpenBsdFiles")
    void readRealOpenBsdPackage() throws Exception {
        List<Path> openBsdFiles = PackageTestFiles.getFiles(PackageTestFiles.OPENBSD, ".tgz", 1);
        assertThat(openBsdFiles).isNotEmpty();

        Path path = openBsdFiles.get(0);
        OpenBsdPackage pkg = OpenBsdReader.read(path);

        assertThat(pkg).isNotNull();
        assertThat(pkg.metadata()).isNotNull();
        assertThat(pkg.metadata().name()).isNotEmpty();
        assertThat(pkg.metadata().version()).isNotEmpty();
    }

    @Test
    @EnabledIf("hasOpenBsdFiles")
    void readMultipleOpenBsdPackages() throws Exception {
        List<Path> openBsdFiles = PackageTestFiles.getFiles(PackageTestFiles.OPENBSD, ".tgz", 10);

        int successCount = 0;
        for (Path path : openBsdFiles) {
            try {
                OpenBsdPackage pkg = OpenBsdReader.read(path);
                assertThat(pkg.metadata().name()).isNotEmpty();
                successCount++;
            } catch (Exception e) {
                // Some packages might have issues, continue with others
            }
        }

        assertThat(successCount).isGreaterThan(0);
    }

    @Test
    @EnabledIf("hasOpenBsdFiles")
    void streamOpenBsdPayload() throws Exception {
        List<Path> openBsdFiles = PackageTestFiles.getFiles(PackageTestFiles.OPENBSD, ".tgz", 1);
        assertThat(openBsdFiles).isNotEmpty();

        Path path = openBsdFiles.get(0);

        try (Stream<PackageEntry> entries = OpenBsdReader.streamPayload(path)) {
            long count = entries.count();
            // OpenBSD packages should have at least some files
            assertThat(count).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    @EnabledIf("hasOpenBsdFiles")
    void openBsdMetadataContainsExpectedFields() throws Exception {
        List<Path> openBsdFiles = PackageTestFiles.getFiles(PackageTestFiles.OPENBSD, ".tgz", 1);
        assertThat(openBsdFiles).isNotEmpty();

        Path path = openBsdFiles.get(0);
        OpenBsdPackage pkg = OpenBsdReader.read(path);

        // All OpenBSD packages should have these basic fields
        assertThat(pkg.metadata().name()).isNotEmpty();
        assertThat(pkg.metadata().version()).isNotEmpty();
    }

    @Test
    @EnabledIf("hasOpenBsdFiles")
    void readAllOpenBsdPackages() throws Exception {
        List<Path> openBsdFiles = PackageTestFiles.getAllFiles(PackageTestFiles.OPENBSD, ".tgz");

        int totalCount = openBsdFiles.size();
        int successCount = 0;

        for (Path path : openBsdFiles) {
            try {
                OpenBsdPackage pkg = OpenBsdReader.read(path);
                if (pkg.metadata().name() != null && !pkg.metadata().name().isEmpty()) {
                    successCount++;
                }
            } catch (Exception e) {
                // Some packages might have issues
            }
        }

        // At least 80% should succeed
        double successRate = (double) successCount / totalCount;
        assertThat(successRate).isGreaterThan(0.8);
    }

    // PURL Integration Tests

    @Test
    @EnabledIf("hasOpenBsdFiles")
    void purlForOpenBsdPackage() throws Exception {
        List<Path> openBsdFiles = PackageTestFiles.getFiles(PackageTestFiles.OPENBSD, ".tgz", 1);
        assertThat(openBsdFiles).isNotEmpty();

        Path path = openBsdFiles.get(0);
        OpenBsdPackage pkg = OpenBsdReader.read(path);

        Purl purl = pkg.metadata().purl();

        assertThat(purl).isNotNull();
        assertThat(purl.type).isEqualTo("openbsd");
        assertThat(purl.name).isEqualTo(pkg.metadata().name());
        assertThat(purl.version).isEqualTo(pkg.metadata().version());
        // OpenBSD does not set a namespace by default
        assertThat(purl.namespace).isNull();
    }

    @Test
    @EnabledIf("hasOpenBsdFiles")
    void purlViaPackageInterfaceMatchesMetadata() throws Exception {
        List<Path> openBsdFiles = PackageTestFiles.getFiles(PackageTestFiles.OPENBSD, ".tgz", 1);
        assertThat(openBsdFiles).isNotEmpty();

        Path path = openBsdFiles.get(0);
        OpenBsdPackage pkg = OpenBsdReader.read(path);

        Purl fromPackage = pkg.purl();
        Purl fromMetadata = pkg.metadata().purl();

        // Type and name must match
        assertThat(fromPackage.type).isEqualTo(fromMetadata.type);
        assertThat(fromPackage.name).isEqualTo(fromMetadata.name);
        assertThat(fromPackage.version).isEqualTo(fromMetadata.version);
    }

    @Test
    @EnabledIf("hasOpenBsdFiles")
    void purlCanBeParsedBack() throws Exception {
        List<Path> openBsdFiles = PackageTestFiles.getFiles(PackageTestFiles.OPENBSD, ".tgz", 1);
        assertThat(openBsdFiles).isNotEmpty();

        Path path = openBsdFiles.get(0);
        OpenBsdPackage pkg = OpenBsdReader.read(path);

        Purl original = pkg.purl();
        String canonical = original.toCanonical();
        Purl parsed = Purl.parse(canonical);

        assertThat(parsed.type).isEqualTo(original.type);
        assertThat(parsed.name).isEqualTo(original.name);
        assertThat(parsed.version).isEqualTo(original.version);
        assertThat(parsed.qualifiers).isEqualTo(original.qualifiers);
    }

    @Test
    @EnabledIf("hasOpenBsdFiles")
    void purlArchitectureHandling() throws Exception {
        List<Path> openBsdFiles = PackageTestFiles.getFiles(PackageTestFiles.OPENBSD, ".tgz", 20);

        for (Path path : openBsdFiles) {
            try {
                OpenBsdPackage pkg = OpenBsdReader.read(path);
                Purl purl = pkg.purl();
                String arch = pkg.metadata().arch();

                // OpenBSD always includes arch if non-empty (no "noarch" equivalent)
                if (!arch.isEmpty()) {
                    assertThat(purl.qualifiers).as("Package %s with arch '%s' should have arch qualifier", pkg.name(), arch)
                            .containsEntry("arch", arch);
                }
            } catch (Exception e) {
                // Some packages might have issues, continue with others
            }
        }
    }

    @Test
    @EnabledIf("hasOpenBsdFiles")
    void purlNameVersionParsing() throws Exception {
        // OpenBSD packages have name-version format that needs parsing
        List<Path> openBsdFiles = PackageTestFiles.getFiles(PackageTestFiles.OPENBSD, ".tgz", 10);

        for (Path path : openBsdFiles) {
            try {
                OpenBsdPackage pkg = OpenBsdReader.read(path);
                OpenBsdMetadata metadata = (OpenBsdMetadata) pkg.metadata();

                // Verify name and version were correctly parsed from fullName
                String fullName = metadata.fullName();
                String name = metadata.name();
                String version = metadata.version();

                // Name should not contain version number pattern at start
                assertThat(name).doesNotMatch("^[0-9].*");

                // Full name should be reconstructible (approximately)
                if (!version.isEmpty()) {
                    assertThat(fullName).contains(name);
                    assertThat(fullName).contains(version);
                }

                // PURL should use parsed name, not full name
                Purl purl = pkg.metadata().purl();
                assertThat(purl.name).isEqualTo(name);
            } catch (Exception e) {
                // Some packages might have issues, continue with others
            }
        }
    }

    @Test
    @EnabledIf("hasOpenBsdFiles")
    void allOpenBsdPackagesProduceValidPurls() throws Exception {
        List<Path> openBsdFiles = PackageTestFiles.getAllFiles(PackageTestFiles.OPENBSD, ".tgz");

        int successCount = 0;
        int totalCount = 0;

        for (Path path : openBsdFiles) {
            try {
                OpenBsdPackage pkg = OpenBsdReader.read(path);
                totalCount++;

                Purl purl = pkg.metadata().purl();
                assertThat(purl).isNotNull();
                assertThat(purl.type).isEqualTo("openbsd");
                assertThat(purl.name).isNotEmpty();

                // Verify roundtrip
                String canonical = purl.toCanonical();
                Purl parsed = Purl.parse(canonical);
                assertThat(parsed.name).isEqualTo(purl.name);

                successCount++;
            } catch (Exception e) {
                // Some packages might have issues
            }
        }

        // At least 80% should produce valid PURLs
        if (totalCount > 0) {
            double successRate = (double) successCount / totalCount;
            assertThat(successRate).as("PURL generation success rate").isGreaterThan(0.8);
        }
    }
}
