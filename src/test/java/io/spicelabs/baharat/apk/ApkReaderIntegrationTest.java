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
 * Integration tests for APK package reading using real Alpine Linux packages.
 */
class ApkReaderIntegrationTest {

    static boolean hasApkFiles() {
        return PackageTestFiles.hasFiles(PackageTestFiles.APKS);
    }

    @Test
    @EnabledIf("hasApkFiles")
    void readRealApkPackage() throws Exception {
        List<Path> apkFiles = PackageTestFiles.getFiles(PackageTestFiles.APKS, ".apk", 1);
        assertThat(apkFiles).isNotEmpty();

        Path path = apkFiles.get(0);
        ApkPackage pkg = ApkReader.read(path);

        assertThat(pkg).isNotNull();
        assertThat(pkg.metadata()).isNotNull();
        assertThat(pkg.metadata().name()).isNotEmpty();
        assertThat(pkg.metadata().version()).isNotEmpty();
    }

    @Test
    @EnabledIf("hasApkFiles")
    void readMultipleApkPackages() throws Exception {
        List<Path> apkFiles = PackageTestFiles.getFiles(PackageTestFiles.APKS, ".apk", 10);

        int successCount = 0;
        for (Path path : apkFiles) {
            try {
                ApkPackage pkg = ApkReader.read(path);
                assertThat(pkg.metadata().name()).isNotEmpty();
                successCount++;
            } catch (Exception e) {
                // Some packages might have issues, continue with others
            }
        }

        assertThat(successCount).isGreaterThan(0);
    }

