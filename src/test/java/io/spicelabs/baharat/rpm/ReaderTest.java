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

import io.spicelabs.baharat.rpm.exception.InvalidFormatException;
import io.spicelabs.baharat.rpm.header.HeaderTag;
import io.spicelabs.baharat.rpm.metadata.Changelog;
import io.spicelabs.baharat.rpm.metadata.Dependency;
import io.spicelabs.baharat.rpm.metadata.DependencyType;
import io.spicelabs.baharat.rpm.metadata.FileInfo;
import io.spicelabs.baharat.rpm.testdata.TestFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReaderTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsNonRpmFile() throws IOException {
        Path notRpm = tempDir.resolve("not-an-rpm.txt");
        Files.writeString(notRpm, "This is not an RPM file");

        assertThatThrownBy(() -> RpmReader.read(notRpm))
                .isInstanceOf(InvalidFormatException.class)
                .hasMessageContaining("Invalid RPM magic");
    }

    @Test
    void isRpmDetectsNonRpmFile() throws IOException {
        Path notRpm = tempDir.resolve("not-an-rpm.txt");
        Files.writeString(notRpm, "This is not an RPM file");

        assertThat(RpmReader.isRpm(notRpm)).isFalse();
    }

    @Test
    void isRpmDetectsValidMagic() throws IOException {
        Path fakeRpm = tempDir.resolve("fake.rpm");
        byte[] magic = new byte[]{(byte) 0xED, (byte) 0xAB, (byte) 0xEE, (byte) 0xDB};
        Files.write(fakeRpm, magic);

        assertThat(RpmReader.isRpm(fakeRpm)).isTrue();
    }

    @Test
    void dependencyFlags() {
        Dependency dep = new Dependency(
                DependencyType.REQUIRES,
                "test-package",
                Optional.of("1.0"),
                Dependency.RPMSENSE_GREATER | Dependency.RPMSENSE_EQUAL
        );

        assertThat(dep.isGreaterThan()).isTrue();
        assertThat(dep.isEqual()).isTrue();
        assertThat(dep.isLessThan()).isFalse();
        assertThat(dep.operator()).isEqualTo(">=");
        assertThat(dep.toVersionedString()).isEqualTo("test-package >= 1.0");
    }

    @Test
    void dependencyOperators() {
        assertThat(createDep(Dependency.RPMSENSE_LESS).operator()).isEqualTo("<");
        assertThat(createDep(Dependency.RPMSENSE_GREATER).operator()).isEqualTo(">");
        assertThat(createDep(Dependency.RPMSENSE_EQUAL).operator()).isEqualTo("=");
        assertThat(createDep(Dependency.RPMSENSE_LESS | Dependency.RPMSENSE_EQUAL).operator()).isEqualTo("<=");
        assertThat(createDep(Dependency.RPMSENSE_GREATER | Dependency.RPMSENSE_EQUAL).operator()).isEqualTo(">=");
        assertThat(createDep(0).operator()).isEmpty();
    }

    @Test
    void fileInfoProperties() {
        FileInfo file = new FileInfo(
                "/usr/bin/test",
                1024,
                0100755,
                Instant.now(),
                FileInfo.RPMFILE_CONFIG,
                "root",
                "root",
                Optional.of("abc123"),
                Optional.empty()
        );

        assertThat(file.isRegularFile()).isTrue();
        assertThat(file.isDirectory()).isFalse();
        assertThat(file.isSymbolicLink()).isFalse();
        assertThat(file.isConfig()).isTrue();
        assertThat(file.isDoc()).isFalse();
        assertThat(file.permissions()).isEqualTo(0755);
        assertThat(file.fileType()).isEqualTo("file");
    }

    @Test
    void fileInfoTypes() {
        assertThat(createFileInfo(FileInfo.S_IFREG).fileType()).isEqualTo("file");
        assertThat(createFileInfo(FileInfo.S_IFDIR).fileType()).isEqualTo("directory");
        assertThat(createFileInfo(FileInfo.S_IFLNK).fileType()).isEqualTo("symlink");
        assertThat(createFileInfo(FileInfo.S_IFBLK).fileType()).isEqualTo("block device");
        assertThat(createFileInfo(FileInfo.S_IFCHR).fileType()).isEqualTo("character device");
        assertThat(createFileInfo(FileInfo.S_IFIFO).fileType()).isEqualTo("fifo");
        assertThat(createFileInfo(FileInfo.S_IFSOCK).fileType()).isEqualTo("socket");
    }

    @Test
    void changelogEntry() {
        Changelog entry = Changelog.fromUnixTime(1704067200, "Test User <test@example.com>", "Initial release");

        assertThat(entry.author()).isEqualTo("Test User <test@example.com>");
        assertThat(entry.text()).isEqualTo("Initial release");
        assertThat(entry.time()).isEqualTo(Instant.parse("2024-01-01T00:00:00Z"));
    }

    @Test
    void headerTagLookup() {
        assertThat(HeaderTag.fromTag(1000)).hasValue(HeaderTag.NAME);
        assertThat(HeaderTag.fromTag(1001)).hasValue(HeaderTag.VERSION);
        assertThat(HeaderTag.fromTag(1002)).hasValue(HeaderTag.RELEASE);
        assertThat(HeaderTag.fromTag(1022)).hasValue(HeaderTag.ARCH);
        assertThat(HeaderTag.fromTag(99999)).isEmpty();
    }

    @Test
    void headerTagValues() {
        assertThat(HeaderTag.NAME.tag()).isEqualTo(1000);
        assertThat(HeaderTag.VERSION.tag()).isEqualTo(1001);
        assertThat(HeaderTag.RELEASE.tag()).isEqualTo(1002);
        assertThat(HeaderTag.ARCH.tag()).isEqualTo(1022);
        assertThat(HeaderTag.PAYLOADCOMPRESSOR.tag()).isEqualTo(1125);
    }

    private Dependency createDep(int flags) {
        return new Dependency(DependencyType.REQUIRES, "test", Optional.of("1.0"), flags);
    }

    private FileInfo createFileInfo(int typeMode) {
        return new FileInfo(
                "/test",
                0,
                typeMode | 0644,
                Instant.now(),
                0,
                "root",
                "root",
                Optional.empty(),
                Optional.empty()
        );
    }
}
