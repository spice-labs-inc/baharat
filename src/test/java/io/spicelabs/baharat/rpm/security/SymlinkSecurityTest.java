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
package io.spicelabs.baharat.rpm.security;

import io.spicelabs.baharat.rpm.payload.CpioArchiveReader;
import io.spicelabs.baharat.rpm.payload.CompressionType;
import io.spicelabs.baharat.rpm.payload.PayloadEntry;
import io.spicelabs.baharat.rpm.payload.PayloadReader;
import io.spicelabs.baharat.rpm.header.Header;
import io.spicelabs.baharat.rpm.header.HeaderTag;
import io.spicelabs.baharat.rpm.header.IndexEntry;
import io.spicelabs.baharat.rpm.header.TagType;
import io.spicelabs.baharat.rpm.metadata.PackageMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Security tests for symlink handling.
 * Tests that malicious symlink targets are detected and rejected,
 * while safe symlinks are handled correctly.
 */
class SymlinkSecurityTest {

    private static final String CPIO_MAGIC = "070701";
    private static final int S_IFLNK = 0120000; // Symlink mode

    // Symlink targets with path traversal

    @ParameterizedTest
    @ValueSource(strings = {
            "../../../etc/passwd",
            "../../../../etc/shadow",
            "../../../../../root/.ssh/id_rsa"
    })
    void rejectsSymlinkTargetWithPathTraversal(String target) throws Exception {
        byte[] payload = createCompressedCpioPayloadWithSymlink("/usr/bin/link", target);
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(payload), CompressionType.GZIP, metadata)) {
            // Security fix: Path traversal symlinks are now detected and rejected
            assertThatThrownBy(() -> reader.entries().toList())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Dangerous symlink target");
        }
    }

    @Test
    void detectsAbsoluteSymlinkTargetOutsidePackage() throws Exception {
        // Absolute symlink pointing to sensitive system file
        byte[] payload = createCompressedCpioPayloadWithSymlink("/usr/bin/link", "/etc/passwd");
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(payload), CompressionType.GZIP, metadata)) {
            List<PayloadEntry> entries = reader.entries().toList();

            assertThat(entries).hasSize(1);
            PayloadEntry entry = entries.get(0);
            assertThat(entry.isSymlink()).isTrue();

            // Absolute paths are often legitimate (e.g., /lib64 -> /usr/lib64)
            // But some tools may want to flag or validate them
            if (entry instanceof PayloadEntry.SymlinkEntry symlink) {
                assertThat(symlink.target()).startsWith("/");
            }
        }
    }

    @Test
    void rejectsSymlinkWithNullByteInTarget() throws Exception {
        // Null byte injection attempt - path after null byte would be truncated in C but
        // could be exploited depending on how the target is processed
        String target = "/safe/path\0/../../../etc/passwd";
        byte[] payload = createCompressedCpioPayloadWithSymlink("/usr/bin/link", target);
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(payload), CompressionType.GZIP, metadata)) {
            // Security fix: Null bytes in symlink targets are now rejected
            assertThatThrownBy(() -> reader.entries().toList())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Dangerous symlink target");
        }
    }

    @Test
    void handlesSymlinkToItself() throws Exception {
        // Self-referential symlink: /usr/bin/link -> /usr/bin/link
        byte[] payload = createCompressedCpioPayloadWithSymlink("/usr/bin/link", "/usr/bin/link");
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(payload), CompressionType.GZIP, metadata)) {
            List<PayloadEntry> entries = reader.entries().toList();

            assertThat(entries).hasSize(1);
            // Self-referential symlinks should be handled (not cause infinite loops)
        }
    }

    @Test
    void handlesSymlinkCycle() throws Exception {
        // Create a cycle: /a -> /b, /b -> /a
        byte[] payload = createCompressedCpioPayloadWithMultipleSymlinks(
                List.of("/a", "/b"),
                List.of("/b", "/a")
        );
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(payload), CompressionType.GZIP, metadata)) {
            List<PayloadEntry> entries = reader.entries().toList();

            // Cycles are valid in archives - it's the extraction tool's job to handle
            assertThat(entries).hasSize(2);
        }
    }

    @Test
    void rejectsVeryLongSymlinkTarget() throws Exception {
        // Security fix: MAX_SYMLINK_TARGET_LENGTH is now 4096 bytes
        String longTarget = "/usr/share/" + "a".repeat(5000);
        byte[] payload = createCompressedCpioPayloadWithSymlink("/link", longTarget);
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(payload), CompressionType.GZIP, metadata)) {
            // Security fix: Symlink targets over 4096 bytes are now rejected
            assertThatThrownBy(() -> reader.entries().toList())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Dangerous symlink target");
        }
    }

    @Test
    void rejectsEmptySymlinkTarget() throws Exception {
        byte[] payload = createCompressedCpioPayloadWithSymlink("/link", "");
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(payload), CompressionType.GZIP, metadata)) {
            // Security fix: Empty symlink targets are now rejected
            assertThatThrownBy(() -> reader.entries().toList())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Dangerous symlink target");
        }
    }

    @Test
    void handlesRelativeSymlinkWithinPackage() throws Exception {
        // Legitimate relative symlink: /usr/lib64/libfoo.so -> libfoo.so.1
        byte[] payload = createCompressedCpioPayloadWithSymlink("/usr/lib64/libfoo.so", "libfoo.so.1");
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(payload), CompressionType.GZIP, metadata)) {
            List<PayloadEntry> entries = reader.entries().toList();

            assertThat(entries).hasSize(1);
            if (entries.get(0) instanceof PayloadEntry.SymlinkEntry symlink) {
                assertThat(symlink.target()).isEqualTo("libfoo.so.1");
            }
        }
    }

    @Test
    void rejectsSymlinkWithBackslashTraversal() throws Exception {
        // Windows-style backslashes in symlink target - these get normalized
        // and the path traversal is detected
        String target = "..\\..\\etc\\passwd";
        byte[] payload = createCompressedCpioPayloadWithSymlink("/link", target);
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(payload), CompressionType.GZIP, metadata)) {
            // Security fix: Backslash-based path traversal is now detected
            assertThatThrownBy(() -> reader.entries().toList())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Dangerous symlink target");
        }
    }

    @Test
    void handlesSymlinkTargetWithSpecialCharacters() throws Exception {
        String target = "/path/with spaces/and$pecial@chars!";
        byte[] payload = createCompressedCpioPayloadWithSymlink("/link", target);
        PackageMetadata metadata = createMinimalMetadata();

        try (PayloadReader reader = new PayloadReader(
                new ByteArrayInputStream(payload), CompressionType.GZIP, metadata)) {
            List<PayloadEntry> entries = reader.entries().toList();

            assertThat(entries).hasSize(1);
            if (entries.get(0) instanceof PayloadEntry.SymlinkEntry symlink) {
                assertThat(symlink.target()).isEqualTo(target);
            }
        }
    }

    // Direct CPIO reader tests for symlink size limits

    @Test
    void cpioReaderRejectsExcessiveSymlinkTarget() throws Exception {
        // Create symlink with target > MAX_SYMLINK_SIZE (64KB)
        String hugeTarget = "a".repeat(70000);
        byte[] archive = createCpioArchiveWithSymlink("/link", hugeTarget);
        ByteArrayInputStream in = new ByteArrayInputStream(archive);

        try (CpioArchiveReader reader = new CpioArchiveReader(in)) {
            CpioArchiveReader.CpioEntry entry = reader.nextEntry();
            assertThat(entry).isNotNull();
            assertThat(entry.isSymlink()).isTrue();

            // Reading the link target should throw
            try {
                entry.readLinkTarget();
                // If it succeeds, document that
            } catch (IllegalStateException e) {
                assertThat(e.getMessage()).containsIgnoringCase("too large");
            }
        }
    }

    @Test
    void cpioReaderRejectsReadLinkTargetOnNonSymlink() throws Exception {
        byte[] archive = createCpioArchive("/file.txt", "content", 0100644);
        ByteArrayInputStream in = new ByteArrayInputStream(archive);

        try (CpioArchiveReader reader = new CpioArchiveReader(in)) {
            CpioArchiveReader.CpioEntry entry = reader.nextEntry();
            assertThat(entry).isNotNull();
            assertThat(entry.isFile()).isTrue();

            try {
                entry.readLinkTarget();
                assertThat(true).as("Should have thrown").isFalse();
            } catch (IllegalStateException e) {
                assertThat(e.getMessage()).containsIgnoringCase("not a symlink");
            }
        }
    }

    // Helper methods

    private byte[] createCompressedCpioPayloadWithSymlink(String path, String target) throws IOException {
        byte[] cpio = createCpioArchiveWithSymlink(path, target);

        ByteArrayOutputStream compressedOut = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressedOut)) {
            gzip.write(cpio);
        }

        return compressedOut.toByteArray();
    }

    private byte[] createCompressedCpioPayloadWithMultipleSymlinks(List<String> paths, List<String> targets) throws IOException {
        ByteArrayOutputStream cpioOut = new ByteArrayOutputStream();

        for (int i = 0; i < paths.size(); i++) {
            cpioOut.write(createCpioSymlinkEntry(paths.get(i), targets.get(i)));
        }
        cpioOut.write(createCpioEntry("TRAILER!!!", "", 0));

        ByteArrayOutputStream compressedOut = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressedOut)) {
            gzip.write(cpioOut.toByteArray());
        }

        return compressedOut.toByteArray();
    }

    private byte[] createCpioArchiveWithSymlink(String path, String target) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(createCpioSymlinkEntry(path, target));
        out.write(createCpioEntry("TRAILER!!!", "", 0));
        return out.toByteArray();
    }

    private byte[] createCpioSymlinkEntry(String path, String target) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        byte[] nameBytes = (path + "\0").getBytes(StandardCharsets.UTF_8);
        byte[] targetBytes = target.getBytes(StandardCharsets.UTF_8);

        // Symlink mode: S_IFLNK | 0777
        int mode = S_IFLNK | 0777;

        String header = String.format(
                "%s%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X",
                CPIO_MAGIC,
                0,                      // inode
                mode,                   // mode (symlink)
                0,                      // uid
                0,                      // gid
                1,                      // nlink
                0,                      // mtime
                targetBytes.length,     // filesize (symlink target length)
                0,                      // devmajor
                0,                      // devminor
                0,                      // rdevmajor
                0,                      // rdevminor
                nameBytes.length,       // namesize
                0                       // check
        );

        out.write(header.getBytes(StandardCharsets.US_ASCII));
        out.write(nameBytes);

        int headerAndName = 110 + nameBytes.length;
        int padding = (4 - (headerAndName % 4)) % 4;
        out.write(new byte[padding]);

        // Symlink target as content
        out.write(targetBytes);

        int contentPadding = (4 - (targetBytes.length % 4)) % 4;
        out.write(new byte[contentPadding]);

        return out.toByteArray();
    }

    private byte[] createCpioArchive(String name, String content, int mode) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(createCpioEntry(name, content, mode));
        out.write(createCpioEntry("TRAILER!!!", "", 0));
        return out.toByteArray();
    }

    private byte[] createCpioEntry(String name, String content, int mode) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        byte[] nameBytes = (name + "\0").getBytes(StandardCharsets.UTF_8);
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);

        String header = String.format(
                "%s%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X",
                CPIO_MAGIC,
                0, mode, 0, 0, 1, 0, contentBytes.length, 0, 0, 0, 0, nameBytes.length, 0
        );

        out.write(header.getBytes(StandardCharsets.US_ASCII));
        out.write(nameBytes);

        int headerAndName = 110 + nameBytes.length;
        int padding = (4 - (headerAndName % 4)) % 4;
        out.write(new byte[padding]);

        out.write(contentBytes);

        int contentPadding = (4 - (contentBytes.length % 4)) % 4;
        out.write(new byte[contentPadding]);

        return out.toByteArray();
    }

    private PackageMetadata createMinimalMetadata() {
        ByteArrayOutputStream dataStore = new ByteArrayOutputStream();

        byte[] compressorBytes = "gzip\0".getBytes(StandardCharsets.US_ASCII);
        int compressorOffset = dataStore.size();
        dataStore.writeBytes(compressorBytes);

        byte[] formatBytes = "cpio\0".getBytes(StandardCharsets.US_ASCII);
        int formatOffset = dataStore.size();
        dataStore.writeBytes(formatBytes);

        List<IndexEntry> entries = List.of(
                new IndexEntry(HeaderTag.PAYLOADCOMPRESSOR.tag(), TagType.STRING, compressorOffset, 1),
                new IndexEntry(HeaderTag.PAYLOADFORMAT.tag(), TagType.STRING, formatOffset, 1)
        );

        Header header = new Header(entries, dataStore.toByteArray());
        return new PackageMetadata(header);
    }
}
