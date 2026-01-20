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

import com.github.packageurl.MalformedPackageURLException;
import com.github.packageurl.PackageURL;
import com.github.packageurl.PackageURLBuilder;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link Package#packageUrl()} method.
 */
class PackageUrlTest {

    // RPM format tests

    @Test
    void rpmPackageUrlBasic() {
        Package pkg = createTestPackage(PackageFormat.RPM, "curl", "7.50.3", "x86_64");
        PackageURL purl = pkg.packageUrl();

        assertThat(purl.getType()).isEqualTo("rpm");
        assertThat(purl.getName()).isEqualTo("curl");
        assertThat(purl.getVersion()).isEqualTo("7.50.3");
        assertThat(purl.getQualifiers()).containsEntry("arch", "x86_64");
    }

    @Test
    void rpmPackageUrlWithRelease() {
        Package pkg = createTestPackage(PackageFormat.RPM, "curl", "7.50.3", "1.fc25", "x86_64");
        PackageURL purl = pkg.packageUrl();

        assertThat(purl.getType()).isEqualTo("rpm");
        assertThat(purl.getName()).isEqualTo("curl");
        assertThat(purl.getVersion()).isEqualTo("7.50.3-1.fc25");
        assertThat(purl.getQualifiers()).containsEntry("arch", "x86_64");
    }

    @Test
    void rpmPackageUrlWithEpoch() {
        Package pkg = createTestPackage(PackageFormat.RPM, "curl", "7.50.3", "1.fc25", 1, "x86_64");
        PackageURL purl = pkg.packageUrl();

        assertThat(purl.getType()).isEqualTo("rpm");
        assertThat(purl.getVersion()).isEqualTo("7.50.3-1.fc25");
        assertThat(purl.getQualifiers()).containsEntry("arch", "x86_64");
        assertThat(purl.getQualifiers()).containsEntry("epoch", "1");
    }

    @Test
    void rpmPackageUrlWithNamespace() {
        Package pkg = createTestPackage(PackageFormat.RPM, "curl", "7.50.3", "1.fc25", "x86_64");
        PackageURL purl = pkg.packageUrl(Optional.of("fedora"));

        assertThat(purl.getType()).isEqualTo("rpm");
        assertThat(purl.getNamespace()).isEqualTo("fedora");
        assertThat(purl.getName()).isEqualTo("curl");
    }

    @Test
    void rpmPackageUrlNoarch() {
        Package pkg = createTestPackage(PackageFormat.RPM, "curl-doc", "7.50.3", "noarch");
        PackageURL purl = pkg.packageUrl();

        // noarch should not include arch qualifier
        assertThat(purl.getType()).isEqualTo("rpm");
        assertThat(purl.getName()).isEqualTo("curl-doc");
        assertThat(purl.getQualifiers()).isNullOrEmpty();
    }

    // DEB format tests

    @Test
    void debPackageUrlBasic() {
        Package pkg = createTestPackage(PackageFormat.DEB, "curl", "7.50.3-1", "amd64");
        PackageURL purl = pkg.packageUrl();

        assertThat(purl.getType()).isEqualTo("deb");
        assertThat(purl.getName()).isEqualTo("curl");
        assertThat(purl.getVersion()).isEqualTo("7.50.3-1");
        assertThat(purl.getQualifiers()).containsEntry("arch", "amd64");
    }

    @Test
    void debPackageUrlWithNamespace() {
        Package pkg = createTestPackage(PackageFormat.DEB, "curl", "7.50.3-1", "amd64");
        PackageURL purl = pkg.packageUrl(Optional.of("debian"));

        assertThat(purl.getType()).isEqualTo("deb");
        assertThat(purl.getNamespace()).isEqualTo("debian");
        assertThat(purl.getName()).isEqualTo("curl");
    }

    @Test
    void debPackageUrlAll() {
        Package pkg = createTestPackage(PackageFormat.DEB, "curl-doc", "7.50.3-1", "all");
        PackageURL purl = pkg.packageUrl();

        // "all" architecture should not include arch qualifier
        assertThat(purl.getType()).isEqualTo("deb");
        assertThat(purl.getName()).isEqualTo("curl-doc");
        assertThat(purl.getQualifiers()).isNullOrEmpty();
    }

    // Pacman/ALPM format tests

    @Test
    void pacmanPackageUrlBasic() {
        Package pkg = createTestPackage(PackageFormat.PACMAN, "curl", "7.50.3-1", "x86_64");
        PackageURL purl = pkg.packageUrl();

        assertThat(purl.getType()).isEqualTo("alpm");
        assertThat(purl.getName()).isEqualTo("curl");
        assertThat(purl.getVersion()).isEqualTo("7.50.3-1");
        assertThat(purl.getQualifiers()).containsEntry("arch", "x86_64");
    }

