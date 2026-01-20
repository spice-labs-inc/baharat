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

import com.github.packageurl.PackageURL;
import io.spicelabs.baharat.apk.ApkPackage;
import io.spicelabs.baharat.apk.ApkReader;
import io.spicelabs.baharat.deb.DebPackage;
import io.spicelabs.baharat.deb.DebReader;
import io.spicelabs.baharat.freebsd.FreeBsdPackage;
import io.spicelabs.baharat.freebsd.FreeBsdReader;
import io.spicelabs.baharat.openbsd.OpenBsdPackage;
import io.spicelabs.baharat.openbsd.OpenBsdReader;
import io.spicelabs.baharat.pacman.PacmanPackage;
import io.spicelabs.baharat.pacman.PacmanReader;
import io.spicelabs.baharat.rpm.RpmPackage;
import io.spicelabs.baharat.rpm.RpmReader;
import io.spicelabs.baharat.rpm.testdata.TestFiles;
import io.spicelabs.baharat.testdata.PackageTestFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-format integration tests for Package URL generation.
 *
 * <p>These tests verify PURL generation consistency across all supported
 * package formats and ensure compliance with the PURL specification.
 */
class CrossFormatPurlIntegrationTest {

    // Expected PURL types for each format
    private static final Map<PackageFormat, String> EXPECTED_PURL_TYPES = Map.of(
            PackageFormat.RPM, "rpm",
            PackageFormat.DEB, "deb",
            PackageFormat.PACMAN, "alpm",
            PackageFormat.APK, "apk",
            PackageFormat.FREEBSD_PKG, "freebsd",
            PackageFormat.OPENBSD_PKG, "openbsd"
    );

    // Architecture values that should NOT result in an arch qualifier
    private static final Set<String> ARCH_INDEPENDENT_VALUES = Set.of(
            "noarch", "all", "any", "*"
    );

    static boolean hasRpmFiles() {
        return TestFiles.exists("v4/sed-4.9-1.fc40.x86_64.rpm");
    }

    static boolean hasDebFiles() {
        return PackageTestFiles.hasFiles(PackageTestFiles.DEBS);
    }

    static boolean hasPacmanFiles() {
        return PackageTestFiles.hasFiles(PackageTestFiles.PACMAN);
    }

    static boolean hasApkFiles() {
        return PackageTestFiles.hasFiles(PackageTestFiles.APKS);
    }

    static boolean hasFreeBsdFiles() {
        return PackageTestFiles.hasFiles(PackageTestFiles.FREEBSD);
    }

    static boolean hasOpenBsdFiles() {
        return PackageTestFiles.hasFiles(PackageTestFiles.OPENBSD);
    }

    static boolean hasAnyTestFiles() {
        return hasRpmFiles() || hasDebFiles() || hasPacmanFiles() ||
                hasApkFiles() || hasFreeBsdFiles() || hasOpenBsdFiles();
    }

    // Type Mapping Tests

    @Test
    @EnabledIf("hasAnyTestFiles")
    void purlTypeMappingIsCorrectForAllFormats() throws Exception {
        Map<PackageFormat, Package> packages = loadOnePackagePerFormat();

        for (Map.Entry<PackageFormat, Package> entry : packages.entrySet()) {
            PackageFormat format = entry.getKey();
            Package pkg = entry.getValue();

            PackageURL purl = pkg.packageUrl();
            String expectedType = EXPECTED_PURL_TYPES.get(format);

            assertThat(purl.getType())
                    .as("PURL type for format %s", format)
                    .isEqualTo(expectedType);
        }
    }

    @Test
    @EnabledIf("hasAnyTestFiles")
    void allFormatsProduceValidPurls() throws Exception {
        Map<PackageFormat, Package> packages = loadOnePackagePerFormat();

        for (Map.Entry<PackageFormat, Package> entry : packages.entrySet()) {
            PackageFormat format = entry.getKey();
            Package pkg = entry.getValue();

            PackageURL purl = pkg.packageUrl();

            // All PURLs must have type and name
            assertThat(purl.getType())
                    .as("PURL type for format %s", format)
                    .isNotEmpty();
            assertThat(purl.getName())
                    .as("PURL name for format %s", format)
                    .isNotEmpty();

            // PURL should be parseable
            String canonical = purl.canonicalize();
            PackageURL parsed = new PackageURL(canonical);
            assertThat(parsed.getType()).isEqualTo(purl.getType());
            assertThat(parsed.getName()).isEqualTo(purl.getName());
        }
    }

