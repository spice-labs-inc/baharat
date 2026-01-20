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
package io.spicelabs.baharat.rpm.metadata;

import io.spicelabs.baharat.rpm.header.Header;
import io.spicelabs.baharat.rpm.header.HeaderTag;
import io.spicelabs.baharat.rpm.header.IndexEntry;
import io.spicelabs.baharat.rpm.header.TagType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetadataTest {

    // Dependency tests

    @Test
    void dependencyCreation() {
        Dependency dep = new Dependency(DependencyType.REQUIRES, "glibc", Optional.of("2.17"),
                Dependency.RPMSENSE_GREATER | Dependency.RPMSENSE_EQUAL);

        assertThat(dep.type()).isEqualTo(DependencyType.REQUIRES);
        assertThat(dep.name()).isEqualTo("glibc");
        assertThat(dep.version()).hasValue("2.17");
        assertThat(dep.flags()).isEqualTo(Dependency.RPMSENSE_GREATER | Dependency.RPMSENSE_EQUAL);
    }

    @Test
    void dependencyComparison() {
        Dependency dep = new Dependency(DependencyType.REQUIRES, "lib", Optional.of("1.0"),
                Dependency.RPMSENSE_GREATER);

        assertThat(dep.isGreaterThan()).isTrue();
        assertThat(dep.isEqual()).isFalse();
        assertThat(dep.isLessThan()).isFalse();
    }

    @Test
    void dependencyComparisonGreaterEqual() {
        Dependency dep = new Dependency(DependencyType.PROVIDES, "feature", Optional.of("2.0"),
                Dependency.RPMSENSE_GREATER | Dependency.RPMSENSE_EQUAL);

        assertThat(dep.isGreaterThan()).isTrue();
        assertThat(dep.isEqual()).isTrue();
        assertThat(dep.isLessThan()).isFalse();
        assertThat(dep.operator()).isEqualTo(">=");
    }

    @Test
    void dependencyComparisonLessEqual() {
        Dependency dep = new Dependency(DependencyType.CONFLICTS, "old-pkg", Optional.of("1.0"),
                Dependency.RPMSENSE_LESS | Dependency.RPMSENSE_EQUAL);

        assertThat(dep.isLessThan()).isTrue();
        assertThat(dep.isEqual()).isTrue();
        assertThat(dep.operator()).isEqualTo("<=");
    }

    @Test
    void dependencyComparisonEqual() {
        Dependency dep = new Dependency(DependencyType.OBSOLETES, "legacy", Optional.of("1.0"),
                Dependency.RPMSENSE_EQUAL);

        assertThat(dep.operator()).isEqualTo("=");
    }

    @Test
    void dependencyWithNoVersion() {
        Dependency dep = new Dependency(DependencyType.REQUIRES, "feature", Optional.empty(), 0);

        assertThat(dep.version()).isEmpty();
        assertThat(dep.operator()).isEmpty();
    }

    @Test
    void dependencyFlags() {
        Dependency preDep = new Dependency(DependencyType.REQUIRES, "prereq", Optional.empty(),
                Dependency.RPMSENSE_PREREQ);
        Dependency scriptDep = new Dependency(DependencyType.REQUIRES, "script", Optional.empty(),
                Dependency.RPMSENSE_SCRIPT_PRE);
        Dependency rpmDep = new Dependency(DependencyType.REQUIRES, "rpmlib", Optional.empty(),
                Dependency.RPMSENSE_RPMLIB);

        assertThat(preDep.isPrereq()).isTrue();
        assertThat((scriptDep.flags() & Dependency.RPMSENSE_SCRIPT_PRE) != 0).isTrue();
        assertThat(rpmDep.isRpmlib()).isTrue();
    }

    @Test
    void dependencyToVersionedString() {
        Dependency dep = new Dependency(DependencyType.REQUIRES, "pkg", Optional.of("1.0"),
                Dependency.RPMSENSE_GREATER | Dependency.RPMSENSE_EQUAL);

        assertThat(dep.toVersionedString()).contains("pkg");
        assertThat(dep.toVersionedString()).contains(">=");
        assertThat(dep.toVersionedString()).contains("1.0");
    }

    @Test
    void dependencyToVersionedStringNoVersion() {
        Dependency dep = new Dependency(DependencyType.PROVIDES, "capability", Optional.empty(), 0);

        assertThat(dep.toVersionedString()).isEqualTo("capability");
    }

    // DependencyType tests

    @Test
    void dependencyTypeValues() {
        assertThat(DependencyType.REQUIRES.name()).isEqualTo("REQUIRES");
        assertThat(DependencyType.PROVIDES.name()).isEqualTo("PROVIDES");
        assertThat(DependencyType.CONFLICTS.name()).isEqualTo("CONFLICTS");
        assertThat(DependencyType.OBSOLETES.name()).isEqualTo("OBSOLETES");
        assertThat(DependencyType.RECOMMENDS.name()).isEqualTo("RECOMMENDS");
        assertThat(DependencyType.SUGGESTS.name()).isEqualTo("SUGGESTS");
        assertThat(DependencyType.SUPPLEMENTS.name()).isEqualTo("SUPPLEMENTS");
        assertThat(DependencyType.ENHANCES.name()).isEqualTo("ENHANCES");
    }

    // Changelog tests

    @Test
    void changelogCreation() {
        Instant time = Instant.ofEpochSecond(1700000000);
        Changelog entry = new Changelog(time, "John Doe <john@example.com>", "- Fixed a bug");

        assertThat(entry.time()).isEqualTo(time);
        assertThat(entry.author()).isEqualTo("John Doe <john@example.com>");
        assertThat(entry.text()).isEqualTo("- Fixed a bug");
    }

    @Test
    void changelogFromUnixTime() {
        Changelog entry = Changelog.fromUnixTime(1700000000L, "Author", "Text");

        assertThat(entry.time()).isEqualTo(Instant.ofEpochSecond(1700000000));
        assertThat(entry.author()).isEqualTo("Author");
        assertThat(entry.text()).isEqualTo("Text");
    }

    // FileInfo tests

    @Test
    void fileInfoCreation() {
        Instant mtime = Instant.ofEpochSecond(1600000000);
        FileInfo file = new FileInfo("/usr/bin/app", 12345L, 0100755, mtime, 0,
                "root", "root", Optional.of("abc123"), Optional.empty());

        assertThat(file.path()).isEqualTo("/usr/bin/app");
        assertThat(file.size()).isEqualTo(12345L);
        assertThat(file.mode()).isEqualTo(0100755);
        assertThat(file.mtime()).isEqualTo(mtime);
        assertThat(file.flags()).isEqualTo(0);
        assertThat(file.userName()).isEqualTo("root");
        assertThat(file.groupName()).isEqualTo("root");
        assertThat(file.digest()).hasValue("abc123");
        assertThat(file.linkTo()).isEmpty();
    }

    @Test
    void fileInfoIsFile() {
        FileInfo file = new FileInfo("/file", 100, 0100644, Instant.EPOCH, 0,
                "user", "group", Optional.empty(), Optional.empty());

        assertThat(file.isRegularFile()).isTrue();
        assertThat(file.isDirectory()).isFalse();
        assertThat(file.isSymbolicLink()).isFalse();
    }

    @Test
    void fileInfoIsDirectory() {
        FileInfo dir = new FileInfo("/dir", 0, 0040755, Instant.EPOCH, 0,
                "user", "group", Optional.empty(), Optional.empty());

        assertThat(dir.isRegularFile()).isFalse();
        assertThat(dir.isDirectory()).isTrue();
        assertThat(dir.isSymbolicLink()).isFalse();
    }

    @Test
    void fileInfoIsSymlink() {
        FileInfo link = new FileInfo("/link", 10, 0120777, Instant.EPOCH, 0,
                "user", "group", Optional.empty(), Optional.of("/target"));

        assertThat(link.isRegularFile()).isFalse();
        assertThat(link.isDirectory()).isFalse();
        assertThat(link.isSymbolicLink()).isTrue();
        assertThat(link.linkTo()).hasValue("/target");
    }

    @Test
    void fileInfoPermissions() {
        FileInfo file = new FileInfo("/file", 0, 0100755, Instant.EPOCH, 0,
                "u", "g", Optional.empty(), Optional.empty());

        assertThat(file.permissions()).isEqualTo(0755);
    }

    @Test
    void fileInfoFlags() {
        FileInfo config = new FileInfo("/etc/app.conf", 100, 0100644, Instant.EPOCH,
                FileInfo.RPMFILE_CONFIG, "root", "root", Optional.empty(), Optional.empty());
        FileInfo doc = new FileInfo("/doc.txt", 50, 0100644, Instant.EPOCH,
                FileInfo.RPMFILE_DOC, "root", "root", Optional.empty(), Optional.empty());
        FileInfo ghost = new FileInfo("/ghost", 0, 0100644, Instant.EPOCH,
                FileInfo.RPMFILE_GHOST, "root", "root", Optional.empty(), Optional.empty());

        assertThat(config.isConfig()).isTrue();
        assertThat(config.isDoc()).isFalse();
        assertThat(doc.isDoc()).isTrue();
        assertThat(ghost.isGhost()).isTrue();
    }

    @Test
    void fileInfoMissingConfig() {
        FileInfo file = new FileInfo("/etc/missing.conf", 100, 0100644, Instant.EPOCH,
                FileInfo.RPMFILE_CONFIG | FileInfo.RPMFILE_MISSINGOK, "root", "root",
                Optional.empty(), Optional.empty());

        assertThat(file.isConfig()).isTrue();
        // MISSINGOK flag is set but no helper method for it
        assertThat(file.flags() & FileInfo.RPMFILE_MISSINGOK).isNotZero();
    }

    @Test
    void fileInfoNoReplace() {
        FileInfo file = new FileInfo("/etc/app.conf", 100, 0100644, Instant.EPOCH,
                FileInfo.RPMFILE_CONFIG | FileInfo.RPMFILE_NOREPLACE, "root", "root",
                Optional.empty(), Optional.empty());

        assertThat(file.isConfig()).isTrue();
        assertThat(file.isNoReplace()).isTrue();
    }

    // PackageMetadata tests

    @Test
    void packageMetadataBasicInfo() throws Exception {
        Header header = createHeaderWithBasicInfo();
        PackageMetadata meta = new PackageMetadata(header);

        assertThat(meta.name()).isEqualTo("test-package");
        assertThat(meta.version()).isEqualTo("1.0.0");
        assertThat(meta.release()).isEqualTo("1.fc39");
        assertThat(meta.header()).isSameAs(header);
    }

    @Test
    void packageMetadataNevra() throws Exception {
        Header header = createHeaderWithBasicInfo();
        PackageMetadata meta = new PackageMetadata(header);

        String nevra = meta.nevra();
        assertThat(nevra).contains("test-package");
        assertThat(nevra).contains("1.0.0");
        assertThat(nevra).contains("1.fc39");
    }

    @Test
    void packageMetadataPayloadInfo() throws Exception {
        Header header = createHeaderWithPayloadInfo();
        PackageMetadata meta = new PackageMetadata(header);

        assertThat(meta.payloadFormat()).isEqualTo("cpio");
        assertThat(meta.payloadCompressor()).isEqualTo("xz");
    }

    @Test
    void packageMetadataEmptyHeader() {
        Header header = new Header(List.of(), new byte[0]);
        PackageMetadata meta = new PackageMetadata(header);

        assertThat(meta.name()).isEmpty();
        assertThat(meta.version()).isEmpty();
        assertThat(meta.release()).isEmpty();
        assertThat(meta.arch()).isEmpty();
        assertThat(meta.summary()).isEmpty();
        assertThat(meta.description()).isEmpty();
        assertThat(meta.license()).isEmpty();
        assertThat(meta.group()).isEmpty();
        assertThat(meta.url()).isEmpty();
        assertThat(meta.vendor()).isEmpty();
        assertThat(meta.packager()).isEmpty();
        assertThat(meta.distribution()).isEmpty();
        assertThat(meta.buildTime()).isEmpty();
        assertThat(meta.buildHost()).isEmpty();
        assertThat(meta.sourceRpm()).isEmpty();
        assertThat(meta.size()).isEqualTo(0);
        assertThat(meta.archiveSize()).isEqualTo(0);
        assertThat(meta.payloadFormat()).isEqualTo("cpio");
        assertThat(meta.payloadCompressor()).isEqualTo("gzip");
        assertThat(meta.payloadFlags()).isEmpty();
        assertThat(meta.requires()).isEmpty();
        assertThat(meta.provides()).isEmpty();
        assertThat(meta.conflicts()).isEmpty();
        assertThat(meta.obsoletes()).isEmpty();
        assertThat(meta.recommends()).isEmpty();
        assertThat(meta.suggests()).isEmpty();
        assertThat(meta.supplements()).isEmpty();
        assertThat(meta.enhances()).isEmpty();
        assertThat(meta.files()).isEmpty();
        assertThat(meta.changelog()).isEmpty();
        assertThat(meta.preInstallScript()).isEmpty();
        assertThat(meta.postInstallScript()).isEmpty();
        assertThat(meta.preUninstallScript()).isEmpty();
        assertThat(meta.postUninstallScript()).isEmpty();
        assertThat(meta.preTransactionScript()).isEmpty();
        assertThat(meta.postTransactionScript()).isEmpty();
        assertThat(meta.verifyScript()).isEmpty();
    }

    // Helper methods

    private Header createHeaderWithBasicInfo() throws Exception {
        ByteArrayOutputStream dataStore = new ByteArrayOutputStream();

        // Write "test-package\0"
        byte[] name = "test-package\0".getBytes(StandardCharsets.US_ASCII);
        int nameOffset = dataStore.size();
        dataStore.write(name);

        // Write "1.0.0\0"
        byte[] version = "1.0.0\0".getBytes(StandardCharsets.US_ASCII);
        int versionOffset = dataStore.size();
        dataStore.write(version);

        // Write "1.fc39\0"
        byte[] release = "1.fc39\0".getBytes(StandardCharsets.US_ASCII);
        int releaseOffset = dataStore.size();
        dataStore.write(release);

        // Write "x86_64\0"
        byte[] arch = "x86_64\0".getBytes(StandardCharsets.US_ASCII);
        int archOffset = dataStore.size();
        dataStore.write(arch);

        List<IndexEntry> entries = Arrays.asList(
                new IndexEntry(HeaderTag.NAME.tag(), TagType.STRING, nameOffset, 1),
                new IndexEntry(HeaderTag.VERSION.tag(), TagType.STRING, versionOffset, 1),
                new IndexEntry(HeaderTag.RELEASE.tag(), TagType.STRING, releaseOffset, 1),
                new IndexEntry(HeaderTag.ARCH.tag(), TagType.STRING, archOffset, 1)
        );

        return new Header(entries, dataStore.toByteArray());
    }

    private Header createHeaderWithPayloadInfo() throws Exception {
        ByteArrayOutputStream dataStore = new ByteArrayOutputStream();

        byte[] format = "cpio\0".getBytes(StandardCharsets.US_ASCII);
        int formatOffset = dataStore.size();
        dataStore.write(format);

        byte[] compressor = "xz\0".getBytes(StandardCharsets.US_ASCII);
        int compressorOffset = dataStore.size();
        dataStore.write(compressor);

        List<IndexEntry> entries = Arrays.asList(
                new IndexEntry(HeaderTag.PAYLOADFORMAT.tag(), TagType.STRING, formatOffset, 1),
                new IndexEntry(HeaderTag.PAYLOADCOMPRESSOR.tag(), TagType.STRING, compressorOffset, 1)
        );

        return new Header(entries, dataStore.toByteArray());
    }
}