    @Test
    void pacmanPackageUrlWithNamespace() {
        Package pkg = createTestPackage(PackageFormat.PACMAN, "curl", "7.50.3-1", "x86_64");
        PackageURL purl = pkg.packageUrl(Optional.of("arch"));

        assertThat(purl.getType()).isEqualTo("alpm");
        assertThat(purl.getNamespace()).isEqualTo("arch");
        assertThat(purl.getName()).isEqualTo("curl");
    }

    @Test
    void pacmanPackageUrlAny() {
        Package pkg = createTestPackage(PackageFormat.PACMAN, "bash-completion", "2.11-1", "any");
        PackageURL purl = pkg.packageUrl();

        // "any" architecture should not include arch qualifier
        assertThat(purl.getType()).isEqualTo("alpm");
        assertThat(purl.getName()).isEqualTo("bash-completion");
        assertThat(purl.getQualifiers()).isNullOrEmpty();
    }

    // APK format tests

    @Test
    void apkPackageUrlBasic() {
        Package pkg = createTestPackage(PackageFormat.APK, "curl", "7.50.3-r0", "x86_64");
        PackageURL purl = pkg.packageUrl();

        assertThat(purl.getType()).isEqualTo("apk");
        assertThat(purl.getName()).isEqualTo("curl");
        assertThat(purl.getVersion()).isEqualTo("7.50.3-r0");
        assertThat(purl.getQualifiers()).containsEntry("arch", "x86_64");
    }

    @Test
    void apkPackageUrlWithNamespace() {
        Package pkg = createTestPackage(PackageFormat.APK, "curl", "7.50.3-r0", "x86_64");
        PackageURL purl = pkg.packageUrl(Optional.of("alpine"));

        assertThat(purl.getType()).isEqualTo("apk");
        assertThat(purl.getNamespace()).isEqualTo("alpine");
        assertThat(purl.getName()).isEqualTo("curl");
    }

    // FreeBSD format tests

    @Test
    void freebsdPackageUrlBasic() {
        Package pkg = createTestPackage(PackageFormat.FREEBSD_PKG, "curl", "7.50.3", "amd64");
        PackageURL purl = pkg.packageUrl();

        assertThat(purl.getType()).isEqualTo("freebsd");
        assertThat(purl.getName()).isEqualTo("curl");
        assertThat(purl.getVersion()).isEqualTo("7.50.3");
        assertThat(purl.getQualifiers()).containsEntry("arch", "amd64");
    }

    @Test
    void freebsdPackageUrlWithNamespace() {
        Package pkg = createTestPackage(PackageFormat.FREEBSD_PKG, "curl", "7.50.3", "amd64");
        PackageURL purl = pkg.packageUrl(Optional.of("freebsd"));

        assertThat(purl.getType()).isEqualTo("freebsd");
        assertThat(purl.getNamespace()).isEqualTo("freebsd");
        assertThat(purl.getName()).isEqualTo("curl");
    }

    // OpenBSD format tests

    @Test
    void openbsdPackageUrlBasic() {
        Package pkg = createTestPackage(PackageFormat.OPENBSD_PKG, "curl", "7.50.3", "amd64");
        PackageURL purl = pkg.packageUrl();

        assertThat(purl.getType()).isEqualTo("openbsd");
        assertThat(purl.getName()).isEqualTo("curl");
        assertThat(purl.getVersion()).isEqualTo("7.50.3");
        assertThat(purl.getQualifiers()).containsEntry("arch", "amd64");
    }

    @Test
    void openbsdPackageUrlWithNamespace() {
        Package pkg = createTestPackage(PackageFormat.OPENBSD_PKG, "curl", "7.50.3", "amd64");
        PackageURL purl = pkg.packageUrl(Optional.of("openbsd"));

        assertThat(purl.getType()).isEqualTo("openbsd");
        assertThat(purl.getNamespace()).isEqualTo("openbsd");
        assertThat(purl.getName()).isEqualTo("curl");
    }

    // Special character handling tests

    @Test
    void packageUrlHandlesSpecialCharacters() {
        Package pkg = createTestPackage(PackageFormat.RPM, "pkg+name", "1.0", "x86_64");
        PackageURL purl = pkg.packageUrl();

        // PackageURL stores raw values and encodes on canonicalization
        assertThat(purl.getName()).isEqualTo("pkg+name");
        assertThat(purl.getVersion()).isEqualTo("1.0");
    }

    @Test
    void packageUrlPreservesSafeCharacters() {
        Package pkg = createTestPackage(PackageFormat.RPM, "pkg-name_test.lib", "1.0-beta~1", "x86_64");
        PackageURL purl = pkg.packageUrl();

        // Hyphens, underscores, dots, and tildes are preserved
        assertThat(purl.getName()).isEqualTo("pkg-name_test.lib");
        assertThat(purl.getVersion()).isEqualTo("1.0-beta~1");
    }

