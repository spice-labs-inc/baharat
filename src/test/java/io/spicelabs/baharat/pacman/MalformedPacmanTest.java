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
package io.spicelabs.baharat.pacman;

import io.spicelabs.baharat.PackageFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for malformed and invalid Pacman packages.
 */
class MalformedPacmanTest {

    @TempDir
    Path tempDir;

    @Test
    void parseEmptyPkgInfo() {
        var fields = PkgInfoParser.parse("");

        assertThat(fields).isEmpty();
    }

    @Test
    void parsePkgInfoWithOnlyComments() {
        var fields = PkgInfoParser.parse("""
                # This is a comment
                # Another comment
                """);

        assertThat(fields).isEmpty();
    }

    @Test
    void parsePkgInfoWithInvalidLines() {
        var fields = PkgInfoParser.parse("""
                pkgname = test
                invalid line no equals
                pkgver = 1.0
                another bad line
                """);

        // Invalid lines are skipped
        assertThat(fields.get("pkgname")).isEqualTo("test");
        assertThat(fields.get("pkgver")).isEqualTo("1.0");
        assertThat(fields).hasSize(2);
    }

    @Test
    void detectPacmanFromExtension() throws Exception {
        Path pkgFile = tempDir.resolve("test-1.0-1-x86_64.pkg.tar.xz");
        // XZ magic bytes
        byte[] xzMagic = {(byte) 0xFD, '7', 'z', 'X', 'Z', 0x00};
        Files.write(pkgFile, xzMagic);

        var format = PackageFormat.detect(pkgFile);

        assertThat(format).contains(PackageFormat.PACMAN);
    }

    @Test
    void detectPacmanFromZstdExtension() throws Exception {
        Path pkgFile = tempDir.resolve("test-1.0-1-x86_64.pkg.tar.zst");
        // Zstd magic bytes
        byte[] zstdMagic = {0x28, (byte) 0xB5, 0x2F, (byte) 0xFD};
        Files.write(pkgFile, zstdMagic);

        var format = PackageFormat.detect(pkgFile);

        assertThat(format).contains(PackageFormat.PACMAN);
    }

    @Test
    void detectPacmanFromGzipExtension() throws Exception {
        Path pkgFile = tempDir.resolve("test-1.0-1-x86_64.pkg.tar.gz");
        // Gzip magic bytes (need at least 4 bytes for detection)
        byte[] gzipMagic = {0x1F, (byte) 0x8B, 0x08, 0x00};
        Files.write(pkgFile, gzipMagic);

        var format = PackageFormat.detect(pkgFile);

        assertThat(format).contains(PackageFormat.PACMAN);
    }

    @Test
    void pacmanFormatProperties() {
        assertThat(PackageFormat.PACMAN.extension()).isEqualTo(".pkg.tar");
        assertThat(PackageFormat.PACMAN.family()).isEqualTo(PackageFormat.Family.LINUX);
        // Pacman doesn't have unique magic bytes
        assertThat(PackageFormat.PACMAN.magic()).isEmpty();
    }

    @Test
    void metadataWithMissingName() {
        var fields = PkgInfoParser.parse("""
                pkgver = 1.0
                arch = x86_64
                """);

        PacmanMetadata metadata = new PacmanMetadata(fields);

        assertThat(metadata.name()).isEmpty();
        assertThat(metadata.version()).isEqualTo("1.0");
    }

    @Test
    void metadataWithMissingVersion() {
        var fields = PkgInfoParser.parse("""
                pkgname = test
                arch = x86_64
                """);

        PacmanMetadata metadata = new PacmanMetadata(fields);

        assertThat(metadata.name()).isEqualTo("test");
        assertThat(metadata.version()).isEmpty();
    }

    @Test
    void metadataWithMissingArch() {
        var fields = PkgInfoParser.parse("""
                pkgname = test
                pkgver = 1.0
                """);

        PacmanMetadata metadata = new PacmanMetadata(fields);

        assertThat(metadata.arch()).isEmpty();
    }

    @Test
    void dependencyWithMalformedVersion() {
        var fields = PkgInfoParser.parse("""
                pkgname = test
                pkgver = 1.0
                depend = >=malformed
                """);

        PacmanMetadata metadata = new PacmanMetadata(fields);

        // Should handle gracefully
        assertThat(metadata.dependencies()).hasSize(1);
    }

    @Test
    void parsePkgInfoLargeFile() {
        StringBuilder sb = new StringBuilder();
        sb.append("pkgname = test\n");
        sb.append("pkgver = 1.0\n");
        for (int i = 0; i < 1000; i++) {
            sb.append("depend = dep").append(i).append("\n");
        }

        var fields = PkgInfoParser.parse(sb.toString());

        assertThat(fields.get("pkgname")).isEqualTo("test");
        @SuppressWarnings("unchecked")
        var depends = (java.util.List<String>) fields.get("depend");
        assertThat(depends).hasSize(1000);
    }
}
