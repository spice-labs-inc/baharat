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
package io.spicelabs.baharat.common;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link FileInfo}.
 */
class FileInfoTest {

    private static final Instant NOW = Instant.now();

    // Factory method tests

    @Test
    void ofFileCreatesRegularFile() {
        // Note: ofFile() does NOT automatically add S_IFREG, unlike ofDirectory()
        FileInfo file = FileInfo.ofFile("/usr/bin/test", 1024, FileInfo.S_IFREG | 0644);

        assertThat(file.path()).isEqualTo("/usr/bin/test");
        assertThat(file.size()).isEqualTo(1024);
        assertThat(file.permissions()).isEqualTo(0644);
        assertThat(file.isRegularFile()).isTrue();
        assertThat(file.isDirectory()).isFalse();
        assertThat(file.isSymbolicLink()).isFalse();
    }

    @Test
    void ofDirectoryCreatesDirectory() {
        FileInfo dir = FileInfo.ofDirectory("/usr/share/test", 0755);

        assertThat(dir.path()).isEqualTo("/usr/share/test");
        assertThat(dir.size()).isEqualTo(0);
        assertThat(dir.permissions()).isEqualTo(0755);
        assertThat(dir.isRegularFile()).isFalse();
        assertThat(dir.isDirectory()).isTrue();
        assertThat(dir.isSymbolicLink()).isFalse();
    }

    @Test
    void ofSymlinkCreatesSymlink() {
        FileInfo link = FileInfo.ofSymlink("/usr/bin/python", "/usr/bin/python3");

        assertThat(link.path()).isEqualTo("/usr/bin/python");
        assertThat(link.linkTarget()).contains("/usr/bin/python3");
        assertThat(link.permissions()).isEqualTo(0777);
        assertThat(link.isRegularFile()).isFalse();
        assertThat(link.isDirectory()).isFalse();
        assertThat(link.isSymbolicLink()).isTrue();
    }

    // Mode constants tests

    @Test
    void fileTypeMasks() {
        assertThat(FileInfo.S_IFMT).isEqualTo(0170000);
        assertThat(FileInfo.S_IFREG).isEqualTo(0100000);
        assertThat(FileInfo.S_IFDIR).isEqualTo(0040000);
        assertThat(FileInfo.S_IFLNK).isEqualTo(0120000);
        assertThat(FileInfo.S_IFBLK).isEqualTo(0060000);
        assertThat(FileInfo.S_IFCHR).isEqualTo(0020000);
        assertThat(FileInfo.S_IFIFO).isEqualTo(0010000);
        assertThat(FileInfo.S_IFSOCK).isEqualTo(0140000);
    }

    @Test
    void flagConstants() {
        assertThat(FileInfo.FLAG_CONFIG).isEqualTo(1);
        assertThat(FileInfo.FLAG_DOC).isEqualTo(2);
        assertThat(FileInfo.FLAG_LICENSE).isEqualTo(4);
        assertThat(FileInfo.FLAG_GHOST).isEqualTo(8);
        assertThat(FileInfo.FLAG_NOREPLACE).isEqualTo(16);
        assertThat(FileInfo.FLAG_README).isEqualTo(32);
    }

    // File type detection tests

    @Test
    void isRegularFile() {
        FileInfo file = new FileInfo("/test", 100, FileInfo.S_IFREG | 0644,
                NOW, "root", "root", Optional.empty(), Optional.empty(), 0);

        assertThat(file.isRegularFile()).isTrue();
        assertThat(file.isDirectory()).isFalse();
        assertThat(file.isSymbolicLink()).isFalse();
    }

    @Test
    void isDirectory() {
        FileInfo dir = new FileInfo("/test", 0, FileInfo.S_IFDIR | 0755,
                NOW, "root", "root", Optional.empty(), Optional.empty(), 0);

        assertThat(dir.isRegularFile()).isFalse();
        assertThat(dir.isDirectory()).isTrue();
        assertThat(dir.isSymbolicLink()).isFalse();
    }