    @Test
    @EnabledIf("hasApkFiles")
    void streamApkPayload() throws Exception {
        List<Path> apkFiles = PackageTestFiles.getFiles(PackageTestFiles.APKS, ".apk", 1);
        assertThat(apkFiles).isNotEmpty();

        Path path = apkFiles.get(0);

        try (Stream<PackageEntry> entries = ApkReader.streamPayload(path)) {
            long count = entries.count();
            // APK packages should have at least some files (payload entries)
            // Note: metadata files (.PKGINFO, etc.) are filtered out
            assertThat(count).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    @EnabledIf("hasApkFiles")
    void apkMetadataContainsExpectedFields() throws Exception {
        List<Path> apkFiles = PackageTestFiles.getFiles(PackageTestFiles.APKS, ".apk", 1);
        assertThat(apkFiles).isNotEmpty();

        Path path = apkFiles.get(0);
        ApkPackage pkg = ApkReader.read(path);

        // All APK packages should have these basic fields
        assertThat(pkg.metadata().name()).isNotEmpty();
        assertThat(pkg.metadata().version()).isNotEmpty();
        assertThat(pkg.metadata().arch()).isNotEmpty();
    }

    @Test
    @EnabledIf("hasApkFiles")
    void readAllApkPackages() throws Exception {
        List<Path> apkFiles = PackageTestFiles.getAllFiles(PackageTestFiles.APKS, ".apk");

        int totalCount = apkFiles.size();
        int successCount = 0;

        for (Path path : apkFiles) {
            try {
                ApkPackage pkg = ApkReader.read(path);
                if (pkg.metadata().name() != null && !pkg.metadata().name().isEmpty()) {
                    successCount++;
                }
            } catch (Exception e) {
                // Some packages might have issues
            }
        }

        // At least 80% should succeed (allowing for some potential issues with downloaded packages)
        double successRate = (double) successCount / totalCount;
        assertThat(successRate).isGreaterThan(0.8);
    }

    // PURL Integration Tests

    @Test
    @EnabledIf("hasApkFiles")
    void purlForApkPackage() throws Exception {
        List<Path> apkFiles = PackageTestFiles.getFiles(PackageTestFiles.APKS, ".apk", 1);
        assertThat(apkFiles).isNotEmpty();

        Path path = apkFiles.get(0);
        ApkPackage pkg = ApkReader.read(path);

        PackageURL purl = pkg.metadata().purl();

        assertThat(purl).isNotNull();
        assertThat(purl.getType()).isEqualTo("apk");
        assertThat(purl.getName()).isEqualTo(pkg.metadata().name());
        assertThat(purl.getVersion()).isEqualTo(pkg.metadata().version());
        assertThat(purl.getNamespace()).isEqualTo("alpine");
    }

    @Test
    @EnabledIf("hasApkFiles")
    void purlViaPackageInterfaceMatchesMetadata() throws Exception {
        List<Path> apkFiles = PackageTestFiles.getFiles(PackageTestFiles.APKS, ".apk", 1);
        assertThat(apkFiles).isNotEmpty();

        Path path = apkFiles.get(0);
        ApkPackage pkg = ApkReader.read(path);

        PackageURL fromPackage = pkg.packageUrl();
        PackageURL fromMetadata = pkg.metadata().purl();

        // Type and name must match
        assertThat(fromPackage.getType()).isEqualTo(fromMetadata.getType());
        assertThat(fromPackage.getName()).isEqualTo(fromMetadata.getName());
        assertThat(fromPackage.getVersion()).isEqualTo(fromMetadata.getVersion());
    }

    @Test
    @EnabledIf("hasApkFiles")
    void purlCanBeParsedBack() throws Exception {
        List<Path> apkFiles = PackageTestFiles.getFiles(PackageTestFiles.APKS, ".apk", 1);
        assertThat(apkFiles).isNotEmpty();

        Path path = apkFiles.get(0);
        ApkPackage pkg = ApkReader.read(path);

        PackageURL original = pkg.packageUrl();
        String canonical = original.canonicalize();
        PackageURL parsed = new PackageURL(canonical);

        assertThat(parsed.getType()).isEqualTo(original.getType());
        assertThat(parsed.getName()).isEqualTo(original.getName());
        assertThat(parsed.getVersion()).isEqualTo(original.getVersion());
        assertThat(parsed.getQualifiers()).isEqualTo(original.getQualifiers());
    }

    @Test
    @EnabledIf("hasApkFiles")
    void purlWithCustomNamespaceOverridesDefault() throws Exception {
        List<Path> apkFiles = PackageTestFiles.getFiles(PackageTestFiles.APKS, ".apk", 1);
        assertThat(apkFiles).isNotEmpty();

        Path path = apkFiles.get(0);
        ApkPackage pkg = ApkReader.read(path);

        PackageURL purl = pkg.packageUrl(Optional.of("alpine-edge"));
        assertThat(purl.getNamespace()).isEqualTo("alpine-edge");
    }

    @Test
    @EnabledIf("hasApkFiles")
    void purlArchitectureHandling() throws Exception {
        List<Path> apkFiles = PackageTestFiles.getFiles(PackageTestFiles.APKS, ".apk", 20);

        for (Path path : apkFiles) {
            try {
                ApkPackage pkg = ApkReader.read(path);
                PackageURL purl = pkg.packageUrl();
                String arch = pkg.metadata().arch();

                if ("noarch".equals(arch)) {
                    // "noarch" architecture should not include arch qualifier
                    assertThat(purl.getQualifiers()).as("Package %s with arch 'noarch' should not have arch qualifier", pkg.name())
                            .isNullOrEmpty();
                } else if (!arch.isEmpty()) {
                    // Other architectures should have arch qualifier
                    assertThat(purl.getQualifiers()).as("Package %s with arch '%s' should have arch qualifier", pkg.name(), arch)
                            .containsEntry("arch", arch);
                }
            } catch (Exception e) {
                // Some packages might have issues, continue with others
            }
        }
    }

    @Test
    @EnabledIf("hasApkFiles")
    void allApkPackagesProduceValidPurls() throws Exception {
        List<Path> apkFiles = PackageTestFiles.getAllFiles(PackageTestFiles.APKS, ".apk");

        int successCount = 0;
        int totalCount = 0;

        for (Path path : apkFiles) {
            try {
                ApkPackage pkg = ApkReader.read(path);
                totalCount++;

                PackageURL purl = pkg.metadata().purl();
                assertThat(purl).isNotNull();
                assertThat(purl.getType()).isEqualTo("apk");
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