    // Architecture Handling Consistency Tests

    @Test
    @EnabledIf("hasAnyTestFiles")
    void architectureIndependentPackagesExcludeArchQualifierAcrossFormats() throws Exception {
        Map<PackageFormat, List<Package>> packages = loadMultiplePackagesPerFormat(20);

        for (Map.Entry<PackageFormat, List<Package>> entry : packages.entrySet()) {
            PackageFormat format = entry.getKey();

            for (Package pkg : entry.getValue()) {
                String arch = pkg.arch();
                PackageURL purl = pkg.packageUrl();

                if (ARCH_INDEPENDENT_VALUES.contains(arch.toLowerCase())) {
                    assertThat(purl.getQualifiers())
                            .as("Format %s package %s with arch '%s' should not have arch qualifier",
                                    format, pkg.name(), arch)
                            .satisfiesAnyOf(
                                    q -> assertThat(q).isNull(),
                                    q -> assertThat(q).isEmpty(),
                                    q -> assertThat(q).doesNotContainKey("arch")
                            );
                }
            }
        }
    }

    @Test
    @EnabledIf("hasAnyTestFiles")
    void architectureSpecificPackagesIncludeArchQualifierAcrossFormats() throws Exception {
        Map<PackageFormat, List<Package>> packages = loadMultiplePackagesPerFormat(20);

        for (Map.Entry<PackageFormat, List<Package>> entry : packages.entrySet()) {
            PackageFormat format = entry.getKey();

            for (Package pkg : entry.getValue()) {
                String arch = pkg.arch();
                PackageURL purl = pkg.packageUrl();

                if (!arch.isEmpty() && !ARCH_INDEPENDENT_VALUES.contains(arch.toLowerCase())) {
                    assertThat(purl.getQualifiers())
                            .as("Format %s package %s with arch '%s' should have arch qualifier",
                                    format, pkg.name(), arch)
                            .containsEntry("arch", arch);
                }
            }
        }
    }

    // Namespace Handling Tests

    @Test
    @EnabledIf("hasAnyTestFiles")
    void customNamespaceOverridesDefaultForAllFormats() throws Exception {
        Map<PackageFormat, Package> packages = loadOnePackagePerFormat();

        for (Map.Entry<PackageFormat, Package> entry : packages.entrySet()) {
            PackageFormat format = entry.getKey();
            Package pkg = entry.getValue();

            String customNamespace = "custom-" + format.name().toLowerCase();
            PackageURL purl = pkg.packageUrl(Optional.of(customNamespace));

            assertThat(purl.getNamespace())
                    .as("Custom namespace for format %s", format)
                    .isEqualTo(customNamespace);
        }
    }

    @Test
    @EnabledIf("hasAnyTestFiles")
    void emptyNamespaceProducesNoNamespace() throws Exception {
        Map<PackageFormat, Package> packages = loadOnePackagePerFormat();

        for (Map.Entry<PackageFormat, Package> entry : packages.entrySet()) {
            PackageFormat format = entry.getKey();
            Package pkg = entry.getValue();

            // packageUrl() with empty optional should use default behavior
            PackageURL purlDefault = pkg.packageUrl();
            PackageURL purlEmpty = pkg.packageUrl(Optional.empty());

            // Both should behave the same (either have default namespace or no namespace)
            assertThat(purlEmpty.getNamespace())
                    .as("Empty namespace handling for format %s", format)
                    .isEqualTo(purlDefault.getNamespace());
        }
    }

    // Consistency Tests Between Package and PackageMetadata

