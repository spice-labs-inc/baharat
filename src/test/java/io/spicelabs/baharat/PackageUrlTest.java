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

import io.spicelabs.baharat.common.PurlHelper;
import io.spicelabs.coordinates.Purl;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link Package#purl()} method.
 */
class PackageUrlTest {

    @Test
    void rpmPurlBasic() {
        Package pkg = createTestPackage(PackageFormat.RPM, "curl", "7.50.3", "x86_64");
        Purl purl = pkg.purl();

        assertThat(purl.type).isEqualTo("rpm");
        assertThat(purl.namespace).isEqualTo("unknown");
        assertThat(purl.name).isEqualTo("curl");
        assertThat(purl.version).isEqualTo("7.50.3");
        assertThat(purl.qualifiers).containsEntry("arch", "x86_64");
    }

    @Test
    void rpmPurlWithRelease() {
        Package pkg = createTestPackage(PackageFormat.RPM, "curl", "7.50.3", "1.fc25", "x86_64");
        Purl purl = pkg.purl();

        assertThat(purl.type).isEqualTo("rpm");
        assertThat(purl.version).isEqualTo("7.50.3-1.fc25");
        assertThat(purl.qualifiers).containsEntry("arch", "x86_64");
    }

    @Test
    void rpmPurlWithEpoch() {
        Package pkg = createTestPackage(PackageFormat.RPM, "curl", "7.50.3", "1.fc25", 1, "x86_64");
        Purl purl = pkg.purl();

        assertThat(purl.type).isEqualTo("rpm");
        assertThat(purl.qualifiers).containsEntry("arch", "x86_64");
        assertThat(purl.qualifiers).containsEntry("epoch", "1");
    }

    @Test
    void rpmPurlNoarch() {
        Package pkg = createTestPackage(PackageFormat.RPM, "curl-doc", "7.50.3", "noarch");
        Purl purl = pkg.purl();

        assertThat(purl.type).isEqualTo("rpm");
        assertThat(purl.qualifiers).doesNotContainKey("arch");
    }

    @Test
    void debPurlBasic() {
        Package pkg = createTestPackage(PackageFormat.DEB, "curl", "7.50.3-1", "amd64");
        Purl purl = pkg.purl();

        assertThat(purl.type).isEqualTo("deb");
        assertThat(purl.namespace).isEqualTo("debian");
        assertThat(purl.name).isEqualTo("curl");
        assertThat(purl.version).isEqualTo("7.50.3-1");
        assertThat(purl.qualifiers).containsEntry("arch", "amd64");
    }

    @Test
    void debPurlAll() {
        Package pkg = createTestPackage(PackageFormat.DEB, "curl-doc", "7.50.3-1", "all");
        Purl purl = pkg.purl();

        assertThat(purl.type).isEqualTo("deb");
        assertThat(purl.qualifiers).doesNotContainKey("arch");
    }

    @Test
    void pacmanPurlBasic() {
        Package pkg = createTestPackage(PackageFormat.PACMAN, "curl", "7.50.3-1", "x86_64");
        Purl purl = pkg.purl();

        assertThat(purl.type).isEqualTo("alpm");
        assertThat(purl.namespace).isEqualTo("arch");
        assertThat(purl.name).isEqualTo("curl");
        assertThat(purl.version).isEqualTo("7.50.3-1");
        assertThat(purl.qualifiers).containsEntry("arch", "x86_64");
    }

    @Test
    void pacmanPurlAny() {
        Package pkg = createTestPackage(PackageFormat.PACMAN, "bash-completion", "2.11-1", "any");
        Purl purl = pkg.purl();

        assertThat(purl.type).isEqualTo("alpm");
        assertThat(purl.qualifiers).doesNotContainKey("arch");
    }

    @Test
    void apkPurlBasic() {
        Package pkg = createTestPackage(PackageFormat.APK, "curl", "7.50.3-r0", "x86_64");
        Purl purl = pkg.purl();

        assertThat(purl.type).isEqualTo("apk");
        assertThat(purl.namespace).isEqualTo("alpine");
        assertThat(purl.name).isEqualTo("curl");
        assertThat(purl.version).isEqualTo("7.50.3-r0");
        assertThat(purl.qualifiers).containsEntry("arch", "x86_64");
    }

    @Test
    void freebsdPurlBasic() {
        Package pkg = createTestPackage(PackageFormat.FREEBSD_PKG, "curl", "7.50.3", "amd64");
        Purl purl = pkg.purl();

        assertThat(purl.type).isEqualTo("freebsd");
        assertThat(purl.namespace).isNull();
        assertThat(purl.name).isEqualTo("curl");
        assertThat(purl.version).isEqualTo("7.50.3");
        assertThat(purl.qualifiers).containsEntry("arch", "amd64");
    }

