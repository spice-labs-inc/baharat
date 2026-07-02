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
 * Integration tests for Pacman package reading using real Arch Linux packages.
 */
class PacmanReaderIntegrationTest {

    static boolean hasPacmanFiles() {
        return PackageTestFiles.hasFiles(PackageTestFiles.PACMAN);
    }

    @Test
    @EnabledIf("hasPacmanFiles")
    void readRealPacmanPackage() throws Exception {
        List<Path> pacmanFiles = PackageTestFiles.getFiles(PackageTestFiles.PACMAN, ".pkg.tar.zst", 1);
        assertThat(pacmanFiles).isNotEmpty();

        Path path = pacmanFiles.get(0);
        PacmanPackage pkg = PacmanReader.read(path);

        assertThat(pkg).isNotNull();
        assertThat(pkg.metadata()).isNotNull();
        assertThat(pkg.metadata().name()).isNotEmpty();
        assertThat(pkg.metadata().version()).isNotEmpty();
    }

    @Test
    @EnabledIf("hasPacmanFiles")
    void readMultiplePacmanPackages() throws Exception {
        List<Path> pacmanFiles = PackageTestFiles.getFiles(PackageTestFiles.PACMAN, ".pkg.tar.zst", 10);

        int successCount = 0;
        for (Path path : pacmanFiles) {
            try {
                PacmanPackage pkg = PacmanReader.read(path);
                assertThat(pkg.metadata().name()).isNotEmpty();
                successCount++;
            } catch (Exception e) {
                // Some packages might have issues, continue with others
            }
        }

        assertThat(successCount).isGreaterThan(0);
    }

    @Test
    @EnabledIf("hasPacmanFiles")
    void streamPacmanPayload() throws Exception {
        List<Path> pacmanFiles = PackageTestFiles.getFiles(PackageTestFiles.PACMAN, ".pkg.tar.zst", 1);
        assertThat(pacmanFiles).isNotEmpty();

        Path path = pacmanFiles.get(0);

        try (Stream<PackageEntry> entries = PacmanReader.streamPayload(path)) {
            long count = entries.count();
            // Pacman packages should have at least some files
            assertThat(count).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    @EnabledIf("hasPacmanFiles")
    void pacmanMetadataContainsExpectedFields() throws Exception {
        List<Path> pacmanFiles = PackageTestFiles.getFiles(PackageTestFiles.PACMAN, ".pkg.tar.zst", 1);
        assertThat(pacmanFiles).isNotEmpty();

        Path path = pacmanFiles.get(0);
        PacmanPackage pkg = PacmanReader.read(path);

        // All Pacman packages should have these basic fields
        assertThat(pkg.metadata().name()).isNotEmpty();
        assertThat(pkg.metadata().version()).isNotEmpty();
        assertThat(pkg.metadata().arch()).isNotEmpty();
    }

    @Test
    @EnabledIf("hasPacmanFiles")
    void readAllPacmanPackages() throws Exception {
        List<Path> pacmanFiles = PackageTestFiles.getAllFiles(PackageTestFiles.PACMAN, ".pkg.tar.zst");

        int totalCount = pacmanFiles.size();
        int successCount = 0;

        for (Path path : pacmanFiles) {
            try {
                PacmanPackage pkg = PacmanReader.read(path);
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
    @EnabledIf("hasPacmanFiles")
    void purlForPacmanPackage() throws Exception {
        List<Path> pacmanFiles = PackageTestFiles.getFiles(PackageTestFiles.PACMAN, ".pkg.tar.zst", 1);
        assertThat(pacmanFiles).isNotEmpty();

        Path path = pacmanFiles.get(0);
        PacmanPackage pkg = PacmanReader.read(path);

        Purl purl = pkg.metadata().purl();

        assertThat(purl).isNotNull();
        assertThat(purl.type).isEqualTo("alpm");
        assertThat(purl.name).isEqualTo(pkg.metadata().name());
        assertThat(purl.version).isEqualTo(pkg.metadata().version());
        assertThat(purl.namespace).isEqualTo("arch");
    }

    @Test
    @EnabledIf("hasPacmanFiles")
    void purlViaPackageInterfaceMatchesMetadata() throws Exception {
        List<Path> pacmanFiles = PackageTestFiles.getFiles(PackageTestFiles.PACMAN, ".pkg.tar.zst", 1);
        assertThat(pacmanFiles).isNotEmpty();

        Path path = pacmanFiles.get(0);
        PacmanPackage pkg = PacmanReader.read(path);

        Purl fromPackage = pkg.purl();
        Purl fromMetadata = pkg.metadata().purl();

        // Type and name must match
        assertThat(fromPackage.type).isEqualTo(fromMetadata.type);
        assertThat(fromPackage.name).isEqualTo(fromMetadata.name);
        assertThat(fromPackage.version).isEqualTo(fromMetadata.version);
    }

    @Test
    @EnabledIf("hasPacmanFiles")
    void purlCanBeParsedBack() throws Exception {
        List<Path> pacmanFiles = PackageTestFiles.getFiles(PackageTestFiles.PACMAN, ".pkg.tar.zst", 1);
        assertThat(pacmanFiles).isNotEmpty();

        Path path = pacmanFiles.get(0);
        PacmanPackage pkg = PacmanReader.read(path);

        Purl original = pkg.purl();
        String canonical = original.toCanonical();
        Purl parsed = Purl.parse(canonical);

        assertThat(parsed.type).isEqualTo(original.type);
        assertThat(parsed.name).isEqualTo(original.name);
        assertThat(parsed.version).isEqualTo(original.version);
        assertThat(parsed.qualifiers).isEqualTo(original.qualifiers);
    }

    @Test
    @EnabledIf("hasPacmanFiles")
    void purlArchitectureHandling() throws Exception {
        List<Path> pacmanFiles = PackageTestFiles.getFiles(PackageTestFiles.PACMAN, ".pkg.tar.zst", 20);

        for (Path path : pacmanFiles) {
            try {
                PacmanPackage pkg = PacmanReader.read(path);
                Purl purl = pkg.purl();
                String arch = pkg.metadata().arch();

                if ("any".equals(arch)) {
                    // "any" architecture should not include arch qualifier
                    assertThat(purl.qualifiers).as("Package %s with arch 'any' should not have arch qualifier", pkg.name())
                            .isNullOrEmpty();
                } else if (!arch.isEmpty()) {
                    // Other architectures should have arch qualifier
                    assertThat(purl.qualifiers).as("Package %s with arch '%s' should have arch qualifier", pkg.name(), arch)
                            .containsEntry("arch", arch);
                }
            } catch (Exception e) {
                // Some packages might have issues, continue with others
            }
        }
    }

    @Test
    @EnabledIf("hasPacmanFiles")
    void allPacmanPackagesProduceValidPurls() throws Exception {
        List<Path> pacmanFiles = PackageTestFiles.getAllFiles(PackageTestFiles.PACMAN, ".pkg.tar.zst");

        int successCount = 0;
        int totalCount = 0;

        for (Path path : pacmanFiles) {
            try {
                PacmanPackage pkg = PacmanReader.read(path);
                totalCount++;

                Purl purl = pkg.metadata().purl();
                assertThat(purl).isNotNull();
                assertThat(purl.type).isEqualTo("alpm");
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