    @Test
    void packageUrlHandlesSpaces() {
        Package pkg = createTestPackage(PackageFormat.DEB, "pkg name", "1.0", "amd64");
        PackageURL purl = pkg.packageUrl();

        assertThat(purl.getName()).isEqualTo("pkg name");
        assertThat(purl.getVersion()).isEqualTo("1.0");
    }

    @Test
    void packageUrlHandlesAtSign() {
        Package pkg = createTestPackage(PackageFormat.RPM, "pkg@2", "1.0", "x86_64");
        PackageURL purl = pkg.packageUrl();

        assertThat(purl.getName()).isEqualTo("pkg@2");
        assertThat(purl.getVersion()).isEqualTo("1.0");
    }

    // Edge cases

    @Test
    void packageUrlWithEmptyVersion() {
        Package pkg = createTestPackage(PackageFormat.RPM, "curl", "", "x86_64");
        PackageURL purl = pkg.packageUrl();

        assertThat(purl.getType()).isEqualTo("rpm");
        assertThat(purl.getName()).isEqualTo("curl");
        assertThat(purl.getVersion()).isNull();
        assertThat(purl.getQualifiers()).containsEntry("arch", "x86_64");
    }

    @Test
    void packageUrlWithEmptyArch() {
        Package pkg = createTestPackage(PackageFormat.RPM, "curl", "7.50.3", "");
        PackageURL purl = pkg.packageUrl();

        assertThat(purl.getType()).isEqualTo("rpm");
        assertThat(purl.getName()).isEqualTo("curl");
        assertThat(purl.getVersion()).isEqualTo("7.50.3");
        assertThat(purl.getQualifiers()).isNullOrEmpty();
    }

    @Test
    void packageUrlWithEmptyNamespace() {
        Package pkg = createTestPackage(PackageFormat.RPM, "curl", "7.50.3", "x86_64");
        PackageURL purl = pkg.packageUrl(Optional.empty());

        assertThat(purl.getType()).isEqualTo("rpm");
        assertThat(purl.getNamespace()).isNull();
        assertThat(purl.getName()).isEqualTo("curl");
    }

    @Test
    void packageUrlZeroEpochNotIncluded() {
        Package pkg = createTestPackage(PackageFormat.RPM, "curl", "7.50.3", "1.fc25", 0, "x86_64");
        PackageURL purl = pkg.packageUrl();

        // Zero epoch should not be included
        assertThat(purl.getType()).isEqualTo("rpm");
        assertThat(purl.getVersion()).isEqualTo("7.50.3-1.fc25");
        assertThat(purl.getQualifiers()).doesNotContainKey("epoch");
    }

    // Helper methods to create test packages

    private Package createTestPackage(PackageFormat format, String name, String version, String arch) {
        return createTestPackage(format, name, version, null, null, arch);
    }

    private Package createTestPackage(PackageFormat format, String name, String version,
                                       String release, String arch) {
        return createTestPackage(format, name, version, release, null, arch);
    }

    private Package createTestPackage(PackageFormat format, String name, String version,
                                       String release, Integer epoch, String arch) {
        PackageMetadata metadata = new TestMetadata(name, version, release, epoch, arch);
        return new TestPackage(format, metadata);
    }

    /**
     * Test implementation of PackageMetadata.
     */
    private static class TestMetadata implements PackageMetadata {
        private final String name;
        private final String version;
        private final String release;
        private final Integer epoch;
        private final String arch;

        TestMetadata(String name, String version, String release, Integer epoch, String arch) {
            this.name = name;
            this.version = version;
            this.release = release;
            this.epoch = epoch;
            this.arch = arch;
        }

        @Override
        public @NotNull String name() {
            return name != null ? name : "";
        }

        @Override
        public @NotNull String version() {
            return version != null ? version : "";
        }

        @Override
        public @NotNull Optional<String> release() {
            return Optional.ofNullable(release).filter(r -> !r.isEmpty());
        }

        @Override
        public @NotNull Optional<Integer> epoch() {
            return Optional.ofNullable(epoch);
        }

        @Override
        public @NotNull String arch() {
            return arch != null ? arch : "";
        }

        @Override
        public long installedSize() {
            return 0;
        }

        @Override
        public @NotNull PackageURL purl() {
            // Test implementation - not used in these tests
            try {
                PackageURLBuilder builder = PackageURLBuilder.aPackageURL()
                        .withType("test")
                        .withName(name());
                if (!version().isEmpty()) {
                    builder.withVersion(version());
                }
                return builder.build();
            } catch (MalformedPackageURLException e) {
                throw new IllegalStateException("Failed to build test PURL", e);
            }
        }
    }

    /**
     * Test implementation of Package.
     */
    private record TestPackage(
            PackageFormat format,
            PackageMetadata metadata
    ) implements Package {

        @Override
        public @NotNull PackageFormat format() {
            return format;
        }

        @Override
        public @NotNull PackageMetadata metadata() {
            return metadata;
        }

        @Override
        public @NotNull Stream<PackageEntry> payload() throws IOException, PackageException {
            return Stream.empty();
        }
    }
}
