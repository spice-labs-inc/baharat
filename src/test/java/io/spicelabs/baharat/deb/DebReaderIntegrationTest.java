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
package io.spicelabs.baharat.deb;

import io.spicelabs.coordinates.Purl;
import io.spicelabs.baharat.PackageEntry;
import io.spicelabs.baharat.PackageFormat;
import io.spicelabs.baharat.testdata.PackageTestFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for DEB package reading.
 * Tests require real DEB files which may not be available in all environments.
 */
class DebReaderIntegrationTest {

    @TempDir
    Path tempDir;

    private static final byte[] AR_MAGIC = "!<arch>\n".getBytes(StandardCharsets.US_ASCII);

    static boolean hasDebFiles() {
        return PackageTestFiles.hasFiles(PackageTestFiles.DEBS);
    }

    @Test
    void detectDebFormatFromMagic() throws Exception {
        Path debFile = tempDir.resolve("test.deb");
        Files.write(debFile, AR_MAGIC);

        assertThat(PackageFormat.detect(debFile)).contains(PackageFormat.DEB);
    }

    @Test
    void detectDebFormatFromExtension() throws Exception {
        Path debFile = tempDir.resolve("test.deb");
        Files.write(debFile, AR_MAGIC);

        var format = PackageFormat.detect(debFile);
        assertThat(format).isPresent();
        assertThat(format.get()).isEqualTo(PackageFormat.DEB);
    }

    @Test
    void debFormatProperties() {
        assertThat(PackageFormat.DEB.extension()).isEqualTo(".deb");
        assertThat(PackageFormat.DEB.family()).isEqualTo(PackageFormat.Family.LINUX);
        assertThat(PackageFormat.DEB.magic()).isPresent();
        assertThat(PackageFormat.DEB.magic().get()).isEqualTo(AR_MAGIC);
    }

    @Test
    @EnabledIf("hasDebFiles")
    void readRealDebPackage() throws Exception {
        List<Path> debFiles = PackageTestFiles.getFiles(PackageTestFiles.DEBS, ".deb", 5);
        assertThat(debFiles).isNotEmpty();

        // Try multiple files until one succeeds
        DebPackage pkg = null;
        Exception lastException = null;
        for (Path path : debFiles) {
            try {
                pkg = DebReader.read(path);
                break;
            } catch (Exception e) {
                lastException = e;
            }
        }

        if (pkg == null) {
            throw lastException != null ? lastException : new RuntimeException("No valid DEB files found");
        }

        assertThat(pkg).isNotNull();
        assertThat(pkg.metadata()).isNotNull();
        assertThat(pkg.metadata().name()).isNotEmpty();
        assertThat(pkg.metadata().version()).isNotEmpty();
    }

    @Test
    @EnabledIf("hasDebFiles")
    void readMultipleDebPackages() throws Exception {
        List<Path> debFiles = PackageTestFiles.getFiles(PackageTestFiles.DEBS, ".deb", 10);

        int successCount = 0;
        for (Path path : debFiles) {
            try {
                DebPackage pkg = DebReader.read(path);
                assertThat(pkg.metadata().name()).isNotEmpty();
                successCount++;
            } catch (Exception e) {
                // Some packages might have issues, continue with others
            }
        }

        assertThat(successCount).isGreaterThan(0);
    }

    @Test
    @EnabledIf("hasDebFiles")
    void streamDebPayload() throws Exception {
        List<Path> debFiles = PackageTestFiles.getFiles(PackageTestFiles.DEBS, ".deb", 1);
        assertThat(debFiles).isNotEmpty();

        Path path = debFiles.get(0);

        try (Stream<PackageEntry> entries = DebReader.streamPayload(path)) {
            long count = entries.count();
            assertThat(count).isGreaterThan(0);
        }
    }

    @Test
    @EnabledIf("hasDebFiles")
    void debMetadataContainsExpectedFields() throws Exception {
        List<Path> debFiles = PackageTestFiles.getFiles(PackageTestFiles.DEBS, ".deb", 5);
        assertThat(debFiles).isNotEmpty();

        // Try multiple files until one succeeds
        DebPackage pkg = null;
        for (Path path : debFiles) {
            try {
                pkg = DebReader.read(path);
                break;
            } catch (Exception e) {
                // Continue to next file
            }
        }

        assertThat(pkg).as("At least one DEB file should be readable").isNotNull();

        // All DEB packages should have these basic fields
        assertThat(pkg.metadata().name()).isNotEmpty();
        assertThat(pkg.metadata().version()).isNotEmpty();
        assertThat(pkg.metadata().arch()).isNotEmpty();
    }

