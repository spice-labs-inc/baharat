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
import io.spicelabs.coordinates.Purl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-format integration tests for Package URL generation.
 *
 * <p>These tests verify PURL generation consistency across all supported
 * package formats and ensure compliance with Coordinates and the PURL specification.
 */
class CrossFormatPurlIntegrationTest {

    private static final Map<PackageFormat, String> EXPECTED_PURL_TYPES = Map.of(
            PackageFormat.RPM, "rpm",
            PackageFormat.DEB, "deb",
            PackageFormat.PACMAN, "alpm",
            PackageFormat.APK, "apk",
            PackageFormat.FREEBSD_PKG, "freebsd",
            PackageFormat.OPENBSD_PKG, "openbsd"
    );

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

    @Test
    @EnabledIf("hasAnyTestFiles")
    void purlTypeMappingIsCorrectForAllFormats() throws Exception {
        Map<PackageFormat, Package> packages = loadOnePackagePerFormat();

        for (Map.Entry<PackageFormat, Package> entry : packages.entrySet()) {
            PackageFormat format = entry.getKey();
            Package pkg = entry.getValue();

            Purl purl = pkg.purl();
            String expectedType = EXPECTED_PURL_TYPES.get(format);

            assertThat(purl.type)
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

            Purl purl = pkg.purl();

            assertThat(purl.type)
                    .as("PURL type for format %s", format)
                    .isNotEmpty();
            assertThat(purl.name)
                    .as("PURL name for format %s", format)
                    .isNotEmpty();

            String canonical = purl.toCanonical();
            Purl parsed = Purl.parse(canonical);
            assertThat(parsed.type).isEqualTo(purl.type);
            assertThat(parsed.name).isEqualTo(purl.name);
        }
    }

