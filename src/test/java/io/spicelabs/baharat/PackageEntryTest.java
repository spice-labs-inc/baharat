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

import io.spicelabs.baharat.common.FileInfo;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link PackageEntry} sealed interface and its implementations.
 */
class PackageEntryTest {

    private static final Instant NOW = Instant.now();
    private static final int FILE_MODE = FileInfo.S_IFREG | 0644;
    private static final int DIR_MODE = FileInfo.S_IFDIR | 0755;
    private static final int LINK_MODE = FileInfo.S_IFLNK | 0777;

    // FileEntry tests

    @Test
    void fileEntryBasicProperties() {
        InputStream content = new ByteArrayInputStream("test".getBytes(StandardCharsets.UTF_8));
        PackageEntry.FileEntry entry = new PackageEntry.FileEntry(
                "/usr/bin/test", FILE_MODE, NOW, "root", "root", 4, content);

        assertThat(entry.path()).isEqualTo("/usr/bin/test");
        assertThat(entry.mode()).isEqualTo(FILE_MODE);
        assertThat(entry.mtime()).isEqualTo(NOW);
        assertThat(entry.userName()).isEqualTo("root");
        assertThat(entry.groupName()).isEqualTo("root");
        assertThat(entry.size()).isEqualTo(4);
        assertThat(entry.content()).isNotNull();
    }

    @Test
    void fileEntryIsFile() {
        InputStream content = new ByteArrayInputStream(new byte[0]);
        PackageEntry.FileEntry entry = new PackageEntry.FileEntry(
                "/test", FILE_MODE, NOW, "root", "root", 0, content);

        assertThat(entry.isFile()).isTrue();
        assertThat(entry.isDirectory()).isFalse();
        assertThat(entry.isSymlink()).isFalse();
    }

    @Test
    void fileEntryPermissions() {
        InputStream content = new ByteArrayInputStream(new byte[0]);
        int mode = FileInfo.S_IFREG | 0755;
        PackageEntry.FileEntry entry = new PackageEntry.FileEntry(
                "/test", mode, NOW, "root", "root", 0, content);

        assertThat(entry.permissions()).isEqualTo(0755);
    }

    // DirectoryEntry tests

    @Test
    void directoryEntryBasicProperties() {
        PackageEntry.DirectoryEntry entry = new PackageEntry.DirectoryEntry(
                "/usr/share/test", DIR_MODE, NOW, "root", "root");

        assertThat(entry.path()).isEqualTo("/usr/share/test");
        assertThat(entry.mode()).isEqualTo(DIR_MODE);
        assertThat(entry.mtime()).isEqualTo(NOW);
        assertThat(entry.userName()).isEqualTo("root");
        assertThat(entry.groupName()).isEqualTo("root");
    }

    @Test
    void directoryEntryIsDirectory() {
        PackageEntry.DirectoryEntry entry = new PackageEntry.DirectoryEntry(
                "/test", DIR_MODE, NOW, "root", "root");

        assertThat(entry.isFile()).isFalse();
        assertThat(entry.isDirectory()).isTrue();
        assertThat(entry.isSymlink()).isFalse();
    }

    @Test
    void directoryEntryPermissions() {
        int mode = FileInfo.S_IFDIR | 0700;
        PackageEntry.DirectoryEntry entry = new PackageEntry.DirectoryEntry(
                "/test", mode, NOW, "root", "root");

        assertThat(entry.permissions()).isEqualTo(0700);
    }

    // SymlinkEntry tests

    @Test
    void symlinkEntryBasicProperties() {
        PackageEntry.SymlinkEntry entry = new PackageEntry.SymlinkEntry(
                "/usr/bin/link", LINK_MODE, NOW, "root", "root", "/usr/bin/target");

        assertThat(entry.path()).isEqualTo("/usr/bin/link");
        assertThat(entry.mode()).isEqualTo(LINK_MODE);
        assertThat(entry.mtime()).isEqualTo(NOW);
        assertThat(entry.userName()).isEqualTo("root");
        assertThat(entry.groupName()).isEqualTo("root");
        assertThat(entry.target()).isEqualTo("/usr/bin/target");
    }

    @Test
    void symlinkEntryIsSymlink() {
        PackageEntry.SymlinkEntry entry = new PackageEntry.SymlinkEntry(
                "/test", LINK_MODE, NOW, "root", "root", "/target");

        assertThat(entry.isFile()).isFalse();
        assertThat(entry.isDirectory()).isFalse();
        assertThat(entry.isSymlink()).isTrue();
    }

    @Test
    void symlinkEntryPermissions() {
        PackageEntry.SymlinkEntry entry = new PackageEntry.SymlinkEntry(
                "/test", LINK_MODE, NOW, "root", "root", "/target");

        // Symlinks typically have 0777 permissions
        assertThat(entry.permissions()).isEqualTo(0777);
    }

    // Pattern matching tests

