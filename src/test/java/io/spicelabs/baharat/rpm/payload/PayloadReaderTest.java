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

import io.spicelabs.baharat.rpm.exception.UnsupportedFormatException;
import io.spicelabs.baharat.rpm.header.Header;
import io.spicelabs.baharat.rpm.header.HeaderTag;
import io.spicelabs.baharat.rpm.header.IndexEntry;
import io.spicelabs.baharat.rpm.header.TagType;
import io.spicelabs.baharat.rpm.metadata.PackageMetadata;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayloadReaderTest {

    @Test
    void payloadEntryTypes() {
        PayloadEntry.FileEntry file = new PayloadEntry.FileEntry(
                "/test.txt",
                0100644,
                java.time.Instant.now(),
                "root",
                "root",
                100,
                java.io.InputStream.nullInputStream()
        );

        assertThat(file.isFile()).isTrue();
        assertThat(file.isDirectory()).isFalse();
        assertThat(file.isSymlink()).isFalse();
        assertThat(file.permissions()).isEqualTo(0644);

        PayloadEntry.DirectoryEntry dir = new PayloadEntry.DirectoryEntry(
                "/mydir",
                0040755,
                java.time.Instant.now(),
                "root",
                "root"
        );

        assertThat(dir.isFile()).isFalse();
        assertThat(dir.isDirectory()).isTrue();
        assertThat(dir.isSymlink()).isFalse();
        assertThat(dir.permissions()).isEqualTo(0755);

        PayloadEntry.SymlinkEntry link = new PayloadEntry.SymlinkEntry(
                "/link",
                0120777,
                java.time.Instant.now(),
                "root",
                "root",
                "/target"
        );

        assertThat(link.isFile()).isFalse();
        assertThat(link.isDirectory()).isFalse();
        assertThat(link.isSymlink()).isTrue();
        assertThat(link.target()).isEqualTo("/target");
    }

    @Test
    void compressionTypeProperties() {
        assertThat(CompressionType.GZIP.compressionName()).isEqualTo("gzip");
        assertThat(CompressionType.XZ.compressionName()).isEqualTo("xz");
        assertThat(CompressionType.ZSTD.compressionName()).isEqualTo("zstd");
        assertThat(CompressionType.BZIP2.compressionName()).isEqualTo("bzip2");
        assertThat(CompressionType.LZMA.compressionName()).isEqualTo("lzma");
    }

    @Test
    void payloadReaderRejectsUnsupportedCompression() {
        InputStream stream = new ByteArrayInputStream(new byte[0]);
        PackageMetadata metadata = createMinimalMetadata("unknown-compression");

        assertThatThrownBy(() -> new PayloadReader(stream, metadata))
                .isInstanceOf(UnsupportedFormatException.class)
                .hasMessageContaining("Unsupported payload compression");
    }

    @Test
    void payloadReaderWithExplicitCompressionType() {
        InputStream stream = new ByteArrayInputStream(new byte[0]);
        PackageMetadata metadata = createMinimalMetadata("gzip");

        PayloadReader reader = new PayloadReader(stream, CompressionType.GZIP, metadata);

        assertThat(reader.compressionType()).isEqualTo(CompressionType.GZIP);
    }

    @Test
    void payloadReaderWithExplicitCompressionTypeAndMaxSize() {
        InputStream stream = new ByteArrayInputStream(new byte[0]);
        PackageMetadata metadata = createMinimalMetadata("gzip");
        long maxSize = 1024 * 1024; // 1 MB

        PayloadReader reader = new PayloadReader(stream, CompressionType.XZ, metadata, maxSize);

        assertThat(reader.compressionType()).isEqualTo(CompressionType.XZ);
    }

    @Test
    void payloadReaderWithCustomMaxDecompressedSize() throws Exception {
        InputStream stream = new ByteArrayInputStream(new byte[0]);
        PackageMetadata metadata = createMinimalMetadata("gzip");
        long maxSize = 1024 * 1024; // 1 MB

        PayloadReader reader = new PayloadReader(stream, metadata, maxSize);

        assertThat(reader.compressionType()).isEqualTo(CompressionType.GZIP);
    }

    @Test
    void payloadReaderClose() throws Exception {
        ByteArrayInputStream stream = new ByteArrayInputStream(new byte[0]);
        PackageMetadata metadata = createMinimalMetadata("gzip");

        PayloadReader reader = new PayloadReader(stream, CompressionType.GZIP, metadata);
        reader.close();

        // Closing should not throw
    }

    @Test
    void fileEntryAccessors() {
        Instant mtime = Instant.ofEpochSecond(1600000000);
        InputStream content = new ByteArrayInputStream(new byte[50]);

        PayloadEntry.FileEntry entry = new PayloadEntry.FileEntry(
                "/usr/bin/app",
                0100755,
                mtime,
                "root",
                "wheel",
                50,
                content
        );

        assertThat(entry.path()).isEqualTo("/usr/bin/app");
        assertThat(entry.mode()).isEqualTo(0100755);
        assertThat(entry.mtime()).isEqualTo(mtime);
        assertThat(entry.userName()).isEqualTo("root");
        assertThat(entry.groupName()).isEqualTo("wheel");
        assertThat(entry.size()).isEqualTo(50);
        assertThat(entry.content()).isSameAs(content);
    }

    @Test
    void directoryEntryAccessors() {
        Instant mtime = Instant.ofEpochSecond(1600000000);

        PayloadEntry.DirectoryEntry entry = new PayloadEntry.DirectoryEntry(
                "/var/log",
                0040755,
                mtime,
                "nobody",
                "nogroup"
        );

        assertThat(entry.path()).isEqualTo("/var/log");
        assertThat(entry.mode()).isEqualTo(0040755);
        assertThat(entry.mtime()).isEqualTo(mtime);
        assertThat(entry.userName()).isEqualTo("nobody");
        assertThat(entry.groupName()).isEqualTo("nogroup");
    }

    @Test
    void symlinkEntryAccessors() {
        Instant mtime = Instant.ofEpochSecond(1600000000);

        PayloadEntry.SymlinkEntry entry = new PayloadEntry.SymlinkEntry(
                "/lib64",
                0120777,
                mtime,
                "root",
                "root",
                "/usr/lib64"
        );

        assertThat(entry.path()).isEqualTo("/lib64");
        assertThat(entry.mode()).isEqualTo(0120777);
        assertThat(entry.mtime()).isEqualTo(mtime);
        assertThat(entry.userName()).isEqualTo("root");
        assertThat(entry.groupName()).isEqualTo("root");
        assertThat(entry.target()).isEqualTo("/usr/lib64");
    }

    @Test
    void compressionTypeMagicBytes() {
        assertThat(CompressionType.GZIP.magic()).containsExactly((byte) 0x1F, (byte) 0x8B);
        assertThat(CompressionType.BZIP2.magic()).containsExactly((byte) 0x42, (byte) 0x5A);
        assertThat(CompressionType.XZ.magic()).hasSize(6);
        assertThat(CompressionType.ZSTD.magic()).hasSize(4);
        assertThat(CompressionType.LZMA.magic()).hasSize(3);
    }

    @Test
    void compressionTypeFromNameHandlesVariants() {
        assertThat(CompressionType.fromName("gzip")).hasValue(CompressionType.GZIP);
        assertThat(CompressionType.fromName("xz")).hasValue(CompressionType.XZ);
        assertThat(CompressionType.fromName("zstd")).hasValue(CompressionType.ZSTD);
        assertThat(CompressionType.fromName("bzip2")).hasValue(CompressionType.BZIP2);
        assertThat(CompressionType.fromName("lzma")).hasValue(CompressionType.LZMA);
        assertThat(CompressionType.fromName("unknown")).isEmpty();
    }

    @Test
    void compressionTypeDetect() {
        // GZIP magic
        assertThat(CompressionType.detect(new byte[]{0x1F, (byte) 0x8B, 0x08}))
                .hasValue(CompressionType.GZIP);

        // BZIP2 magic
        assertThat(CompressionType.detect(new byte[]{0x42, 0x5A, 0x68}))
                .hasValue(CompressionType.BZIP2);

        // XZ magic
        assertThat(CompressionType.detect(new byte[]{(byte) 0xFD, 0x37, 0x7A, 0x58, 0x5A, 0x00}))
                .hasValue(CompressionType.XZ);

        // ZSTD magic
        assertThat(CompressionType.detect(new byte[]{0x28, (byte) 0xB5, 0x2F, (byte) 0xFD}))
                .hasValue(CompressionType.ZSTD);

        // LZMA magic
        assertThat(CompressionType.detect(new byte[]{0x5D, 0x00, 0x00}))
                .hasValue(CompressionType.LZMA);

        // Unknown
        assertThat(CompressionType.detect(new byte[]{0x00, 0x00, 0x00})).isEmpty();
        assertThat(CompressionType.detect(new byte[]{})).isEmpty();
    }

    @Test
    void compressionTypeDecompress() throws Exception {
        // Test GZIP decompression with actual compressed data
        byte[] original = "Hello, World!".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(original);
        }
        byte[] compressed = baos.toByteArray();

        InputStream decompressed = CompressionType.GZIP.decompress(new ByteArrayInputStream(compressed));
        byte[] result = decompressed.readAllBytes();

        assertThat(result).isEqualTo(original);
    }

    @Test
    void allCompressionTypesExist() {
        assertThat(CompressionType.values()).hasSize(5);
        assertThat(CompressionType.values()).containsExactlyInAnyOrder(
                CompressionType.GZIP,
                CompressionType.XZ,
                CompressionType.ZSTD,
                CompressionType.BZIP2,
                CompressionType.LZMA
        );
    }

    private PackageMetadata createMinimalMetadata(String compressor) {
        ByteArrayOutputStream dataStore = new ByteArrayOutputStream();

        // Write compressor name
        byte[] compressorBytes = (compressor + "\0").getBytes(StandardCharsets.US_ASCII);
        int compressorOffset = dataStore.size();
        dataStore.writeBytes(compressorBytes);

        // Write format "cpio"
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
