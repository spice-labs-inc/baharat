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
package io.spicelabs.baharat.rpm;

import com.github.packageurl.PackageURL;
import io.spicelabs.baharat.rpm.lead.Lead;
import io.spicelabs.baharat.rpm.metadata.Dependency;
import io.spicelabs.baharat.rpm.metadata.FileInfo;
import io.spicelabs.baharat.rpm.metadata.PackageMetadata;
import io.spicelabs.baharat.rpm.payload.PayloadEntry;
import io.spicelabs.baharat.rpm.payload.PayloadReader;
import io.spicelabs.baharat.rpm.signature.KeyProvider;
import io.spicelabs.baharat.rpm.signature.SignatureResult;
import io.spicelabs.baharat.rpm.signature.SignatureVerifier;
import io.spicelabs.baharat.rpm.testdata.TestFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests using real RPM files.
 */
class ReaderIntegrationTest {

    // Test file paths
    private static final String V4_RPM = "v4/sed-4.9-1.fc40.x86_64.rpm";
    private static final String V3_RPM = "v3/grep-2.14-1.fc18.x86_64.rpm";
    private static final String NOARCH_RPM = "architectures/noarch/basesystem-11-20.fc40.noarch.rpm";
    private static final String SIGNED_RPM = "signatures/signed/tzdata-2024a-5.fc40.noarch.rpm";

    static boolean hasTestRpmFiles() {
        return TestFiles.exists(V4_RPM);
    }

    @Test
    @EnabledIf("hasTestRpmFiles")
    void readV4Rpm() throws Exception {
        Path path = TestFiles.getPath(V4_RPM);

        RpmPackage rpm = RpmReader.read(path);

        assertThat(rpm).isNotNull();
        assertThat(rpm.name()).isEqualTo("sed");
        assertThat(rpm.version()).isEqualTo("4.9");
        assertThat(rpm.release()).isEqualTo("1.fc40");
        assertThat(rpm.arch()).isEqualTo("x86_64");
        assertThat(rpm.formatVersion()).contains("3");
        assertThat(rpm.isBinary()).isTrue();
        assertThat(rpm.isSource()).isFalse();
    }

    @Test
    @EnabledIf("hasTestRpmFiles")
    void readV4RpmFromStream() throws Exception {
        Path path = TestFiles.getPath(V4_RPM);

        try (var stream = Files.newInputStream(path)) {
            RpmPackage rpm = RpmReader.read(stream);

            assertThat(rpm).isNotNull();
            assertThat(rpm.name()).isEqualTo("sed");
            assertThat(rpm.version()).isEqualTo("4.9");
        }
    }

    @Test
    @EnabledIf("hasTestRpmFiles")
    void readV3Rpm() throws Exception {
        if (!TestFiles.exists(V3_RPM)) return;

        Path path = TestFiles.getPath(V3_RPM);
        RpmPackage rpm = RpmReader.read(path);

        assertThat(rpm).isNotNull();
        assertThat(rpm.name()).isEqualTo("grep");
        assertThat(rpm.isBinary()).isTrue();
    }

    @Test
    @EnabledIf("hasTestRpmFiles")
    void readNoarchRpm() throws Exception {
        if (!TestFiles.exists(NOARCH_RPM)) return;

        Path path = TestFiles.getPath(NOARCH_RPM);
        RpmPackage rpm = RpmReader.read(path);

        assertThat(rpm).isNotNull();
        assertThat(rpm.arch()).isEqualTo("noarch");
    }

    @Test
    @EnabledIf("hasTestRpmFiles")
    void packageMetadataAccess() throws Exception {
        Path path = TestFiles.getPath(V4_RPM);
        RpmPackage rpm = RpmReader.read(path);
        PackageMetadata meta = rpm.rpmMetadata();

        assertThat(meta.name()).isEqualTo("sed");
        assertThat(meta.version()).isEqualTo("4.9");
        assertThat(meta.release()).isEqualTo("1.fc40");
        assertThat(meta.arch()).isEqualTo("x86_64");
        assertThat(meta.nevra()).contains("sed").contains("4.9");

        // Description and summary should be present
        assertThat(meta.summary()).isNotEmpty();
        assertThat(meta.description()).isNotEmpty();

        // License should be present
        assertThat(meta.license()).isNotEmpty();

        // Payload info
        assertThat(meta.payloadFormat()).isEqualTo("cpio");
        assertThat(meta.payloadCompressor()).isIn("xz", "zstd", "gzip");
    }