    @Test
    void isSymbolicLink() {
        FileInfo link = new FileInfo("/test", 0, FileInfo.S_IFLNK | 0777,
                NOW, "root", "root", Optional.empty(), Optional.of("/target"), 0);

        assertThat(link.isRegularFile()).isFalse();
        assertThat(link.isDirectory()).isFalse();
        assertThat(link.isSymbolicLink()).isTrue();
    }

    // Permissions tests

    @Test
    void permissionsExtracted() {
        FileInfo file = new FileInfo("/test", 100, FileInfo.S_IFREG | 0755,
                NOW, "root", "root", Optional.empty(), Optional.empty(), 0);

        assertThat(file.permissions()).isEqualTo(0755);
    }

    @Test
    void permissionsWithSetuid() {
        int mode = FileInfo.S_IFREG | 04755; // setuid
        FileInfo file = new FileInfo("/test", 100, mode,
                NOW, "root", "root", Optional.empty(), Optional.empty(), 0);

        assertThat(file.permissions()).isEqualTo(04755);
    }

    @Test
    void permissionsWithSetgid() {
        int mode = FileInfo.S_IFREG | 02755; // setgid
        FileInfo file = new FileInfo("/test", 100, mode,
                NOW, "root", "root", Optional.empty(), Optional.empty(), 0);

        assertThat(file.permissions()).isEqualTo(02755);
    }

    @Test
    void permissionsWithSticky() {
        int mode = FileInfo.S_IFDIR | 01777; // sticky
        FileInfo dir = new FileInfo("/tmp", 0, mode,
                NOW, "root", "root", Optional.empty(), Optional.empty(), 0);

        assertThat(dir.permissions()).isEqualTo(01777);
    }

    // Flag tests

    @Test
    void isConfig() {
        FileInfo config = new FileInfo("/etc/test.conf", 100, FileInfo.S_IFREG | 0644,
                NOW, "root", "root", Optional.empty(), Optional.empty(), FileInfo.FLAG_CONFIG);

        assertThat(config.isConfig()).isTrue();
        assertThat(config.isDoc()).isFalse();
    }

    @Test
    void isDoc() {
        FileInfo doc = new FileInfo("/usr/share/doc/test/README", 100, FileInfo.S_IFREG | 0644,
                NOW, "root", "root", Optional.empty(), Optional.empty(), FileInfo.FLAG_DOC);

        assertThat(doc.isConfig()).isFalse();
        assertThat(doc.isDoc()).isTrue();
    }

    @Test
    void isLicense() {
        FileInfo license = new FileInfo("/usr/share/licenses/test/LICENSE", 100, FileInfo.S_IFREG | 0644,
                NOW, "root", "root", Optional.empty(), Optional.empty(), FileInfo.FLAG_LICENSE);

        assertThat(license.isLicense()).isTrue();
    }

    @Test
    void isGhost() {
        FileInfo ghost = new FileInfo("/var/run/test.pid", 0, FileInfo.S_IFREG | 0644,
                NOW, "root", "root", Optional.empty(), Optional.empty(), FileInfo.FLAG_GHOST);

        assertThat(ghost.isGhost()).isTrue();
    }

    @Test
    void multipleFlags() {
        int flags = FileInfo.FLAG_CONFIG | FileInfo.FLAG_NOREPLACE;
        FileInfo file = new FileInfo("/etc/test.conf", 100, FileInfo.S_IFREG | 0644,
                NOW, "root", "root", Optional.empty(), Optional.empty(), flags);

        assertThat(file.isConfig()).isTrue();
        assertThat((file.flags() & FileInfo.FLAG_NOREPLACE) != 0).isTrue();
    }

    // File type string tests

    @Test
    void fileTypeRegular() {
        FileInfo file = new FileInfo("/test", 100, FileInfo.S_IFREG | 0644,
                NOW, "root", "root", Optional.empty(), Optional.empty(), 0);

        assertThat(file.fileType()).isEqualTo("file");
    }

    @Test
    void fileTypeDirectory() {
        FileInfo dir = new FileInfo("/test", 0, FileInfo.S_IFDIR | 0755,
                NOW, "root", "root", Optional.empty(), Optional.empty(), 0);

        assertThat(dir.fileType()).isEqualTo("directory");
    }

