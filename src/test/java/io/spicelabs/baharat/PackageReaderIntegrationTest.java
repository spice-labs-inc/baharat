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

import io.spicelabs.baharat.rpm.RpmPackage;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for the PackageReader unified API.
 */
class PackageReaderIntegrationTest {

    @Test
    void detectRpmFormat() throws Exception {
        // Create a mock RPM file with magic bytes
        Path tempFile = Files.createTempFile("test", ".rpm");
        try {
            Files.write(tempFile, new byte[]{(byte) 0xED, (byte) 0xAB, (byte) 0xEE, (byte) 0xDB, 0, 0, 0, 0});

            Optional<PackageFormat> format = PackageReader.detect(tempFile);
            assertThat(format).isPresent();
            assertThat(format.get()).isEqualTo(PackageFormat.RPM);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void detectDebFormat() throws Exception {
        // Create a mock DEB file with ar archive magic
        Path tempFile = Files.createTempFile("test", ".deb");
        try {
            Files.write(tempFile, "!<arch>\n".getBytes());

            Optional<PackageFormat> format = PackageReader.detect(tempFile);
            assertThat(format).isPresent();
            assertThat(format.get()).isEqualTo(PackageFormat.DEB);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void detectFormatByExtension() throws Exception {
        Path tempFile = Files.createTempFile("test.pkg.tar", ".zst");
        try {
            // Just need enough bytes for detection
            Files.write(tempFile, new byte[]{0x28, (byte) 0xB5, 0x2F, (byte) 0xFD, 0, 0, 0, 0});

            Optional<PackageFormat> format = PackageReader.detect(tempFile);
            assertThat(format).isPresent();
            // Should detect as Pacman due to .pkg.tar extension
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void isPackageReturnsFalseForUnknownFormat() throws Exception {
        Path tempFile = Files.createTempFile("test", ".txt");
        try {
            Files.writeString(tempFile, "This is not a package file");

            assertThat(PackageReader.isPackage(tempFile)).isFalse();
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void packageFormatMagicBytes() {
        assertThat(PackageFormat.RPM.magic()).isPresent();
        assertThat(PackageFormat.RPM.magic().get()).hasSize(4);

        assertThat(PackageFormat.DEB.magic()).isPresent();
        assertThat(PackageFormat.DEB.magic().get()).hasSize(8);

        assertThat(PackageFormat.PACMAN.magic()).isEmpty(); // Structure-detected
    }

    @Test
    void packageFormatFamily() {
        assertThat(PackageFormat.RPM.family()).isEqualTo(PackageFormat.Family.LINUX);
        assertThat(PackageFormat.DEB.family()).isEqualTo(PackageFormat.Family.LINUX);
        assertThat(PackageFormat.PACMAN.family()).isEqualTo(PackageFormat.Family.LINUX);
        assertThat(PackageFormat.APK.family()).isEqualTo(PackageFormat.Family.LINUX);
        assertThat(PackageFormat.FREEBSD_PKG.family()).isEqualTo(PackageFormat.Family.BSD);
        assertThat(PackageFormat.OPENBSD_PKG.family()).isEqualTo(PackageFormat.Family.BSD);
    }

    @Test
    void packageFormatExtension() {
        assertThat(PackageFormat.RPM.extension()).isEqualTo(".rpm");
        assertThat(PackageFormat.DEB.extension()).isEqualTo(".deb");
        assertThat(PackageFormat.PACMAN.extension()).isEqualTo(".pkg.tar");
        assertThat(PackageFormat.APK.extension()).isEqualTo(".apk");
        assertThat(PackageFormat.FREEBSD_PKG.extension()).isEqualTo(".pkg");
        assertThat(PackageFormat.OPENBSD_PKG.extension()).isEqualTo(".tgz");
    }

    @Test
    void readUnknownFormatThrowsException() throws Exception {
        Path tempFile = Files.createTempFile("test", ".unknown");
        try {
            Files.writeString(tempFile, "Unknown content");

            assertThatThrownBy(() -> PackageReader.read(tempFile))
                    .isInstanceOf(PackageException.UnsupportedPackageException.class);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