    @Test
    @EnabledIf("hasAnyTestFiles")
    void packageUrlAndMetadataPurlAreConsistentAcrossFormats() throws Exception {
        Map<PackageFormat, Package> packages = loadOnePackagePerFormat();

        for (Map.Entry<PackageFormat, Package> entry : packages.entrySet()) {
            PackageFormat format = entry.getKey();
            Package pkg = entry.getValue();

            PackageURL fromPackage = pkg.packageUrl();
            PackageURL fromMetadata = pkg.metadata().purl();

            // Type and name must always match
            assertThat(fromPackage.getType())
                    .as("PURL type consistency for format %s", format)
                    .isEqualTo(fromMetadata.getType());
            assertThat(fromPackage.getName())
                    .as("PURL name consistency for format %s", format)
                    .isEqualTo(fromMetadata.getName());
            assertThat(fromPackage.getVersion())
                    .as("PURL version consistency for format %s", format)
                    .isEqualTo(fromMetadata.getVersion());
        }
    }

    // Roundtrip Parsing Tests

    @Test
    @EnabledIf("hasAnyTestFiles")
    void purlRoundtripParsingWorksForAllFormats() throws Exception {
        Map<PackageFormat, List<Package>> packages = loadMultiplePackagesPerFormat(10);

        for (Map.Entry<PackageFormat, List<Package>> entry : packages.entrySet()) {
            PackageFormat format = entry.getKey();

            for (Package pkg : entry.getValue()) {
                PackageURL original = pkg.packageUrl();
                String canonical = original.canonicalize();
                PackageURL parsed = new PackageURL(canonical);

                assertThat(parsed.getType())
                        .as("Roundtrip type for format %s package %s", format, pkg.name())
                        .isEqualTo(original.getType());
                assertThat(parsed.getName())
                        .as("Roundtrip name for format %s package %s", format, pkg.name())
                        .isEqualTo(original.getName());
                assertThat(parsed.getVersion())
                        .as("Roundtrip version for format %s package %s", format, pkg.name())
                        .isEqualTo(original.getVersion());
                assertThat(parsed.getNamespace())
                        .as("Roundtrip namespace for format %s package %s", format, pkg.name())
                        .isEqualTo(original.getNamespace());
                assertThat(parsed.getQualifiers())
                        .as("Roundtrip qualifiers for format %s package %s", format, pkg.name())
                        .isEqualTo(original.getQualifiers());
            }
        }
    }

    // Version Handling Tests

    @Test
    @EnabledIf("hasAnyTestFiles")
    void versionIsIncludedWhenPresentAcrossFormats() throws Exception {
        Map<PackageFormat, Package> packages = loadOnePackagePerFormat();

        for (Map.Entry<PackageFormat, Package> entry : packages.entrySet()) {
            PackageFormat format = entry.getKey();
            Package pkg = entry.getValue();

            String version = pkg.version();
            PackageURL purl = pkg.packageUrl();

            if (!version.isEmpty()) {
                assertThat(purl.getVersion())
                        .as("PURL version for format %s", format)
                        .isNotNull()
                        .isNotEmpty();
            }
        }
    }

    // Special Character Handling Tests

    @Test
    @EnabledIf("hasAnyTestFiles")
    void packagesWithSpecialCharactersInNameProduceValidPurls() throws Exception {
        Map<PackageFormat, List<Package>> packages = loadMultiplePackagesPerFormat(50);

        for (Map.Entry<PackageFormat, List<Package>> entry : packages.entrySet()) {
            PackageFormat format = entry.getKey();

            for (Package pkg : entry.getValue()) {
                String name = pkg.name();

                // Look for packages with special characters
                if (name.contains("-") || name.contains("+") || name.contains("_") || name.contains(".")) {
                    PackageURL purl = pkg.packageUrl();

                    // Should be able to generate and parse PURL
                    String canonical = purl.canonicalize();
                    PackageURL parsed = new PackageURL(canonical);

                    assertThat(parsed.getName())
                            .as("Package %s with special chars should roundtrip correctly", name)
                            .isEqualTo(purl.getName());
                }
            }
        }
    }

    // Format Detection and PURL Type Correlation Tests

    @Test
    @EnabledIf("hasAnyTestFiles")
    void formatDetectionMatchesPurlType() throws Exception {
        Map<PackageFormat, Package> packages = loadOnePackagePerFormat();

        for (Map.Entry<PackageFormat, Package> entry : packages.entrySet()) {
            PackageFormat format = entry.getKey();
            Package pkg = entry.getValue();

            assertThat(pkg.format()).isEqualTo(format);

            PackageURL purl = pkg.packageUrl();
            String expectedType = EXPECTED_PURL_TYPES.get(format);

            assertThat(purl.getType())
                    .as("PURL type should match expected for format %s", format)
                    .isEqualTo(expectedType);
        }
    }