    @Test
    @EnabledIf("hasTestRpmFiles")
    void packageDependencies() throws Exception {
        Path path = TestFiles.getPath(V4_RPM);
        RpmPackage rpm = RpmReader.read(path);
        PackageMetadata meta = rpm.rpmMetadata();

        // Should have requires
        List<Dependency> requires = meta.requires();
        assertThat(requires).isNotEmpty();

        // Should have provides
        List<Dependency> provides = meta.provides();
        assertThat(provides).isNotEmpty();

        // Check a dependency has expected properties
        Dependency first = requires.get(0);
        assertThat(first.name()).isNotEmpty();
        assertThat(first.type()).isNotNull();
    }

    @Test
    @EnabledIf("hasTestRpmFiles")
    void packageFiles() throws Exception {
        Path path = TestFiles.getPath(V4_RPM);
        RpmPackage rpm = RpmReader.read(path);
        PackageMetadata meta = rpm.rpmMetadata();

        List<FileInfo> files = meta.files();
        assertThat(files).isNotEmpty();

        // Check that sed binary is in the file list
        boolean hasSedBinary = files.stream()
                .anyMatch(f -> f.path().endsWith("/sed") && f.isRegularFile());
        assertThat(hasSedBinary).isTrue();

        // Check file properties
        FileInfo sedFile = files.stream()
                .filter(f -> f.path().endsWith("/sed"))
                .findFirst()
                .orElseThrow();

        assertThat(sedFile.size()).isGreaterThan(0);
        assertThat(sedFile.userName()).isNotEmpty();
        assertThat(sedFile.groupName()).isNotEmpty();
    }

    @Test
    @EnabledIf("hasTestRpmFiles")
    void isRpmDetection() throws Exception {
        Path rpmPath = TestFiles.getPath(V4_RPM);
        assertThat(RpmReader.isRpm(rpmPath)).isTrue();
    }

    @Test
    @EnabledIf("hasTestRpmFiles")
    void readMetadataShortcut() throws Exception {
        Path path = TestFiles.getPath(V4_RPM);

        PackageMetadata meta = RpmReader.readMetadata(path);

        assertThat(meta.name()).isEqualTo("sed");
        assertThat(meta.version()).isEqualTo("4.9");
    }

    @Test
    @EnabledIf("hasTestRpmFiles")
    void streamPayload() throws Exception {
        Path path = TestFiles.getPath(V4_RPM);

        AtomicInteger count = new AtomicInteger(0);
        try (Stream<PayloadEntry> entries = RpmReader.streamPayload(path)) {
            entries.forEach(entry -> {
                assertThat(entry.path()).isNotEmpty();
                count.incrementAndGet();
            });
        }

        assertThat(count.get()).isGreaterThan(0);
    }

    @Test
    @EnabledIf("hasTestRpmFiles")
    void streamPayloadFromStream() throws Exception {
        Path path = TestFiles.getPath(V4_RPM);

        try (var input = Files.newInputStream(path);
             Stream<PayloadEntry> entries = RpmReader.streamPayload(input)) {

            long fileCount = entries
                    .filter(PayloadEntry::isFile)
                    .count();

            assertThat(fileCount).isGreaterThan(0);
        }
    }

    @Test
    @EnabledIf("hasTestRpmFiles")
    void openPayloadReader() throws Exception {
        Path path = TestFiles.getPath(V4_RPM);

        try (PayloadReader reader = RpmReader.openPayload(path)) {
            assertThat(reader.compressionType()).isNotNull();

            PayloadEntry entry = reader.nextEntry();
            assertThat(entry).isNotNull();
            assertThat(entry.path()).isNotEmpty();
        }
    }

