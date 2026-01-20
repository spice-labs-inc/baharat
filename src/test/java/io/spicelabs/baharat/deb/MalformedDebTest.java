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
package io.spicelabs.baharat.deb;

import io.spicelabs.baharat.PackageException;
import io.spicelabs.baharat.PackageFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for malformed and invalid DEB files.
 * These tests verify that the library handles edge cases securely.
 */
class MalformedDebTest {

    @TempDir
    Path tempDir;

    private static final byte[] AR_MAGIC = "!<arch>\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] DEB_BINARY = "2.0\n".getBytes(StandardCharsets.US_ASCII);

    @Test
    void rejectEmptyFile() throws IOException {
        Path emptyFile = tempDir.resolve("empty.deb");
        Files.write(emptyFile, new byte[0]);

        assertThatThrownBy(() -> DebReader.read(emptyFile))
                .isInstanceOf(PackageException.InvalidPackageException.class);
    }

    @Test
    void rejectTooSmallFile() throws IOException {
        Path tinyFile = tempDir.resolve("tiny.deb");
        Files.write(tinyFile, new byte[]{0x21, 0x3C}); // Only "!<"

        assertThatThrownBy(() -> DebReader.read(tinyFile))
                .isInstanceOf(PackageException.InvalidPackageException.class);
    }

    @Test
    void rejectInvalidMagic() throws IOException {
        Path invalidMagic = tempDir.resolve("invalid-magic.deb");
        Files.write(invalidMagic, new byte[]{0x50, 0x4B, 0x03, 0x04}); // ZIP magic

        assertThatThrownBy(() -> DebReader.read(invalidMagic))
                .isInstanceOf(PackageException.InvalidPackageException.class);
    }

    @Test
    void rejectMissingDebianBinary() throws IOException {
        Path missingDebianBinary = tempDir.resolve("missing-debian-binary.deb");
        byte[] archive = createArArchiveWithEntries(
                new TestEntry("other-file", new byte[]{'t', 'e', 's', 't'})
        );
        Files.write(missingDebianBinary, archive);

        // Without debian-binary, the package should be rejected
        assertThatThrownBy(() -> DebReader.read(missingDebianBinary))
                .isInstanceOf(Exception.class); // May throw InvalidPackageException or other IO error
    }

    @Test
    void rejectMissingControlTar() throws IOException {
        Path missingControl = tempDir.resolve("missing-control.deb");
        byte[] archive = createArArchiveWithEntries(
                new TestEntry("debian-binary", DEB_BINARY),
                new TestEntry("data.tar.gz", new byte[]{0x1F, (byte) 0x8B, 0x08, 0x00})
        );
        Files.write(missingControl, archive);

        // Should fail when trying to read - may be InvalidPackageException or other error
        assertThatThrownBy(() -> DebReader.read(missingControl))
                .isInstanceOf(Exception.class);
    }

    @Test
    void rejectTruncatedEntryHeader() throws IOException {
        Path truncated = tempDir.resolve("truncated-entry.deb");
        byte[] data = new byte[AR_MAGIC.length + 30]; // Less than 60-byte header
        System.arraycopy(AR_MAGIC, 0, data, 0, AR_MAGIC.length);
        Files.write(truncated, data);

        assertThatThrownBy(() -> DebReader.read(truncated))
                .isInstanceOf(PackageException.InvalidPackageException.class)
                .hasMessageContaining("Truncated");
    }

    @Test
    void rejectInvalidEntryMagic() throws IOException {
        Path invalidEntryMagic = tempDir.resolve("invalid-entry-magic.deb");
        byte[] archive = new byte[AR_MAGIC.length + 60];
        System.arraycopy(AR_MAGIC, 0, archive, 0, AR_MAGIC.length);
        // Fill header but with wrong entry magic bytes
        java.util.Arrays.fill(archive, AR_MAGIC.length, archive.length, (byte) ' ');
        archive[AR_MAGIC.length + 58] = 'X';
        archive[AR_MAGIC.length + 59] = 'X';
        Files.write(invalidEntryMagic, archive);

        assertThatThrownBy(() -> DebReader.read(invalidEntryMagic))
                .isInstanceOf(PackageException.InvalidPackageException.class)
                .hasMessageContaining("entry magic");
    }

    @Test
    void isDebDetectsValidMagic() throws IOException {
        Path validMagic = tempDir.resolve("has-magic.deb");
        Files.write(validMagic, AR_MAGIC);

        assertThat(PackageFormat.detect(validMagic)).contains(PackageFormat.DEB);
    }

    @Test
    void isDebRejectsBadMagic() throws IOException {
        // Use a non-.deb extension so that extension fallback doesn't apply
        Path badMagic = tempDir.resolve("bad-magic.bin");
        Files.write(badMagic, new byte[]{0x50, 0x4B, 0x03, 0x04}); // ZIP magic

        // ZIP magic without .deb extension should not be detected as DEB
        var detected = PackageFormat.detect(badMagic);
        assertThat(detected.isEmpty() || detected.get() != PackageFormat.DEB).isTrue();
    }

    @Test
    void isDebHandlesEmptyFile() throws IOException {
        Path emptyFile = tempDir.resolve("empty.deb");
        Files.write(emptyFile, new byte[0]);

        assertThat(PackageFormat.detect(emptyFile)).isEmpty();
    }

    @Test
    void streamPayloadRejectsMissingDataTar() throws IOException {
        Path noDataTar = tempDir.resolve("no-data-tar.deb");
        byte[] archive = createArArchiveWithEntries(
                new TestEntry("debian-binary", DEB_BINARY)
        );
        Files.write(noDataTar, archive);

        assertThatThrownBy(() -> DebReader.streamPayload(noDataTar))
                .isInstanceOf(PackageException.InvalidPackageException.class)
                .hasMessageContaining("data.tar");
    }

    // Helper methods

    private record TestEntry(String name, byte[] content) {}

    private byte[] createArArchiveWithEntries(TestEntry... entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(AR_MAGIC);

        for (TestEntry entry : entries) {
            writeArEntry(out, entry.name, entry.content);
        }

        return out.toByteArray();
    }

    private void writeArEntry(ByteArrayOutputStream out, String name, byte[] content) throws IOException {
        byte[] header = new byte[60];
        java.util.Arrays.fill(header, (byte) ' ');

        // Name (16 bytes)
        byte[] nameBytes = name.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(nameBytes, 0, header, 0, Math.min(nameBytes.length, 16));

        // Timestamp (12 bytes) - use 0
        header[16] = '0';

        // Owner ID (6 bytes) - use 0
        header[28] = '0';

        // Group ID (6 bytes) - use 0
        header[34] = '0';

        // Mode (8 bytes, octal) - use 100644
        String mode = "100644";
        System.arraycopy(mode.getBytes(StandardCharsets.US_ASCII), 0, header, 40, mode.length());

        // Size (10 bytes)
        String size = String.valueOf(content.length);
        System.arraycopy(size.getBytes(StandardCharsets.US_ASCII), 0, header, 48, size.length());

        // Entry magic (2 bytes)
        header[58] = '`';
        header[59] = '\n';

        out.write(header);
        out.write(content);

        // Padding for odd-sized content
        if (content.length % 2 != 0) {
            out.write('\n');
        }
    }
}