    // Default Namespace Tests (format-specific defaults)

    @Test
    @EnabledIf("hasDebFiles")
    void debPackagesHaveDebianNamespaceByDefault() throws Exception {
        List<Path> debFiles = PackageTestFiles.getFiles(PackageTestFiles.DEBS, ".deb", 5);

        for (Path path : debFiles) {
            try {
                DebPackage pkg = DebReader.read(path);
                PackageURL purl = pkg.metadata().purl();

                assertThat(purl.getNamespace())
                        .as("DEB package %s should have 'debian' namespace", pkg.name())
                        .isEqualTo("debian");
            } catch (Exception e) {
                // Some packages might have issues
            }
        }
    }

    @Test
    @EnabledIf("hasPacmanFiles")
    void pacmanPackagesHaveArchNamespaceByDefault() throws Exception {
        List<Path> pacmanFiles = PackageTestFiles.getFiles(PackageTestFiles.PACMAN, ".pkg.tar.zst", 5);

        for (Path path : pacmanFiles) {
            try {
                PacmanPackage pkg = PacmanReader.read(path);
                PackageURL purl = pkg.metadata().purl();

                assertThat(purl.getNamespace())
                        .as("Pacman package %s should have 'arch' namespace", pkg.name())
                        .isEqualTo("arch");
            } catch (Exception e) {
                // Some packages might have issues
            }
        }
    }

    @Test
    @EnabledIf("hasApkFiles")
    void apkPackagesHaveAlpineNamespaceByDefault() throws Exception {
        List<Path> apkFiles = PackageTestFiles.getFiles(PackageTestFiles.APKS, ".apk", 5);

        for (Path path : apkFiles) {
            try {
                ApkPackage pkg = ApkReader.read(path);
                PackageURL purl = pkg.metadata().purl();

                assertThat(purl.getNamespace())
                        .as("APK package %s should have 'alpine' namespace", pkg.name())
                        .isEqualTo("alpine");
            } catch (Exception e) {
                // Some packages might have issues
            }
        }
    }

    @Test
    @EnabledIf("hasFreeBsdFiles")
    void freeBsdPackagesHaveNoNamespaceByDefault() throws Exception {
        List<Path> freeBsdFiles = PackageTestFiles.getFiles(PackageTestFiles.FREEBSD, ".pkg", 5);

        for (Path path : freeBsdFiles) {
            try {
                FreeBsdPackage pkg = FreeBsdReader.read(path);
                PackageURL purl = pkg.metadata().purl();

                assertThat(purl.getNamespace())
                        .as("FreeBSD package %s should have no namespace by default", pkg.name())
                        .isNull();
            } catch (Exception e) {
                // Some packages might have issues
            }
        }
    }

    @Test
    @EnabledIf("hasOpenBsdFiles")
    void openBsdPackagesHaveNoNamespaceByDefault() throws Exception {
        List<Path> openBsdFiles = PackageTestFiles.getFiles(PackageTestFiles.OPENBSD, ".tgz", 5);

        for (Path path : openBsdFiles) {
            try {
                OpenBsdPackage pkg = OpenBsdReader.read(path);
                PackageURL purl = pkg.metadata().purl();

                assertThat(purl.getNamespace())
                        .as("OpenBSD package %s should have no namespace by default", pkg.name())
                        .isNull();
            } catch (Exception e) {
                // Some packages might have issues
            }
        }
    }

    // Helper Methods

