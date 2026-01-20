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
package io.spicelabs.baharat.rpm.delta;

import io.spicelabs.baharat.rpm.exception.InvalidFormatException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for delta RPM reading functionality.
 */
class DeltaTest {

    @TempDir
    Path tempDir;

    @Test
    void deltaRpmRecordProperties() {
        Delta drpm = new Delta(
                "0003",
                "test-package-1.0-1.fc40.x86_64",
                "test-package-1.0-2.fc40.x86_64",
                "abc123",
                Optional.of("deadbeef"),
                Optional.of("cafebabe"),
                1024,
                2048,
                Delta.DeltaType.STANDARD,
                Delta.CompressionMethod.XZ
        );

        assertThat(drpm.version()).isEqualTo("0003");
        assertThat(drpm.sourceNevra()).isEqualTo("test-package-1.0-1.fc40.x86_64");
        assertThat(drpm.targetNevra()).isEqualTo("test-package-1.0-2.fc40.x86_64");
        assertThat(drpm.sequence()).isEqualTo("abc123");
        assertThat(drpm.sourceRpmDigest()).hasValue("deadbeef");
        assertThat(drpm.targetRpmDigest()).hasValue("cafebabe");
        assertThat(drpm.deltaSize()).isEqualTo(1024);
        assertThat(drpm.targetSize()).isEqualTo(2048);
        assertThat(drpm.deltaType()).isEqualTo(Delta.DeltaType.STANDARD);
        assertThat(drpm.compressionMethod()).isEqualTo(Delta.CompressionMethod.XZ);
    }

    @Test
    void extractsPackageNameFromNevra() {
        Delta drpm = new Delta(
                "0003",
                "kernel-core-6.5.0-1.fc40.x86_64",
                "kernel-core-6.5.0-2.fc40.x86_64",
                "seq",
                Optional.empty(),
                Optional.empty(),
                100,
                200,
                Delta.DeltaType.STANDARD,
                Delta.CompressionMethod.GZIP
        );

        assertThat(drpm.sourceName()).isEqualTo("kernel-core");
        assertThat(drpm.targetName()).isEqualTo("kernel-core");
        assertThat(drpm.isValid()).isTrue();
    }

    @Test
    void detectsInvalidDeltaWithDifferentPackageNames() {
        Delta drpm = new Delta(
                "0003",
                "package-a-1.0-1.fc40.x86_64",
                "package-b-1.0-1.fc40.x86_64",
                "seq",
                Optional.empty(),
                Optional.empty(),
                100,
                200,
                Delta.DeltaType.STANDARD,
                Delta.CompressionMethod.GZIP
        );

        assertThat(drpm.sourceName()).isEqualTo("package-a");
        assertThat(drpm.targetName()).isEqualTo("package-b");
        assertThat(drpm.isValid()).isFalse();
    }

    @Test
    void isDeltaRpmReturnsFalseForRegularFile() throws IOException {
        Path regularFile = tempDir.resolve("regular.txt");
        Files.writeString(regularFile, "This is not a delta RPM");

        assertThat(DeltaReader.isDelta(regularFile)).isFalse();
    }

    @Test
    void isDeltaRpmReturnsFalseForEmptyFile() throws IOException {
        Path emptyFile = tempDir.resolve("empty.drpm");
        Files.write(emptyFile, new byte[0]);

        assertThat(DeltaReader.isDelta(emptyFile)).isFalse();
    }

    @Test
    void isDeltaRpmReturnsFalseForShortFile() throws IOException {
        Path shortFile = tempDir.resolve("short.drpm");
        Files.write(shortFile, new byte[]{0x64, 0x72}); // Only "dr"

        assertThat(DeltaReader.isDelta(shortFile)).isFalse();
    }

    @Test
    void isDeltaRpmReturnsTrueForDrpmMagic() throws IOException {
        Path drpmFile = tempDir.resolve("test.drpm");
        Files.write(drpmFile, new byte[]{'d', 'r', 'p', 'm', '0', '0', '0', '3'});

        assertThat(DeltaReader.isDelta(drpmFile)).isTrue();
    }

    @Test
    void isDeltaRpmReturnsTrueForDlt3Magic() throws IOException {
        Path dlt3File = tempDir.resolve("test.drpm");
        Files.write(dlt3File, new byte[]{'D', 'L', 'T', '3', '0', '0', '0', '3'});

        assertThat(DeltaReader.isDelta(dlt3File)).isTrue();
    }

    @Test
    void isDeltaRpmFromStreamWorksWithMarkSupport() throws IOException {
        ByteArrayInputStream stream = new ByteArrayInputStream(new byte[]{'d', 'r', 'p', 'm'});
        // ByteArrayInputStream supports mark, so this should work and return true
        assertThat(DeltaReader.isDelta(stream)).isTrue();
    }

    @Test
    void isDeltaRpmFromStreamReturnsFalseForNonDelta() throws IOException {
        ByteArrayInputStream stream = new ByteArrayInputStream(new byte[]{'n', 'o', 't', '!'});
        assertThat(DeltaReader.isDelta(stream)).isFalse();
    }

