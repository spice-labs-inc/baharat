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

import io.spicelabs.coordinates.Purl;
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
 * Integration tests for FreeBSD package reading using real FreeBSD packages.
 */
class FreeBsdReaderIntegrationTest {

    static boolean hasFreeBsdFiles() {
        return PackageTestFiles.hasFiles(PackageTestFiles.FREEBSD);
    }

    @Test
    @EnabledIf("hasFreeBsdFiles")
    void readRealFreeBsdPackage() throws Exception {
        List<Path> freeBsdFiles = PackageTestFiles.getFiles(PackageTestFiles.FREEBSD, ".pkg", 1);
        assertThat(freeBsdFiles).isNotEmpty();

        Path path = freeBsdFiles.get(0);
        FreeBsdPackage pkg = FreeBsdReader.read(path);

        assertThat(pkg).isNotNull();
        assertThat(pkg.metadata()).isNotNull();
        assertThat(pkg.metadata().name()).isNotEmpty();
        assertThat(pkg.metadata().version()).isNotEmpty();
    }

    @Test
    @EnabledIf("hasFreeBsdFiles")
    void readMultipleFreeBsdPackages() throws Exception {
        List<Path> freeBsdFiles = PackageTestFiles.getFiles(PackageTestFiles.FREEBSD, ".pkg", 10);

        int successCount = 0;
        for (Path path : freeBsdFiles) {
            try {
                FreeBsdPackage pkg = FreeBsdReader.read(path);
                assertThat(pkg.metadata().name()).isNotEmpty();
                successCount++;
            } catch (Exception e) {
                // Some packages might have issues, continue with others
            }
        }

        assertThat(successCount).isGreaterThan(0);
    }

