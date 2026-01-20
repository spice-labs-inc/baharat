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
package io.spicelabs.baharat.freebsd;

import io.spicelabs.baharat.PackageException;
import io.spicelabs.baharat.PackageFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for malformed and invalid FreeBSD packages.
 */
class MalformedFreeBsdTest {

    @TempDir
    Path tempDir;

    @Test
    void parseInvalidJsonManifest() {
        assertThatThrownBy(() -> ManifestParser.parse("{ invalid }"))
                .isInstanceOf(PackageException.InvalidPackageException.class);
    }

    @Test
    void parseEmptyManifest() {
        assertThatThrownBy(() -> ManifestParser.parse(""))
                .isInstanceOf(PackageException.InvalidPackageException.class);
    }

    @Test
    void parseJsonArray() {
        assertThatThrownBy(() -> ManifestParser.parse("[1,2,3]"))
                .isInstanceOf(PackageException.InvalidPackageException.class);
    }

    @Test
    void detectFreeBsdFromTxzExtension() throws Exception {
        Path pkgFile = tempDir.resolve("test-1.0.txz");
        // XZ magic bytes
        byte[] xzMagic = {(byte) 0xFD, '7', 'z', 'X', 'Z', 0x00};
        Files.write(pkgFile, xzMagic);

        var format = PackageFormat.detect(pkgFile);

        assertThat(format).contains(PackageFormat.FREEBSD_PKG);
    }

    @Test
    void detectFreeBsdFromPkgExtension() throws Exception {
        Path pkgFile = tempDir.resolve("test-1.0.pkg");
        // XZ magic bytes
        byte[] xzMagic = {(byte) 0xFD, '7', 'z', 'X', 'Z', 0x00};
        Files.write(pkgFile, xzMagic);

        var format = PackageFormat.detect(pkgFile);

        assertThat(format).contains(PackageFormat.FREEBSD_PKG);
    }

    @Test
    void detectFreeBsdFromZstdPkg() throws Exception {
        Path pkgFile = tempDir.resolve("test-1.0.pkg");
        // Zstd magic bytes
        byte[] zstdMagic = {0x28, (byte) 0xB5, 0x2F, (byte) 0xFD};
        Files.write(pkgFile, zstdMagic);

        var format = PackageFormat.detect(pkgFile);

        assertThat(format).contains(PackageFormat.FREEBSD_PKG);
    }

    @Test
    void freebsdFormatProperties() {
        assertThat(PackageFormat.FREEBSD_PKG.extension()).isEqualTo(".pkg");
        assertThat(PackageFormat.FREEBSD_PKG.family()).isEqualTo(PackageFormat.Family.BSD);
        // FreeBSD doesn't have unique magic bytes (uses xz/zstd)
        assertThat(PackageFormat.FREEBSD_PKG.magic()).isEmpty();
    }

    @Test
    void metadataWithMissingName() throws Exception {
        var json = ManifestParser.parse("""
                {
                  "version": "1.0"
                }
                """);

        FreeBsdMetadata metadata = new FreeBsdMetadata(json);

        assertThat(metadata.name()).isEmpty();
    }

    @Test
    void metadataWithNullValues() throws Exception {
        var json = ManifestParser.parse("""
                {
                  "name": "test",
                  "version": null,
                  "flatsize": null
                }
                """);

        FreeBsdMetadata metadata = new FreeBsdMetadata(json);

        assertThat(metadata.name()).isEqualTo("test");
        assertThat(metadata.version()).isEmpty();
        assertThat(metadata.installedSize()).isEqualTo(0L);
    }

    @Test
    void metadataWithEmptyDeps() throws Exception {
        var json = ManifestParser.parse("""
                {
                  "name": "test",
                  "deps": {}
                }
                """);

        FreeBsdMetadata metadata = new FreeBsdMetadata(json);

        assertThat(metadata.dependencies()).isEmpty();
    }

    @Test
    void metadataWithEmptyProvides() throws Exception {
        var json = ManifestParser.parse("""
                {
                  "name": "test",
                  "provides": []
                }
                """);

        FreeBsdMetadata metadata = new FreeBsdMetadata(json);

        assertThat(metadata.provides()).isEmpty();
    }

    @Test
    void metadataWithEmptyConflicts() throws Exception {
        var json = ManifestParser.parse("""
                {
                  "name": "test",
                  "conflicts": {}
                }
                """);

        FreeBsdMetadata metadata = new FreeBsdMetadata(json);

        assertThat(metadata.conflicts()).isEmpty();
    }

    @Test
    void metadataWithEmptyFiles() throws Exception {
        var json = ManifestParser.parse("""
                {
                  "name": "test",
                  "files": {}
                }
                """);

        FreeBsdMetadata metadata = new FreeBsdMetadata(json);

        assertThat(metadata.files()).isEmpty();
    }

    @Test
    void metadataWithEmptyLicenses() throws Exception {
        var json = ManifestParser.parse("""
                {
                  "name": "test",
                  "licenses": []
                }
                """);

        FreeBsdMetadata metadata = new FreeBsdMetadata(json);

        // Empty licenses array results in empty string, not empty Optional
        assertThat(metadata.license()).hasValue("");
    }

    @Test
    void parseManifestWithUnicodeContent() throws Exception {
        var json = ManifestParser.parse("""
                {
                  "name": "test",
                  "comment": "Ünïcödé description 日本語"
                }
                """);

        FreeBsdMetadata metadata = new FreeBsdMetadata(json);

        assertThat(metadata.summary()).contains("Ünïcödé description 日本語");
    }
}