    @Test
    void rejectsInvalidDeltaRpmMagic() throws IOException {
        Path invalidFile = tempDir.resolve("invalid.drpm");
        Files.write(invalidFile, new byte[]{'b', 'a', 'd', '!', '0', '0', '0', '3'});

        assertThatThrownBy(() -> DeltaReader.read(invalidFile))
                .isInstanceOf(InvalidFormatException.class)
                .hasMessageContaining("Invalid delta RPM magic");
    }

    @Test
    void readsDeltaRpmWithDrpmMagic() throws IOException, InvalidFormatException {
        // Create a minimal valid delta RPM structure
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Magic: drpm
        baos.write(new byte[]{'d', 'r', 'p', 'm'});

        // Version: 0003
        baos.write("0003".getBytes(StandardCharsets.US_ASCII));

        // Source NEVRA (null-terminated)
        baos.write("test-1.0-1.fc40.x86_64".getBytes(StandardCharsets.US_ASCII));
        baos.write(0);

        // Sequence (null-terminated)
        baos.write("abc123".getBytes(StandardCharsets.US_ASCII));
        baos.write(0);

        // Target NEVRA (null-terminated)
        baos.write("test-1.0-2.fc40.x86_64".getBytes(StandardCharsets.US_ASCII));
        baos.write(0);

        // Target size (4 bytes, big-endian): 4096
        baos.write(new byte[]{0x00, 0x00, 0x10, 0x00});

        // Some payload data
        baos.write(new byte[100]);

        byte[] data = baos.toByteArray();
        Path drpmFile = tempDir.resolve("test.drpm");
        Files.write(drpmFile, data);

        Delta drpm = DeltaReader.read(drpmFile);

        assertThat(drpm.version()).isEqualTo("0003");
        assertThat(drpm.sourceNevra()).isEqualTo("test-1.0-1.fc40.x86_64");
        assertThat(drpm.targetNevra()).isEqualTo("test-1.0-2.fc40.x86_64");
        assertThat(drpm.sequence()).isEqualTo("abc123");
        assertThat(drpm.targetSize()).isEqualTo(4096);
        assertThat(drpm.deltaType()).isEqualTo(Delta.DeltaType.STANDARD);
        assertThat(drpm.sourceName()).isEqualTo("test");
        assertThat(drpm.targetName()).isEqualTo("test");
        assertThat(drpm.isValid()).isTrue();
    }

    @Test
    void detectsGzipCompression() throws IOException, InvalidFormatException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Magic: drpm
        baos.write(new byte[]{'d', 'r', 'p', 'm'});
        baos.write("0003".getBytes(StandardCharsets.US_ASCII));
        baos.write("src-1.0-1.x86_64".getBytes(StandardCharsets.US_ASCII));
        baos.write(0);
        baos.write("seq".getBytes(StandardCharsets.US_ASCII));
        baos.write(0);
        baos.write("tgt-1.0-2.x86_64".getBytes(StandardCharsets.US_ASCII));
        baos.write(0);
        baos.write(new byte[]{0x00, 0x00, 0x10, 0x00}); // target size

        // gzip magic
        baos.write(new byte[]{(byte) 0x1F, (byte) 0x8B, 0x08, 0x00});
        baos.write(new byte[50]);

        Path drpmFile = tempDir.resolve("gzip.drpm");
        Files.write(drpmFile, baos.toByteArray());

        Delta drpm = DeltaReader.read(drpmFile);
        assertThat(drpm.compressionMethod()).isEqualTo(Delta.CompressionMethod.GZIP);
    }

    @Test
    void detectsXzCompression() throws IOException, InvalidFormatException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Magic: drpm
        baos.write(new byte[]{'d', 'r', 'p', 'm'});
        baos.write("0003".getBytes(StandardCharsets.US_ASCII));
        baos.write("src-1.0-1.x86_64".getBytes(StandardCharsets.US_ASCII));
        baos.write(0);
        baos.write("seq".getBytes(StandardCharsets.US_ASCII));
        baos.write(0);
        baos.write("tgt-1.0-2.x86_64".getBytes(StandardCharsets.US_ASCII));
        baos.write(0);
        baos.write(new byte[]{0x00, 0x00, 0x10, 0x00}); // target size

        // xz magic: FD 37 7A 58 5A 00
        baos.write(new byte[]{(byte) 0xFD, (byte) 0x37, (byte) 0x7A, (byte) 0x58});
        baos.write(new byte[50]);

        Path drpmFile = tempDir.resolve("xz.drpm");
        Files.write(drpmFile, baos.toByteArray());

        Delta drpm = DeltaReader.read(drpmFile);
        assertThat(drpm.compressionMethod()).isEqualTo(Delta.CompressionMethod.XZ);
    }

    @Test
    void deltaTypeEnumValues() {
        assertThat(Delta.DeltaType.STANDARD).isNotNull();
        assertThat(Delta.DeltaType.RPM_ONLY).isNotNull();
        assertThat(Delta.DeltaType.UNKNOWN).isNotNull();
    }

    @Test
    void compressionMethodEnumValues() {
        assertThat(Delta.CompressionMethod.NONE).isNotNull();
        assertThat(Delta.CompressionMethod.GZIP).isNotNull();
        assertThat(Delta.CompressionMethod.BZIP2).isNotNull();
        assertThat(Delta.CompressionMethod.XZ).isNotNull();
        assertThat(Delta.CompressionMethod.ZSTD).isNotNull();
        assertThat(Delta.CompressionMethod.UNKNOWN).isNotNull();
    }
}
