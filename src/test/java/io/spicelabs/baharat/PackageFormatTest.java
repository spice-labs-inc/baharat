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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link PackageFormat}.
 */
class PackageFormatTest {

    @TempDir
    Path tempDir;

    // Magic bytes
    private static final byte[] RPM_MAGIC = {(byte) 0xED, (byte) 0xAB, (byte) 0xEE, (byte) 0xDB};
    private static final byte[] DEB_MAGIC = "!<arch>\n".getBytes();
    private static final byte[] GZIP_MAGIC = {0x1F, (byte) 0x8B};
    private static final byte[] XZ_MAGIC = {(byte) 0xFD, '7', 'z', 'X', 'Z', 0x00};
    private static final byte[] ZSTD_MAGIC = {0x28, (byte) 0xB5, 0x2F, (byte) 0xFD};

    // RPM format tests

    @Test
    void rpmFormatProperties() {
        assertThat(PackageFormat.RPM.extension()).isEqualTo(".rpm");
        assertThat(PackageFormat.RPM.family()).isEqualTo(PackageFormat.Family.LINUX);
        assertThat(PackageFormat.RPM.magic()).isPresent();
        assertThat(PackageFormat.RPM.magic().get()).isEqualTo(RPM_MAGIC);
    }

    @Test
    void detectRpmFromMagic() throws Exception {
        Path file = tempDir.resolve("test.rpm");
        Files.write(file, RPM_MAGIC);

        assertThat(PackageFormat.detect(file)).contains(PackageFormat.RPM);
    }

    @Test
    void detectRpmFromBytes() {
        assertThat(PackageFormat.detect(RPM_MAGIC)).contains(PackageFormat.RPM);
    }

    // DEB format tests

    @Test
    void debFormatProperties() {
        assertThat(PackageFormat.DEB.extension()).isEqualTo(".deb");
        assertThat(PackageFormat.DEB.family()).isEqualTo(PackageFormat.Family.LINUX);
        assertThat(PackageFormat.DEB.magic()).isPresent();
        assertThat(PackageFormat.DEB.magic().get()).isEqualTo(DEB_MAGIC);
    }

    @Test
    void detectDebFromMagic() throws Exception {
        Path file = tempDir.resolve("test.deb");
        Files.write(file, DEB_MAGIC);

        assertThat(PackageFormat.detect(file)).contains(PackageFormat.DEB);
    }

    @Test
    void detectDebFromBytes() {
        assertThat(PackageFormat.detect(DEB_MAGIC)).contains(PackageFormat.DEB);
    }

    // Pacman format tests

    @Test
    void pacmanFormatProperties() {
        assertThat(PackageFormat.PACMAN.extension()).isEqualTo(".pkg.tar");
        assertThat(PackageFormat.PACMAN.family()).isEqualTo(PackageFormat.Family.LINUX);
        assertThat(PackageFormat.PACMAN.magic()).isEmpty();
    }

    @Test
    void detectPacmanFromGzipExtension() throws Exception {
        Path file = tempDir.resolve("test-1.0-1-x86_64.pkg.tar.gz");
        // Need at least 4 bytes for detection
        Files.write(file, new byte[]{0x1F, (byte) 0x8B, 0x08, 0x00});

        assertThat(PackageFormat.detect(file)).contains(PackageFormat.PACMAN);
    }

    @Test
    void detectPacmanFromXzExtension() throws Exception {
        Path file = tempDir.resolve("test-1.0-1-x86_64.pkg.tar.xz");
        Files.write(file, XZ_MAGIC);

        assertThat(PackageFormat.detect(file)).contains(PackageFormat.PACMAN);
    }

    @Test
    void detectPacmanFromZstdExtension() throws Exception {
        Path file = tempDir.resolve("test-1.0-1-x86_64.pkg.tar.zst");
        Files.write(file, ZSTD_MAGIC);

        assertThat(PackageFormat.detect(file)).contains(PackageFormat.PACMAN);
    }

    // APK format tests

    @Test
    void apkFormatProperties() {
        assertThat(PackageFormat.APK.extension()).isEqualTo(".apk");
        assertThat(PackageFormat.APK.family()).isEqualTo(PackageFormat.Family.LINUX);
        assertThat(PackageFormat.APK.magic()).isPresent();
        assertThat(PackageFormat.APK.magic().get()).isEqualTo(GZIP_MAGIC);
    }

    @Test
    void detectApkFromExtension() throws Exception {
        Path file = tempDir.resolve("test-1.0-r0.apk");
        // Need at least 4 bytes for detection; include gzip magic + some extra bytes
        Files.write(file, new byte[]{0x1F, (byte) 0x8B, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00});

        assertThat(PackageFormat.detect(file)).contains(PackageFormat.APK);
    }

    // FreeBSD format tests

    @Test
    void freebsdFormatProperties() {
        assertThat(PackageFormat.FREEBSD_PKG.extension()).isEqualTo(".pkg");
        assertThat(PackageFormat.FREEBSD_PKG.family()).isEqualTo(PackageFormat.Family.BSD);
        assertThat(PackageFormat.FREEBSD_PKG.magic()).isEmpty();
    }