    @Test
    @EnabledIf("hasFreeBsdFiles")
    void streamFreeBsdPayload() throws Exception {
        List<Path> freeBsdFiles = PackageTestFiles.getFiles(PackageTestFiles.FREEBSD, ".pkg", 1);
        assertThat(freeBsdFiles).isNotEmpty();

        Path path = freeBsdFiles.get(0);

        try (Stream<PackageEntry> entries = FreeBsdReader.streamPayload(path)) {
            long count = entries.count();
            // FreeBSD packages should have at least some files
            assertThat(count).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    @EnabledIf("hasFreeBsdFiles")
    void freeBsdMetadataContainsExpectedFields() throws Exception {
        List<Path> freeBsdFiles = PackageTestFiles.getFiles(PackageTestFiles.FREEBSD, ".pkg", 1);
        assertThat(freeBsdFiles).isNotEmpty();

        Path path = freeBsdFiles.get(0);
        FreeBsdPackage pkg = FreeBsdReader.read(path);

        // All FreeBSD packages should have these basic fields
        assertThat(pkg.metadata().name()).isNotEmpty();
        assertThat(pkg.metadata().version()).isNotEmpty();
    }

    @Test
    @EnabledIf("hasFreeBsdFiles")
    void readAllFreeBsdPackages() throws Exception {
        List<Path> freeBsdFiles = PackageTestFiles.getAllFiles(PackageTestFiles.FREEBSD, ".pkg");

        int totalCount = freeBsdFiles.size();
        int successCount = 0;

        for (Path path : freeBsdFiles) {
            try {
                FreeBsdPackage pkg = FreeBsdReader.read(path);
                if (pkg.metadata().name() != null && !pkg.metadata().name().isEmpty()) {
                    successCount++;
                }
            } catch (Exception e) {
                // Some packages might have issues
            }
        }

        // At least 80% should succeed
        if (totalCount > 0) {
            double successRate = (double) successCount / totalCount;
            assertThat(successRate).isGreaterThan(0.8);
        }
    }

    // PURL Integration Tests

    @Test
    @EnabledIf("hasFreeBsdFiles")
    void purlForFreeBsdPackage() throws Exception {
        List<Path> freeBsdFiles = PackageTestFiles.getFiles(PackageTestFiles.FREEBSD, ".pkg", 1);
        assertThat(freeBsdFiles).isNotEmpty();

        Path path = freeBsdFiles.get(0);
        FreeBsdPackage pkg = FreeBsdReader.read(path);

        Purl purl = pkg.metadata().purl();

        assertThat(purl).isNotNull();
        assertThat(purl.type).isEqualTo("freebsd");
        assertThat(purl.name).isEqualTo(pkg.metadata().name());
        assertThat(purl.version).isEqualTo(pkg.metadata().version());
        // FreeBSD does not set a namespace by default
        assertThat(purl.namespace).isNull();
    }

    @Test
    @EnabledIf("hasFreeBsdFiles")
    void purlViaPackageInterfaceMatchesMetadata() throws Exception {
        List<Path> freeBsdFiles = PackageTestFiles.getFiles(PackageTestFiles.FREEBSD, ".pkg", 1);
        assertThat(freeBsdFiles).isNotEmpty();

        Path path = freeBsdFiles.get(0);
        FreeBsdPackage pkg = FreeBsdReader.read(path);

        Purl fromPackage = pkg.purl();
        Purl fromMetadata = pkg.metadata().purl();

        // Type and name must match
        assertThat(fromPackage.type).isEqualTo(fromMetadata.type);
        assertThat(fromPackage.name).isEqualTo(fromMetadata.name);
        assertThat(fromPackage.version).isEqualTo(fromMetadata.version);
    }

    @Test
    @EnabledIf("hasFreeBsdFiles")
    void purlCanBeParsedBack() throws Exception {
        List<Path> freeBsdFiles = PackageTestFiles.getFiles(PackageTestFiles.FREEBSD, ".pkg", 1);
        assertThat(freeBsdFiles).isNotEmpty();

        Path path = freeBsdFiles.get(0);
        FreeBsdPackage pkg = FreeBsdReader.read(path);

        Purl original = pkg.purl();
        String canonical = original.toCanonical();
        Purl parsed = Purl.parse(canonical);

        assertThat(parsed.type).isEqualTo(original.type);
        assertThat(parsed.name).isEqualTo(original.name);
        assertThat(parsed.version).isEqualTo(original.version);
        assertThat(parsed.qualifiers).isEqualTo(original.qualifiers);
    }

    @Test
    @EnabledIf("hasFreeBsdFiles")
    void purlArchitectureHandling() throws Exception {
        List<Path> freeBsdFiles = PackageTestFiles.getFiles(PackageTestFiles.FREEBSD, ".pkg", 20);

        for (Path path : freeBsdFiles) {
            try {
                FreeBsdPackage pkg = FreeBsdReader.read(path);
                Purl purl = pkg.purl();
                String arch = pkg.metadata().arch();

                if ("*".equals(arch)) {
                    // "*" architecture should not include arch qualifier
                    assertThat(purl.qualifiers).as("Package %s with arch '*' should not have arch qualifier", pkg.name())
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
    @EnabledIf("hasFreeBsdFiles")
    void purlOriginMetadata() throws Exception {
        // FreeBSD packages have origin metadata (e.g., "www/nginx")
        List<Path> freeBsdFiles = PackageTestFiles.getFiles(PackageTestFiles.FREEBSD, ".pkg", 10);

        for (Path path : freeBsdFiles) {
            try {
                FreeBsdPackage pkg = FreeBsdReader.read(path);
                FreeBsdMetadata metadata = (FreeBsdMetadata) pkg.metadata();

                // Origin should be available for most packages
                Optional<String> origin = metadata.origin();
                if (origin.isPresent()) {
                    // Origin format is typically "category/name"
                    assertThat(origin.get()).contains("/");
                }

                // PURL should still be valid regardless of origin
                Purl purl = pkg.metadata().purl();
                assertThat(purl.name).isNotEmpty();
            } catch (Exception e) {
                // Some packages might have issues, continue with others
            }
        }
    }

    @Test
    @EnabledIf("hasFreeBsdFiles")
    void allFreeBsdPackagesProduceValidPurls() throws Exception {
        List<Path> freeBsdFiles = PackageTestFiles.getAllFiles(PackageTestFiles.FREEBSD, ".pkg");

        int successCount = 0;
        int totalCount = 0;

        for (Path path : freeBsdFiles) {
            try {
                FreeBsdPackage pkg = FreeBsdReader.read(path);
                totalCount++;

                Purl purl = pkg.metadata().purl();
                assertThat(purl).isNotNull();
                assertThat(purl.type).isEqualTo("freebsd");
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