    @Test
    void fileTypeSymlink() {
        FileInfo link = new FileInfo("/test", 0, FileInfo.S_IFLNK | 0777,
                NOW, "root", "root", Optional.empty(), Optional.of("/target"), 0);

        assertThat(link.fileType()).isEqualTo("symlink");
    }

    @Test
    void fileTypeBlockDevice() {
        FileInfo dev = new FileInfo("/dev/sda", 0, FileInfo.S_IFBLK | 0660,
                NOW, "root", "disk", Optional.empty(), Optional.empty(), 0);

        assertThat(dev.fileType()).isEqualTo("block device");
    }

    @Test
    void fileTypeCharDevice() {
        FileInfo dev = new FileInfo("/dev/null", 0, FileInfo.S_IFCHR | 0666,
                NOW, "root", "root", Optional.empty(), Optional.empty(), 0);

        assertThat(dev.fileType()).isEqualTo("character device");
    }

    @Test
    void fileTypeFifo() {
        FileInfo fifo = new FileInfo("/tmp/pipe", 0, FileInfo.S_IFIFO | 0644,
                NOW, "root", "root", Optional.empty(), Optional.empty(), 0);

        assertThat(fifo.fileType()).isEqualTo("fifo");
    }

    @Test
    void fileTypeSocket() {
        FileInfo sock = new FileInfo("/var/run/test.sock", 0, FileInfo.S_IFSOCK | 0755,
                NOW, "root", "root", Optional.empty(), Optional.empty(), 0);

        assertThat(sock.fileType()).isEqualTo("socket");
    }

    @Test
    void fileTypeUnknown() {
        FileInfo unknown = new FileInfo("/test", 0, 0, // No type bits
                NOW, "root", "root", Optional.empty(), Optional.empty(), 0);

        assertThat(unknown.fileType()).isEqualTo("unknown");
    }

    // Digest tests

    @Test
    void digestPresent() {
        FileInfo file = new FileInfo("/test", 100, FileInfo.S_IFREG | 0644,
                NOW, "root", "root", Optional.of("sha256:abcd1234"), Optional.empty(), 0);

        assertThat(file.digest()).contains("sha256:abcd1234");
    }

    @Test
    void digestAbsent() {
        FileInfo file = FileInfo.ofFile("/test", 100, 0644);

        assertThat(file.digest()).isEmpty();
    }

    // Record equality tests

    @Test
    void fileInfoEquality() {
        FileInfo file1 = new FileInfo("/test", 100, FileInfo.S_IFREG | 0644,
                NOW, "root", "root", Optional.empty(), Optional.empty(), 0);
        FileInfo file2 = new FileInfo("/test", 100, FileInfo.S_IFREG | 0644,
                NOW, "root", "root", Optional.empty(), Optional.empty(), 0);

        assertThat(file1).isEqualTo(file2);
        assertThat(file1.hashCode()).isEqualTo(file2.hashCode());
    }

    @Test
    void fileInfoInequality() {
        FileInfo file1 = FileInfo.ofFile("/test1", 100, 0644);
        FileInfo file2 = FileInfo.ofFile("/test2", 100, 0644);

        assertThat(file1).isNotEqualTo(file2);
    }

    // Edge cases

    @Test
    void emptyPath() {
        FileInfo file = FileInfo.ofFile("", 0, 0644);

        assertThat(file.path()).isEmpty();
    }

    @Test
    void largeFileSize() {
        long largeSize = 10L * 1024 * 1024 * 1024; // 10 GB
        FileInfo file = FileInfo.ofFile("/large", largeSize, 0644);

        assertThat(file.size()).isEqualTo(largeSize);
    }

    @Test
    void pathWithSpecialCharacters() {
        FileInfo file = FileInfo.ofFile("/path with spaces/and-dashes/under_scores", 100, 0644);

        assertThat(file.path()).isEqualTo("/path with spaces/and-dashes/under_scores");
    }
}