    // PURL Integration Tests

    @Test
    @EnabledIf("hasDebFiles")
    void purlForDebPackage() throws Exception {
        List<Path> debFiles = PackageTestFiles.getFiles(PackageTestFiles.DEBS, ".deb", 5);
        assertThat(debFiles).isNotEmpty();

        DebPackage pkg = null;
        Path usedPath = null;
        for (Path path : debFiles) {
            try {
                pkg = DebReader.read(path);
                usedPath = path;
                break;
            } catch (Exception e) {
                // Continue to next file
            }
        }

        assertThat(pkg).as("At least one DEB file should be readable").isNotNull();

        Purl purl = pkg.metadata().purl();

        assertThat(purl).isNotNull();
        assertThat(purl.type).isEqualTo("deb");
        assertThat(purl.name).isEqualTo(pkg.metadata().name());
        assertThat(purl.version).isEqualTo(pkg.metadata().version());

        String expectedNamespace = DebPackage.inferNamespace(usedPath.toString()).orElse("debian");
        assertThat(purl.namespace).isEqualTo(expectedNamespace);
    }

    @Test
    @EnabledIf("hasDebFiles")
    void purlViaPackageInterfaceMatchesMetadata() throws Exception {
        List<Path> debFiles = PackageTestFiles.getFiles(PackageTestFiles.DEBS, ".deb", 5);
        assertThat(debFiles).isNotEmpty();

        DebPackage pkg = null;
        for (Path path : debFiles) {
            try {
                pkg = DebReader.read(path);
                break;
            } catch (Exception e) {
                // Continue to next file
            }
        }

        assertThat(pkg).as("At least one DEB file should be readable").isNotNull();

        Purl fromPackage = pkg.purl();
        Purl fromMetadata = pkg.metadata().purl();

        // Type and name must match
        assertThat(fromPackage.type).isEqualTo(fromMetadata.type);
        assertThat(fromPackage.name).isEqualTo(fromMetadata.name);
        assertThat(fromPackage.version).isEqualTo(fromMetadata.version);
    }

    @Test
    @EnabledIf("hasDebFiles")
    void purlCanBeParsedBack() throws Exception {
        List<Path> debFiles = PackageTestFiles.getFiles(PackageTestFiles.DEBS, ".deb", 5);
        assertThat(debFiles).isNotEmpty();

        DebPackage pkg = null;
        for (Path path : debFiles) {
            try {
                pkg = DebReader.read(path);
                break;
            } catch (Exception e) {
                // Continue to next file
            }
        }

        assertThat(pkg).as("At least one DEB file should be readable").isNotNull();

        Purl original = pkg.purl();
        String canonical = original.toCanonical();
        Purl parsed = Purl.parse(canonical);

        assertThat(parsed.type).isEqualTo(original.type);
        assertThat(parsed.name).isEqualTo(original.name);
        assertThat(parsed.version).isEqualTo(original.version);
        assertThat(parsed.qualifiers).isEqualTo(original.qualifiers);
    }

    @Test
    @EnabledIf("hasDebFiles")
    void purlArchitectureHandling() throws Exception {
        List<Path> debFiles = PackageTestFiles.getFiles(PackageTestFiles.DEBS, ".deb", 20);

        for (Path path : debFiles) {
            try {
                DebPackage pkg = DebReader.read(path);
                Purl purl = pkg.purl();
                String arch = pkg.metadata().arch();

                if ("all".equals(arch)) {
                    // "all" architecture should not include arch qualifier
                    assertThat(purl.qualifiers).as("Package %s with arch 'all' should not have arch qualifier", pkg.name())
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
    @EnabledIf("hasDebFiles")
    void allDebPackagesProduceValidPurls() throws Exception {
        List<Path> debFiles = PackageTestFiles.getAllFiles(PackageTestFiles.DEBS, ".deb");

        int successCount = 0;
        int totalCount = 0;

        for (Path path : debFiles) {
            try {
                DebPackage pkg = DebReader.read(path);
                totalCount++;

                Purl purl = pkg.metadata().purl();
                assertThat(purl).isNotNull();
                assertThat(purl.type).isEqualTo("deb");
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