    @Test
    void openbsdPurlBasic() {
        Package pkg = createTestPackage(PackageFormat.OPENBSD_PKG, "curl", "7.50.3", "amd64");
        Purl purl = pkg.purl();

        assertThat(purl.type).isEqualTo("openbsd");
        assertThat(purl.namespace).isNull();
        assertThat(purl.name).isEqualTo("curl");
        assertThat(purl.version).isEqualTo("7.50.3");
        assertThat(purl.qualifiers).containsEntry("arch", "amd64");
    }

    @Test
    void purlHandlesSpecialCharacters() {
        Package pkg = createTestPackage(PackageFormat.RPM, "pkg+name", "1.0", "x86_64");
        Purl purl = pkg.purl();

        assertThat(purl.name).isEqualTo("pkg+name");
        assertThat(purl.version).isEqualTo("1.0");
    }

    @Test
    void purlPreservesSafeCharacters() {
        Package pkg = createTestPackage(PackageFormat.RPM, "pkg-name_test.lib", "1.0-beta~1", "x86_64");
        Purl purl = pkg.purl();

        assertThat(purl.name).isEqualTo("pkg-name_test.lib");
        assertThat(purl.version).isEqualTo("1.0-beta~1");
    }

    @Test
    void purlHandlesSpaces() {
        Package pkg = createTestPackage(PackageFormat.DEB, "pkg name", "1.0", "amd64");
        Purl purl = pkg.purl();

        assertThat(purl.name).isEqualTo("pkg name");
        assertThat(purl.version).isEqualTo("1.0");
    }

    @Test
    void purlHandlesAtSign() {
        Package pkg = createTestPackage(PackageFormat.RPM, "pkg@2", "1.0", "x86_64");
        Purl purl = pkg.purl();

        assertThat(purl.name).isEqualTo("pkg@2");
        assertThat(purl.version).isEqualTo("1.0");
    }

    @Test
    void purlWithEmptyVersion() {
        Package pkg = createTestPackage(PackageFormat.RPM, "curl", "", "x86_64");
        Purl purl = pkg.purl();

        assertThat(purl.type).isEqualTo("rpm");
        assertThat(purl.name).isEqualTo("curl");
        assertThat(purl.version).isNull();
        assertThat(purl.qualifiers).containsEntry("arch", "x86_64");
    }

    @Test
    void purlWithEmptyArch() {
        Package pkg = createTestPackage(PackageFormat.RPM, "curl", "7.50.3", "");
        Purl purl = pkg.purl();

        assertThat(purl.type).isEqualTo("rpm");
        assertThat(purl.name).isEqualTo("curl");
        assertThat(purl.version).isEqualTo("7.50.3");
        assertThat(purl.qualifiers).isEmpty();
    }

    @Test
    void purlZeroEpochNotIncluded() {
        Package pkg = createTestPackage(PackageFormat.RPM, "curl", "7.50.3", "1.fc25", 0, "x86_64");
        Purl purl = pkg.purl();

        assertThat(purl.qualifiers).doesNotContainKey("epoch");
    }

    @Test
    void purlRoundtripsThroughCoordinates() {
        Package pkg = createTestPackage(PackageFormat.DEB, "curl", "7.50.3-1", "amd64");
        Purl original = pkg.purl();
        String canonical = original.toCanonical();
        Purl parsed = Purl.parse(canonical);

        assertThat(parsed.type).isEqualTo(original.type);
        assertThat(parsed.namespace).isEqualTo(original.namespace);
        assertThat(parsed.name).isEqualTo(original.name);
        assertThat(parsed.version).isEqualTo(original.version);
        assertThat(parsed.qualifiers).isEqualTo(original.qualifiers);
    }

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
        public @NotNull Purl purl() {
            return PurlHelper.build("test", null, name(), version().isEmpty() ? null : version(), PurlHelper.newQualifiers());
        }
    }

    private record TestPackage(PackageFormat format, PackageMetadata metadata) implements Package {

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

        @Override
        public @NotNull Purl purl() {
            String type = switch (format) {
                case RPM -> "rpm";
                case DEB -> "deb";
                case PACMAN -> "alpm";
                case APK -> "apk";
                case FREEBSD_PKG -> "freebsd";
                case OPENBSD_PKG -> "openbsd";
            };
            String namespace = switch (format) {
                case RPM -> "unknown";
                case DEB -> "debian";
                case PACMAN -> "arch";
                case APK -> "alpine";
                default -> null;
            };
            String version = version();
            Optional<String> rel = metadata().release();
            if (format == PackageFormat.RPM && rel.isPresent() && !rel.get().isEmpty()) {
                version = version + "-" + rel.get();
            }

            Map<String, String> qualifiers = new LinkedHashMap<>();
            if (!PurlHelper.isArchitectureIndependent(arch())) {
                qualifiers.put("arch", arch());
            }
            if (format == PackageFormat.RPM) {
                metadata().epoch().filter(e -> e != 0).ifPresent(e -> qualifiers.put("epoch", String.valueOf(e)));
            }

            return PurlHelper.build(type, namespace, name(), version.isEmpty() ? null : version, qualifiers);
        }
    }
}