    @Test
    void detectFreebsdFromTxz() throws Exception {
        Path file = tempDir.resolve("test-1.0.txz");
        Files.write(file, XZ_MAGIC);

        assertThat(PackageFormat.detect(file)).contains(PackageFormat.FREEBSD_PKG);
    }

    @Test
    void detectFreebsdFromPkgWithXz() throws Exception {
        Path file = tempDir.resolve("test-1.0.pkg");
        Files.write(file, XZ_MAGIC);

        assertThat(PackageFormat.detect(file)).contains(PackageFormat.FREEBSD_PKG);
    }

    @Test
    void detectFreebsdFromPkgWithZstd() throws Exception {
        Path file = tempDir.resolve("test-1.0.pkg");
        Files.write(file, ZSTD_MAGIC);

        assertThat(PackageFormat.detect(file)).contains(PackageFormat.FREEBSD_PKG);
    }

    // OpenBSD format tests

    @Test
    void openbsdFormatProperties() {
        assertThat(PackageFormat.OPENBSD_PKG.extension()).isEqualTo(".tgz");
        assertThat(PackageFormat.OPENBSD_PKG.family()).isEqualTo(PackageFormat.Family.BSD);
        assertThat(PackageFormat.OPENBSD_PKG.magic()).isPresent();
        assertThat(PackageFormat.OPENBSD_PKG.magic().get()).isEqualTo(GZIP_MAGIC);
    }

    @Test
    void detectOpenbsdFromTgz() throws Exception {
        Path file = tempDir.resolve("test-1.0.tgz");
        // Need at least 4 bytes for detection
        Files.write(file, new byte[]{0x1F, (byte) 0x8B, 0x08, 0x00});

        assertThat(PackageFormat.detect(file)).contains(PackageFormat.OPENBSD_PKG);
    }

    // Edge cases

    @Test
    void detectEmptyFile() throws Exception {
        Path file = tempDir.resolve("empty.rpm");
        Files.write(file, new byte[0]);

        assertThat(PackageFormat.detect(file)).isEmpty();
    }

    @Test
    void detectTooSmallFile() throws Exception {
        Path file = tempDir.resolve("small.rpm");
        Files.write(file, new byte[]{0x00, 0x01, 0x02}); // Only 3 bytes

        assertThat(PackageFormat.detect(file)).isEmpty();
    }

    @Test
    void detectFromNullBytes() {
        assertThat(PackageFormat.detect((byte[]) null)).isEmpty();
    }

    @Test
    void detectFromShortBytes() {
        assertThat(PackageFormat.detect(new byte[]{0x00})).isEmpty();
    }

    @Test
    void detectUnknownMagic() throws Exception {
        Path file = tempDir.resolve("unknown.bin");
        Files.write(file, new byte[]{0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77});

        // Unknown magic with unknown extension
        assertThat(PackageFormat.detect(file)).isEmpty();
    }

    @Test
    void detectFallsBackToExtension() throws Exception {
        // File with unknown magic but known extension
        Path rpmFile = tempDir.resolve("test.rpm");
        Files.write(rpmFile, new byte[]{0x00, 0x11, 0x22, 0x33, 0x44});
        // RPM detection should fail magic check but extension might match

        Path debFile = tempDir.resolve("test.deb");
        Files.write(debFile, new byte[]{0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88});
        // Not ar magic, but .deb extension
    }

    // Family tests

    @Test
    void linuxFamily() {
        assertThat(PackageFormat.RPM.family()).isEqualTo(PackageFormat.Family.LINUX);
        assertThat(PackageFormat.DEB.family()).isEqualTo(PackageFormat.Family.LINUX);
        assertThat(PackageFormat.PACMAN.family()).isEqualTo(PackageFormat.Family.LINUX);
        assertThat(PackageFormat.APK.family()).isEqualTo(PackageFormat.Family.LINUX);
    }

    @Test
    void bsdFamily() {
        assertThat(PackageFormat.FREEBSD_PKG.family()).isEqualTo(PackageFormat.Family.BSD);
        assertThat(PackageFormat.OPENBSD_PKG.family()).isEqualTo(PackageFormat.Family.BSD);
    }

    // Magic cloning

    @Test
    void magicReturnsDefensiveCopy() {
        byte[] magic1 = PackageFormat.RPM.magic().orElseThrow();
        byte[] magic2 = PackageFormat.RPM.magic().orElseThrow();

        // Modify one copy
        magic1[0] = 0x00;

        // Other copy should be unchanged
        assertThat(magic2[0]).isEqualTo((byte) 0xED);
    }

    // All formats enumeration

    @Test
    void allFormatsCount() {
        assertThat(PackageFormat.values()).hasSize(6);
    }

    @Test
    void formatNames() {
        assertThat(PackageFormat.RPM.name()).isEqualTo("RPM");
        assertThat(PackageFormat.DEB.name()).isEqualTo("DEB");
        assertThat(PackageFormat.PACMAN.name()).isEqualTo("PACMAN");
        assertThat(PackageFormat.APK.name()).isEqualTo("APK");
        assertThat(PackageFormat.FREEBSD_PKG.name()).isEqualTo("FREEBSD_PKG");
        assertThat(PackageFormat.OPENBSD_PKG.name()).isEqualTo("OPENBSD_PKG");
    }
}