    @Test
    @EnabledIf("hasAnyTestFiles")
    void architectureIndependentPackagesExcludeArchQualifierAcrossFormats() throws Exception {
        Map<PackageFormat, List<Package>> packages = loadMultiplePackagesPerFormat(20);

        for (Map.Entry<PackageFormat, List<Package>> entry : packages.entrySet()) {
            PackageFormat format = entry.getKey();

            for (Package pkg : entry.getValue()) {
                String arch = pkg.arch();
                Purl purl = pkg.purl();

                if (ARCH_INDEPENDENT_VALUES.contains(arch.toLowerCase())) {
                    assertThat(purl.qualifiers)
                            .as("Format %s package %s with arch '%s' should not have arch qualifier",
                                    format, pkg.name(), arch)
                            .doesNotContainKey("arch");
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
                Purl purl = pkg.purl();

                if (!arch.isEmpty() && !ARCH_INDEPENDENT_VALUES.contains(arch.toLowerCase())) {
                    assertThat(purl.qualifiers)
                            .as("Format %s package %s with arch '%s' should have arch qualifier",
                                    format, pkg.name(), arch)
                            .containsEntry("arch", arch);
                }
            }
        }
    }

    @Test
    @EnabledIf("hasAnyTestFiles")
    void purlAndMetadataPurlAreConsistentAcrossFormats() throws Exception {
        Map<PackageFormat, Package> packages = loadOnePackagePerFormat();

        for (Map.Entry<PackageFormat, Package> entry : packages.entrySet()) {
            PackageFormat format = entry.getKey();
            Package pkg = entry.getValue();

            Purl fromPackage = pkg.purl();
            Purl fromMetadata = pkg.metadata().purl();

            assertThat(fromPackage.type)
                    .as("PURL type consistency for format %s", format)
                    .isEqualTo(fromMetadata.type);
            assertThat(fromPackage.namespace)
                    .as("PURL namespace consistency for format %s", format)
                    .isEqualTo(fromMetadata.namespace);
            assertThat(fromPackage.name)
                    .as("PURL name consistency for format %s", format)
                    .isEqualTo(fromMetadata.name);
            assertThat(fromPackage.version)
                    .as("PURL version consistency for format %s", format)
                    .isEqualTo(fromMetadata.version);
            assertThat(fromPackage.qualifiers)
                    .as("PURL qualifiers consistency for format %s", format)
                    .isEqualTo(fromMetadata.qualifiers);
        }
    }

    @Test
    @EnabledIf("hasAnyTestFiles")
    void purlRoundtripParsingWorksForAllFormats() throws Exception {
        Map<PackageFormat, List<Package>> packages = loadMultiplePackagesPerFormat(10);

        for (Map.Entry<PackageFormat, List<Package>> entry : packages.entrySet()) {
            PackageFormat format = entry.getKey();

            for (Package pkg : entry.getValue()) {
                Purl original = pkg.purl();
                String canonical = original.toCanonical();
                Purl parsed = Purl.parse(canonical);

                assertThat(parsed.type)
                        .as("Roundtrip type for format %s package %s", format, pkg.name())
                        .isEqualTo(original.type);
                assertThat(parsed.namespace)
                        .as("Roundtrip namespace for format %s package %s", format, pkg.name())
                        .isEqualTo(original.namespace);
                assertThat(parsed.name)
                        .as("Roundtrip name for format %s package %s", format, pkg.name())
                        .isEqualTo(original.name);
                assertThat(parsed.version)
                        .as("Roundtrip version for format %s package %s", format, pkg.name())
                        .isEqualTo(original.version);
                assertThat(parsed.qualifiers)
                        .as("Roundtrip qualifiers for format %s package %s", format, pkg.name())
                        .isEqualTo(original.qualifiers);
            }
        }
    }

    @Test
    @EnabledIf("hasAnyTestFiles")
    void versionIsIncludedWhenPresentAcrossFormats() throws Exception {
        Map<PackageFormat, Package> packages = loadOnePackagePerFormat();

        for (Map.Entry<PackageFormat, Package> entry : packages.entrySet()) {
            PackageFormat format = entry.getKey();
            Package pkg = entry.getValue();

            String version = pkg.version();
            Purl purl = pkg.purl();

            if (!version.isEmpty()) {
                assertThat(purl.version)
                        .as("PURL version for format %s", format)
                        .isNotNull()
                        .isNotEmpty();
            }
        }
    }

    @Test
    @EnabledIf("hasAnyTestFiles")
    void packagesWithSpecialCharactersInNameProduceValidPurls() throws Exception {
        Map<PackageFormat, List<Package>> packages = loadMultiplePackagesPerFormat(50);

        for (Map.Entry<PackageFormat, List<Package>> entry : packages.entrySet()) {
            PackageFormat format = entry.getKey();

            for (Package pkg : entry.getValue()) {
                String name = pkg.name();

                if (name.contains("-") || name.contains("+") || name.contains("_") || name.contains(".")) {
                    Purl purl = pkg.purl();

                    String canonical = purl.toCanonical();
                    Purl parsed = Purl.parse(canonical);

                    assertThat(parsed.name)
                            .as("Package %s with special chars should roundtrip correctly", name)
                            .isEqualTo(purl.name);
                }
            }
        }
    }

    @Test
    @EnabledIf("hasAnyTestFiles")
    void formatDetectionMatchesPurlType() throws Exception {
        Map<PackageFormat, Package> packages = loadOnePackagePerFormat();

        for (Map.Entry<PackageFormat, Package> entry : packages.entrySet()) {
            PackageFormat format = entry.getKey();
            Package pkg = entry.getValue();

            assertThat(pkg.format()).isEqualTo(format);

            Purl purl = pkg.purl();
            String expectedType = EXPECTED_PURL_TYPES.get(format);

            assertThat(purl.type)
                    .as("PURL type should match expected for format %s", format)
                    .isEqualTo(expectedType);
        }
    }

    @Test
    @EnabledIf("hasDebFiles")
    void debPackagesHaveDebianNamespaceByDefault() throws Exception {
        List<Path> debFiles = PackageTestFiles.getFiles(PackageTestFiles.DEBS, ".deb", 5);

        for (Path path : debFiles) {
            try {
                DebPackage pkg = DebReader.read(path);
                Purl purl = pkg.metadata().purl();

                String expectedNamespace = DebPackage.inferNamespace(path.toString()).orElse("debian");
                assertThat(purl.namespace)
                        .as("DEB package %s should have expected namespace", pkg.name())
                        .isEqualTo(expectedNamespace);
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
                Purl purl = pkg.metadata().purl();

                assertThat(purl.namespace)
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
                Purl purl = pkg.metadata().purl();

                assertThat(purl.namespace)
                        .as("APK package %s should have 'alpine' namespace", pkg.name())
                        .isEqualTo("alpine");
            } catch (Exception e) {
                // Some packages might have issues
            }
        }
    }

    @Test
    @EnabledIf("hasRpmFiles")
    void rpmPackagesHaveNamespace() throws Exception {
        List<Path> rpmFiles = TestFiles.getAllRpmFiles();

        int checked = 0;
        for (Path path : rpmFiles) {
            try {
                RpmPackage pkg = RpmReader.read(path);
                Purl purl = pkg.metadata().purl();

                assertThat(purl.namespace)
                        .as("RPM package %s should have a namespace", pkg.name())
                        .isNotNull()
                        .isNotEmpty();
                checked++;
                if (checked >= 5) break;
            } catch (Exception e) {
                // Skip problematic files
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
                Purl purl = pkg.metadata().purl();

                assertThat(purl.namespace)
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
                Purl purl = pkg.metadata().purl();

                assertThat(purl.namespace)
                        .as("OpenBSD package %s should have no namespace by default", pkg.name())
                        .isNull();
            } catch (Exception e) {
                // Some packages might have issues
            }
        }
    }

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