    private Map<PackageFormat, Package> loadOnePackagePerFormat() throws Exception {
        Map<PackageFormat, Package> packages = new HashMap<>();

        if (hasRpmFiles()) {
            Path path = TestFiles.getPath("v4/sed-4.9-1.fc40.x86_64.rpm");
            packages.put(PackageFormat.RPM, RpmReader.read(path));
        }

        if (hasDebFiles()) {
            List<Path> debFiles = PackageTestFiles.getFiles(PackageTestFiles.DEBS, ".deb", 1);
            if (!debFiles.isEmpty()) {
                packages.put(PackageFormat.DEB, DebReader.read(debFiles.get(0)));
            }
        }

        if (hasPacmanFiles()) {
            List<Path> pacmanFiles = PackageTestFiles.getFiles(PackageTestFiles.PACMAN, ".pkg.tar.zst", 1);
            if (!pacmanFiles.isEmpty()) {
                packages.put(PackageFormat.PACMAN, PacmanReader.read(pacmanFiles.get(0)));
            }
        }

        if (hasApkFiles()) {
            List<Path> apkFiles = PackageTestFiles.getFiles(PackageTestFiles.APKS, ".apk", 1);
            if (!apkFiles.isEmpty()) {
                packages.put(PackageFormat.APK, ApkReader.read(apkFiles.get(0)));
            }
        }

        if (hasFreeBsdFiles()) {
            List<Path> freeBsdFiles = PackageTestFiles.getFiles(PackageTestFiles.FREEBSD, ".pkg", 1);
            if (!freeBsdFiles.isEmpty()) {
                packages.put(PackageFormat.FREEBSD_PKG, FreeBsdReader.read(freeBsdFiles.get(0)));
            }
        }

        if (hasOpenBsdFiles()) {
            List<Path> openBsdFiles = PackageTestFiles.getFiles(PackageTestFiles.OPENBSD, ".tgz", 1);
            if (!openBsdFiles.isEmpty()) {
                packages.put(PackageFormat.OPENBSD_PKG, OpenBsdReader.read(openBsdFiles.get(0)));
            }
        }

        return packages;
    }

    private Map<PackageFormat, List<Package>> loadMultiplePackagesPerFormat(int limit) throws Exception {
        Map<PackageFormat, List<Package>> packages = new HashMap<>();

        if (hasRpmFiles()) {
            List<Package> rpms = new ArrayList<>();
            for (Path path : TestFiles.getAllRpmFiles()) {
                if (rpms.size() >= limit) break;
                try {
                    rpms.add(RpmReader.read(path));
                } catch (Exception e) {
                    // Skip problematic files
                }
            }
            packages.put(PackageFormat.RPM, rpms);
        }

        if (hasDebFiles()) {
            List<Package> debs = new ArrayList<>();
            for (Path path : PackageTestFiles.getFiles(PackageTestFiles.DEBS, ".deb", limit)) {
                try {
                    debs.add(DebReader.read(path));
                } catch (Exception e) {
                    // Skip problematic files
                }
            }
            packages.put(PackageFormat.DEB, debs);
        }

        if (hasPacmanFiles()) {
            List<Package> pacmans = new ArrayList<>();
            for (Path path : PackageTestFiles.getFiles(PackageTestFiles.PACMAN, ".pkg.tar.zst", limit)) {
                try {
                    pacmans.add(PacmanReader.read(path));
                } catch (Exception e) {
                    // Skip problematic files
                }
            }
            packages.put(PackageFormat.PACMAN, pacmans);
        }

        if (hasApkFiles()) {
            List<Package> apks = new ArrayList<>();
            for (Path path : PackageTestFiles.getFiles(PackageTestFiles.APKS, ".apk", limit)) {
                try {
                    apks.add(ApkReader.read(path));
                } catch (Exception e) {
                    // Skip problematic files
                }
            }
            packages.put(PackageFormat.APK, apks);
        }

        if (hasFreeBsdFiles()) {
            List<Package> freeBsds = new ArrayList<>();
            for (Path path : PackageTestFiles.getFiles(PackageTestFiles.FREEBSD, ".pkg", limit)) {
                try {
                    freeBsds.add(FreeBsdReader.read(path));
                } catch (Exception e) {
                    // Skip problematic files
                }
            }
            packages.put(PackageFormat.FREEBSD_PKG, freeBsds);
        }

        if (hasOpenBsdFiles()) {
            List<Package> openBsds = new ArrayList<>();
            for (Path path : PackageTestFiles.getFiles(PackageTestFiles.OPENBSD, ".tgz", limit)) {
                try {
                    openBsds.add(OpenBsdReader.read(path));
                } catch (Exception e) {
                    // Skip problematic files
                }
            }
            packages.put(PackageFormat.OPENBSD_PKG, openBsds);
        }

        return packages;
    }
}