    @Test
    void patternMatchingOnEntryType() {
        PackageEntry fileEntry = new PackageEntry.FileEntry(
                "/file", FILE_MODE, NOW, "root", "root", 100,
                new ByteArrayInputStream(new byte[0]));

        PackageEntry dirEntry = new PackageEntry.DirectoryEntry(
                "/dir", DIR_MODE, NOW, "root", "root");

        PackageEntry linkEntry = new PackageEntry.SymlinkEntry(
                "/link", LINK_MODE, NOW, "root", "root", "/target");

        // Test pattern matching with switch expression
        String fileResult = describeEntry(fileEntry);
        String dirResult = describeEntry(dirEntry);
        String linkResult = describeEntry(linkEntry);

        assertThat(fileResult).startsWith("File:");
        assertThat(dirResult).startsWith("Dir:");
        assertThat(linkResult).startsWith("Link:");
    }

    private String describeEntry(PackageEntry entry) {
        return switch (entry) {
            case PackageEntry.FileEntry f -> "File: " + f.path() + " (" + f.size() + " bytes)";
            case PackageEntry.DirectoryEntry d -> "Dir: " + d.path();
            case PackageEntry.SymlinkEntry s -> "Link: " + s.path() + " -> " + s.target();
        };
    }

    // Record equality tests

    @Test
    void fileEntryEquality() {
        InputStream content1 = new ByteArrayInputStream(new byte[0]);
        InputStream content2 = new ByteArrayInputStream(new byte[0]);

        PackageEntry.FileEntry entry1 = new PackageEntry.FileEntry(
                "/test", FILE_MODE, NOW, "root", "root", 0, content1);

        PackageEntry.FileEntry entry2 = new PackageEntry.FileEntry(
                "/test", FILE_MODE, NOW, "root", "root", 0, content2);

        // Different content streams mean different entries (reference equality for streams)
        assertThat(entry1).isNotEqualTo(entry2);
    }

    @Test
    void directoryEntryEquality() {
        PackageEntry.DirectoryEntry entry1 = new PackageEntry.DirectoryEntry(
                "/test", DIR_MODE, NOW, "root", "root");

        PackageEntry.DirectoryEntry entry2 = new PackageEntry.DirectoryEntry(
                "/test", DIR_MODE, NOW, "root", "root");

        assertThat(entry1).isEqualTo(entry2);
    }

    @Test
    void symlinkEntryEquality() {
        PackageEntry.SymlinkEntry entry1 = new PackageEntry.SymlinkEntry(
                "/link", LINK_MODE, NOW, "root", "root", "/target");

        PackageEntry.SymlinkEntry entry2 = new PackageEntry.SymlinkEntry(
                "/link", LINK_MODE, NOW, "root", "root", "/target");

        assertThat(entry1).isEqualTo(entry2);
    }

    // Different users/groups

    @Test
    void entryWithDifferentOwner() {
        PackageEntry.DirectoryEntry entry = new PackageEntry.DirectoryEntry(
                "/home/user", DIR_MODE, NOW, "user", "users");

        assertThat(entry.userName()).isEqualTo("user");
        assertThat(entry.groupName()).isEqualTo("users");
    }

    // Edge cases

    @Test
    void entryWithEmptyPath() {
        PackageEntry.DirectoryEntry entry = new PackageEntry.DirectoryEntry(
                "", DIR_MODE, NOW, "root", "root");

        assertThat(entry.path()).isEmpty();
    }

    @Test
    void entryWithRootPath() {
        PackageEntry.DirectoryEntry entry = new PackageEntry.DirectoryEntry(
                "/", DIR_MODE, NOW, "root", "root");

        assertThat(entry.path()).isEqualTo("/");
    }

    @Test
    void entryWithSpecialCharactersInPath() {
        PackageEntry.DirectoryEntry entry = new PackageEntry.DirectoryEntry(
                "/usr/share/test file with spaces", DIR_MODE, NOW, "root", "root");

        assertThat(entry.path()).isEqualTo("/usr/share/test file with spaces");
    }

    @Test
    void symlinkWithRelativeTarget() {
        PackageEntry.SymlinkEntry entry = new PackageEntry.SymlinkEntry(
                "/usr/bin/python", LINK_MODE, NOW, "root", "root", "python3.11");

        assertThat(entry.target()).isEqualTo("python3.11");
    }

    @Test
    void symlinkWithAbsoluteTarget() {
        PackageEntry.SymlinkEntry entry = new PackageEntry.SymlinkEntry(
                "/usr/bin/python", LINK_MODE, NOW, "root", "root", "/usr/bin/python3.11");

        assertThat(entry.target()).isEqualTo("/usr/bin/python3.11");
    }

    @Test
    void fileEntryWithZeroSize() {
        InputStream content = new ByteArrayInputStream(new byte[0]);
        PackageEntry.FileEntry entry = new PackageEntry.FileEntry(
                "/empty", FILE_MODE, NOW, "root", "root", 0, content);

        assertThat(entry.size()).isEqualTo(0);
    }

    @Test
    void fileEntryWithLargeSize() {
        InputStream content = new ByteArrayInputStream(new byte[0]);
        long largeSize = 10L * 1024 * 1024 * 1024; // 10 GB
        PackageEntry.FileEntry entry = new PackageEntry.FileEntry(
                "/large", FILE_MODE, NOW, "root", "root", largeSize, content);

        assertThat(entry.size()).isEqualTo(largeSize);
    }
}
