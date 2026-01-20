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
package io.spicelabs.baharat.rpm.payload;

import io.spicelabs.baharat.rpm.exception.InvalidFormatException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CpioArchiveReaderTest {

    @Test
    void readsEmptyArchive() throws Exception {
        byte[] archive = createCpioArchive();

        try (CpioArchiveReader reader = new CpioArchiveReader(new ByteArrayInputStream(archive))) {
            CpioArchiveReader.CpioEntry entry = reader.nextEntry();
            assertThat(entry).isNull();
        }
    }

    @Test
    void readsFileEntry() throws Exception {
        byte[] archive = createCpioArchiveWithFile("test.txt", "Hello, World!", 0100644);

        try (CpioArchiveReader reader = new CpioArchiveReader(new ByteArrayInputStream(archive))) {
            CpioArchiveReader.CpioEntry entry = reader.nextEntry();

            assertThat(entry).isNotNull();
            assertThat(entry.name()).isEqualTo("test.txt");
            assertThat(entry.size()).isEqualTo(13);
            assertThat(entry.isFile()).isTrue();
            assertThat(entry.isDirectory()).isFalse();
            assertThat(entry.permissions()).isEqualTo(0644);

            // Read content
            byte[] content = entry.dataStream().readNBytes((int) entry.size());
            assertThat(new String(content, StandardCharsets.UTF_8)).isEqualTo("Hello, World!");

            // Should be end of archive
            assertThat(reader.nextEntry()).isNull();
        }
    }

    @Test
    void readsDirectoryEntry() throws Exception {
        byte[] archive = createCpioArchiveWithFile("mydir", "", 0040755);

        try (CpioArchiveReader reader = new CpioArchiveReader(new ByteArrayInputStream(archive))) {
            CpioArchiveReader.CpioEntry entry = reader.nextEntry();

            assertThat(entry).isNotNull();
            assertThat(entry.name()).isEqualTo("mydir");
            assertThat(entry.isDirectory()).isTrue();
            assertThat(entry.isFile()).isFalse();
            assertThat(entry.permissions()).isEqualTo(0755);
        }
    }

    @Test
    void rejectsInvalidMagic() throws Exception {
        byte[] archive = createCpioArchiveWithFile("test.txt", "data", 0100644);
        archive[0] = 'X';  // Corrupt magic (change '0' to 'X')

        try (CpioArchiveReader reader = new CpioArchiveReader(new ByteArrayInputStream(archive))) {
            assertThatThrownBy(reader::nextEntry)
                    .isInstanceOf(InvalidFormatException.class)
                    .hasMessageContaining("Invalid CPIO magic");
        }
    }

    @Test
    void streamsEntries() throws Exception {
        byte[] archive = createCpioArchiveWithFile("file.txt", "content", 0100644);

        try (CpioArchiveReader reader = new CpioArchiveReader(new ByteArrayInputStream(archive))) {
            List<CpioArchiveReader.CpioEntry> entries = reader.stream().collect(Collectors.toList());

            assertThat(entries).hasSize(1);
            assertThat(entries.get(0).name()).isEqualTo("file.txt");
        }
    }

    @Test
    void compressionTypeFromName() {
        assertThat(CompressionType.fromName("gzip")).hasValue(CompressionType.GZIP);
        assertThat(CompressionType.fromName("xz")).hasValue(CompressionType.XZ);
        assertThat(CompressionType.fromName("zstd")).hasValue(CompressionType.ZSTD);
        assertThat(CompressionType.fromName("bzip2")).hasValue(CompressionType.BZIP2);
        assertThat(CompressionType.fromName("lzma")).hasValue(CompressionType.LZMA);
        assertThat(CompressionType.fromName("unknown")).isEmpty();
    }

    @Test
    void compressionTypeDetection() {
        assertThat(CompressionType.detect(new byte[]{0x1F, (byte) 0x8B}))
                .hasValue(CompressionType.GZIP);
        assertThat(CompressionType.detect(new byte[]{(byte) 0xFD, 0x37, 0x7A, 0x58, 0x5A, 0x00}))
                .hasValue(CompressionType.XZ);
        assertThat(CompressionType.detect(new byte[]{0x28, (byte) 0xB5, 0x2F, (byte) 0xFD}))
                .hasValue(CompressionType.ZSTD);
        assertThat(CompressionType.detect(new byte[]{0x42, 0x5A}))
                .hasValue(CompressionType.BZIP2);
        assertThat(CompressionType.detect(new byte[]{0x00, 0x00}))
                .isEmpty();
    }

    private byte[] createCpioArchive() throws IOException {
        return createCpioEntry("TRAILER!!!", "", 0);
    }

    private byte[] createCpioArchiveWithFile(String name, String content, int mode) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(createCpioEntry(name, content, mode));
        out.write(createCpioEntry("TRAILER!!!", "", 0));
        return out.toByteArray();
    }

    private byte[] createCpioEntry(String name, String content, int mode) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        byte[] nameBytes = (name + "\0").getBytes(StandardCharsets.US_ASCII);
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);

        // CPIO newc header
        String header = String.format(
                "%s%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X",
                "070701",           // magic
                0,                  // inode
                mode,               // mode
                0,                  // uid
                0,                  // gid
                1,                  // nlink
                0,                  // mtime
                contentBytes.length, // filesize
                0,                  // devmajor
                0,                  // devminor
                0,                  // rdevmajor
                0,                  // rdevminor
                nameBytes.length,   // namesize
                0                   // check
        );

        out.write(header.getBytes(StandardCharsets.US_ASCII));
        out.write(nameBytes);

        // Pad to 4-byte boundary
        int headerAndName = 110 + nameBytes.length;
        int padding = (4 - (headerAndName % 4)) % 4;
        out.write(new byte[padding]);

        // Content
        out.write(contentBytes);

        // Pad content to 4-byte boundary
        int contentPadding = (4 - (contentBytes.length % 4)) % 4;
        out.write(new byte[contentPadding]);

        return out.toByteArray();
    }
}