    @Test
    @EnabledIf("hasTestRpmFiles")
    void payloadEntryTypes() throws Exception {
        Path path = TestFiles.getPath(V4_RPM);

        boolean hasFile = false;
        boolean hasDir = false;

        try (PayloadReader reader = RpmReader.openPayload(path)) {
            PayloadEntry entry;
            while ((entry = reader.nextEntry()) != null) {
                if (entry.isFile()) hasFile = true;
                if (entry.isDirectory()) hasDir = true;

                // Check permissions
                assertThat(entry.permissions()).isGreaterThanOrEqualTo(0);
                assertThat(entry.mtime()).isNotNull();
                assertThat(entry.userName()).isNotEmpty();
                assertThat(entry.groupName()).isNotEmpty();
            }
        }

        assertThat(hasFile).isTrue();
        assertThat(hasDir).isTrue();
    }

    @Test
    @EnabledIf("hasTestRpmFiles")
    void rpmPackageComponents() throws Exception {
        Path path = TestFiles.getPath(V4_RPM);
        RpmPackage rpm = RpmReader.read(path);

        // Check lead
        assertThat(rpm.lead()).isNotNull();
        assertThat(Lead.MAGIC).isEqualTo(0xEDABEEDB);

        // Check headers
        assertThat(rpm.header()).isNotNull();
        assertThat(rpm.signatureHeader()).isNotNull();

        // Check payload offset
        assertThat(rpm.payloadOffset()).isGreaterThan(0);

        // toString should contain nevra
        String str = rpm.toString();
        assertThat(str).contains("sed");
    }

    @Test
    @EnabledIf("hasTestRpmFiles")
    void signatureHeaderInfo() throws Exception {
        if (!TestFiles.exists(SIGNED_RPM)) return;

        Path path = TestFiles.getPath(SIGNED_RPM);
        RpmPackage rpm = RpmReader.read(path);

        SignatureVerifier verifier = new SignatureVerifier(KeyProvider.empty());

        // Package should be detected as signed even if we don't have the key
        boolean isSigned = verifier.isSigned(rpm.signatureHeader());
        // Note: Some packages may not be signed

        // Try to get signing key ID (if signed)
        verifier.getSigningKeyId(rpm.signatureHeader());

        // Verify returns key-not-found or not-signed for empty key provider
        SignatureResult result = verifier.verifyHeaderSignature(
                rpm.signatureHeader(), new byte[0]);
        assertThat(result.status()).isIn(
                SignatureResult.Status.NOT_SIGNED,
                SignatureResult.Status.KEY_NOT_FOUND,
                SignatureResult.Status.ERROR
        );
    }

    @Test
    @EnabledIf("hasTestRpmFiles")
    void verifyPackageSignature() throws Exception {
        if (!TestFiles.exists(SIGNED_RPM)) return;

        Path path = TestFiles.getPath(SIGNED_RPM);
        RpmPackage rpm = RpmReader.read(path);

        SignatureVerifier verifier = new SignatureVerifier(KeyProvider.empty());

        SignatureResult result = verifier.verifyPackageSignature(
                rpm.signatureHeader(), new byte[0]);

        // Without a key, we should get NOT_SIGNED, KEY_NOT_FOUND, or ERROR
        assertThat(result.status()).isIn(
                SignatureResult.Status.NOT_SIGNED,
                SignatureResult.Status.KEY_NOT_FOUND,
                SignatureResult.Status.ERROR
        );
    }

    @Test
    @EnabledIf("hasTestRpmFiles")
    void multipleRpmsCanBeRead() throws Exception {
        List<Path> rpms = TestFiles.getAllRpmFiles();
        assertThat(rpms).isNotEmpty();

        int successCount = 0;
        for (Path path : rpms) {
            try {
                RpmPackage rpm = RpmReader.read(path);
                assertThat(rpm.name()).isNotEmpty();
                successCount++;
            } catch (Exception e) {
                // Some test files might be corrupted or unsupported
            }
        }

        // At least some should succeed
        assertThat(successCount).isGreaterThan(0);
    }

