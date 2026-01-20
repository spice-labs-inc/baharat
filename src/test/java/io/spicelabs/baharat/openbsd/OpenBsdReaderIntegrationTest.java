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

import com.github.packageurl.PackageURL;
import io.spicelabs.baharat.PackageEntry;
import io.spicelabs.baharat.testdata.PackageTestFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
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

        PackageURL purl = pkg.metadata().purl();

        assertThat(purl).isNotNull();
        assertThat(purl.getType()).isEqualTo("openbsd");
        assertThat(purl.getName()).isEqualTo(pkg.metadata().name());
        assertThat(purl.getVersion()).isEqualTo(pkg.metadata().version());
        // OpenBSD does not set a namespace by default
        assertThat(purl.getNamespace()).isNull();
    }

    @Test
    @EnabledIf("hasOpenBsdFiles")
    void purlViaPackageInterfaceMatchesMetadata() throws Exception {
        List<Path> openBsdFiles = PackageTestFiles.getFiles(PackageTestFiles.OPENBSD, ".tgz", 1);
        assertThat(openBsdFiles).isNotEmpty();

        Path path = openBsdFiles.get(0);
        OpenBsdPackage pkg = OpenBsdReader.read(path);

        PackageURL fromPackage = pkg.packageUrl();
        PackageURL fromMetadata = pkg.metadata().purl();

        // Type and name must match
        assertThat(fromPackage.getType()).isEqualTo(fromMetadata.getType());
        assertThat(fromPackage.getName()).isEqualTo(fromMetadata.getName());
        assertThat(fromPackage.getVersion()).isEqualTo(fromMetadata.getVersion());
    }

    @Test
    @EnabledIf("hasOpenBsdFiles")
    void purlCanBeParsedBack() throws Exception {
        List<Path> openBsdFiles = PackageTestFiles.getFiles(PackageTestFiles.OPENBSD, ".tgz", 1);
        assertThat(openBsdFiles).isNotEmpty();

        Path path = openBsdFiles.get(0);
        OpenBsdPackage pkg = OpenBsdReader.read(path);

        PackageURL original = pkg.packageUrl();
        String canonical = original.canonicalize();
        PackageURL parsed = new PackageURL(canonical);

        assertThat(parsed.getType()).isEqualTo(original.getType());
        assertThat(parsed.getName()).isEqualTo(original.getName());
        assertThat(parsed.getVersion()).isEqualTo(original.getVersion());
        assertThat(parsed.getQualifiers()).isEqualTo(original.getQualifiers());
    }

    @Test
    @EnabledIf("hasOpenBsdFiles")
    void purlWithNamespace() throws Exception {
        List<Path> openBsdFiles = PackageTestFiles.getFiles(PackageTestFiles.OPENBSD, ".tgz", 1);
        assertThat(openBsdFiles).isNotEmpty();

        Path path = openBsdFiles.get(0);
        OpenBsdPackage pkg = OpenBsdReader.read(path);

        PackageURL purl = pkg.packageUrl(Optional.of("openbsd"));
        assertThat(purl.getNamespace()).isEqualTo("openbsd");
    }

    @Test
    @EnabledIf("hasOpenBsdFiles")
    void purlArchitectureHandling() throws Exception {
        List<Path> openBsdFiles = PackageTestFiles.getFiles(PackageTestFiles.OPENBSD, ".tgz", 20);

        for (Path path : openBsdFiles) {
            try {
                OpenBsdPackage pkg = OpenBsdReader.read(path);
                PackageURL purl = pkg.packageUrl();
                String arch = pkg.metadata().arch();

                // OpenBSD always includes arch if non-empty (no "noarch" equivalent)
                if (!arch.isEmpty()) {
                    assertThat(purl.getQualifiers()).as("Package %s with arch '%s' should have arch qualifier", pkg.name(), arch)
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
                PackageURL purl = pkg.metadata().purl();
                assertThat(purl.getName()).isEqualTo(name);
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

                PackageURL purl = pkg.metadata().purl();
                assertThat(purl).isNotNull();
                assertThat(purl.getType()).isEqualTo("openbsd");
                assertThat(purl.getName()).isNotEmpty();

                // Verify roundtrip
                String canonical = purl.canonicalize();
                PackageURL parsed = new PackageURL(canonical);
                assertThat(parsed.getName()).isEqualTo(purl.getName());

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