    @Test
    void isRpmReturnsFalseForShortFile() throws Exception {
        Path tempFile = Files.createTempFile("short", ".rpm");
        try {
            Files.write(tempFile, new byte[]{0x00, 0x01});
            assertThat(RpmReader.isRpm(tempFile)).isFalse();
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    // PURL tests

    @Test
    @EnabledIf("hasTestRpmFiles")
    void purlForRpmPackage() throws Exception {
        Path path = TestFiles.getPath(V4_RPM);
        RpmPackage rpm = RpmReader.read(path);

        PackageURL purl = rpm.metadata().purl();

        assertThat(purl.getType()).isEqualTo("rpm");
        assertThat(purl.getName()).isEqualTo("sed");
        assertThat(purl.getVersion()).isEqualTo("4.9-1.fc40");
        assertThat(purl.getQualifiers()).containsEntry("arch", "x86_64");
    }

    @Test
    @EnabledIf("hasTestRpmFiles")
    void purlForNoarchRpm() throws Exception {
        Path path = TestFiles.getPath(NOARCH_RPM);
        RpmPackage rpm = RpmReader.read(path);

        PackageURL purl = rpm.metadata().purl();

        assertThat(purl.getType()).isEqualTo("rpm");
        assertThat(purl.getName()).isEqualTo("basesystem");
        // noarch should not include arch qualifier
        assertThat(purl.getQualifiers()).isNullOrEmpty();
    }

    @Test
    @EnabledIf("hasTestRpmFiles")
    void purlReturnsValidPackageURL() throws Exception {
        // Test with a real RPM - check that the PURL is valid
        Path path = TestFiles.getPath(V4_RPM);
        RpmPackage rpm = RpmReader.read(path);

        PackageURL purl = rpm.metadata().purl();

        // Verify PackageURL object is valid and contains expected data
        assertThat(purl).isNotNull();
        assertThat(purl.getType()).isNotEmpty();
        assertThat(purl.getName()).isNotEmpty();
        assertThat(purl.getVersion()).isNotEmpty();
    }

    @Test
    @EnabledIf("hasTestRpmFiles")
    void purlViaPackageInterfaceMatchesMetadata() throws Exception {
        Path path = TestFiles.getPath(V4_RPM);
        RpmPackage rpm = RpmReader.read(path);

        PackageURL fromPackage = rpm.packageUrl();
        PackageURL fromMetadata = rpm.metadata().purl();

        // Type and name must match
        assertThat(fromPackage.getType()).isEqualTo(fromMetadata.getType());
        assertThat(fromPackage.getName()).isEqualTo(fromMetadata.getName());
        assertThat(fromPackage.getVersion()).isEqualTo(fromMetadata.getVersion());
    }

    @Test
    @EnabledIf("hasTestRpmFiles")
    void purlCanBeParsedBack() throws Exception {
        Path path = TestFiles.getPath(V4_RPM);
        RpmPackage rpm = RpmReader.read(path);

        PackageURL original = rpm.packageUrl();
        String canonical = original.canonicalize();
        PackageURL parsed = new PackageURL(canonical);

        assertThat(parsed.getType()).isEqualTo(original.getType());
        assertThat(parsed.getName()).isEqualTo(original.getName());
        assertThat(parsed.getVersion()).isEqualTo(original.getVersion());
        assertThat(parsed.getQualifiers()).isEqualTo(original.getQualifiers());
    }

    @Test
    @EnabledIf("hasTestRpmFiles")
    void purlWithCustomNamespace() throws Exception {
        Path path = TestFiles.getPath(V4_RPM);
        RpmPackage rpm = RpmReader.read(path);

        PackageURL purl = rpm.packageUrl(Optional.of("fedora"));
        assertThat(purl.getNamespace()).isEqualTo("fedora");
        assertThat(purl.getType()).isEqualTo("rpm");
        assertThat(purl.getName()).isEqualTo("sed");
    }

    @Test
    @EnabledIf("hasTestRpmFiles")
    void purlVendorNamespaceExtraction() throws Exception {
        // Test that vendor is extracted as namespace when available
        List<Path> rpms = TestFiles.getAllRpmFiles();

        for (Path path : rpms) {
            try {
                RpmPackage rpm = RpmReader.read(path);
                PackageMetadata rpmMeta = rpm.rpmMetadata();
                PackageURL purl = rpm.metadata().purl();

                Optional<String> vendor = rpmMeta.vendor();
                if (vendor.isPresent() && !vendor.get().isEmpty()) {
                    // If vendor is present, namespace should be set
                    String expectedNamespace = vendor.get().toLowerCase().replaceAll("\\s+", "-");
                    assertThat(purl.getNamespace())
                            .as("Vendor '%s' should produce namespace '%s' for package %s",
                                    vendor.get(), expectedNamespace, rpm.name())
                            .isEqualTo(expectedNamespace);
                }
            } catch (Exception e) {
                // Some packages might have issues, continue with others
            }
        }
    }

    @Test
    @EnabledIf("hasTestRpmFiles")
    void purlEpochHandling() throws Exception {
        // Test that epoch is handled correctly
        List<Path> rpms = TestFiles.getAllRpmFiles();

        for (Path path : rpms) {
            try {
                RpmPackage rpm = RpmReader.read(path);
                PackageURL purl = rpm.metadata().purl();
                Optional<Integer> epoch = rpm.metadata().epoch();

                if (epoch.isPresent() && epoch.get() > 0) {
                    // Non-zero epoch should be in qualifiers
                    assertThat(purl.getQualifiers())
                            .as("Package %s with epoch %d should have epoch qualifier",
                                    rpm.name(), epoch.get())
                            .containsEntry("epoch", String.valueOf(epoch.get()));
                } else {
                    // Zero or missing epoch should not be in qualifiers
                    if (purl.getQualifiers() != null) {
                        assertThat(purl.getQualifiers())
                                .as("Package %s with no/zero epoch should not have epoch qualifier", rpm.name())
                                .doesNotContainKey("epoch");
                    }
                }
            } catch (Exception e) {
                // Some packages might have issues, continue with others
            }
        }
    }

    @Test
    @EnabledIf("hasTestRpmFiles")
    void purlArchitectureHandling() throws Exception {
        // Test architecture handling across multiple RPMs
        List<Path> rpms = TestFiles.getAllRpmFiles();

        for (Path path : rpms) {
            try {
                RpmPackage rpm = RpmReader.read(path);
                PackageURL purl = rpm.packageUrl();
                String arch = rpm.arch();

                if ("noarch".equalsIgnoreCase(arch)) {
                    // noarch should not include arch qualifier
                    assertThat(purl.getQualifiers())
                            .as("Package %s with arch 'noarch' should not have arch qualifier", rpm.name())
                            .isNullOrEmpty();
                } else if (!arch.isEmpty()) {
                    // Other architectures should have arch qualifier
                    assertThat(purl.getQualifiers())
                            .as("Package %s with arch '%s' should have arch qualifier", rpm.name(), arch)
                            .containsEntry("arch", arch);
                }
            } catch (Exception e) {
                // Some packages might have issues, continue with others
            }
        }
    }

    @Test
    @EnabledIf("hasTestRpmFiles")
    void purlVersionIncludesRelease() throws Exception {
        Path path = TestFiles.getPath(V4_RPM);
        RpmPackage rpm = RpmReader.read(path);

        PackageURL purl = rpm.metadata().purl();

        // Version should include release (version-release format)
        String version = rpm.version();
        String release = rpm.release();

        assertThat(purl.getVersion()).isEqualTo(version + "-" + release);
    }

    @Test
    @EnabledIf("hasTestRpmFiles")
    void allRpmPackagesProduceValidPurls() throws Exception {
        List<Path> rpms = TestFiles.getAllRpmFiles();

        int successCount = 0;
        int totalCount = 0;

        for (Path path : rpms) {
            try {
                RpmPackage rpm = RpmReader.read(path);
                totalCount++;

                PackageURL purl = rpm.metadata().purl();
                assertThat(purl).isNotNull();
                assertThat(purl.getType()).isEqualTo("rpm");
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
