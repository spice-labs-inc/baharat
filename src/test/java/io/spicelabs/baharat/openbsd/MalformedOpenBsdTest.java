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
package io.spicelabs.baharat.openbsd;

import io.spicelabs.baharat.PackageFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for malformed and invalid OpenBSD packages.
 */
class MalformedOpenBsdTest {

    @TempDir
    Path tempDir;

    @Test
    void parseEmptyContents() {
        ContentsParser.ParseResult result = ContentsParser.parse("");

        assertThat(result.metadata()).isEmpty();
        assertThat(result.dependencies()).isEmpty();
        assertThat(result.files()).isEmpty();
    }

    @Test
    void parseContentsWithOnlyWhitespace() {
        ContentsParser.ParseResult result = ContentsParser.parse("   \n\n   \n");

        assertThat(result.metadata()).isEmpty();
        assertThat(result.files()).isEmpty();
    }

    @Test
    void parseContentsWithInvalidSize() {
        ContentsParser.ParseResult result = ContentsParser.parse("""
                @name test-1.0
                @size /usr/local/bin/test=invalid
                /usr/local/bin/test
                """);

        // Invalid size is ignored
        assertThat(result.files()).hasSize(1);
        assertThat(result.files().get(0).size()).isEqualTo(0L);
    }

    @Test
    void parseContentsWithMalformedSha() {
        ContentsParser.ParseResult result = ContentsParser.parse("""
                @name test-1.0
                @sha no-equals-sign
                /usr/local/bin/test
                """);

        // Malformed sha is ignored
        assertThat(result.files()).hasSize(1);
        assertThat(result.files().get(0).digest()).isEmpty();
    }

    @Test
    void detectOpenBsdFromTgzExtension() throws Exception {
        Path pkgFile = tempDir.resolve("test-1.0.tgz");
        // Gzip magic bytes (need at least 4 bytes for detection)
        byte[] gzipMagic = {0x1F, (byte) 0x8B, 0x08, 0x00};
        Files.write(pkgFile, gzipMagic);

        var format = PackageFormat.detect(pkgFile);

        assertThat(format).contains(PackageFormat.OPENBSD_PKG);
    }

    @Test
    void openbsdFormatProperties() {
        assertThat(PackageFormat.OPENBSD_PKG.extension()).isEqualTo(".tgz");
        assertThat(PackageFormat.OPENBSD_PKG.family()).isEqualTo(PackageFormat.Family.BSD);
        assertThat(PackageFormat.OPENBSD_PKG.magic()).isPresent();
        // Gzip magic
        assertThat(PackageFormat.OPENBSD_PKG.magic().get()).isEqualTo(new byte[]{0x1F, (byte) 0x8B});
    }

    @Test
    void metadataWithMissingName() {
        ContentsParser.ParseResult result = ContentsParser.parse("@pkgpath www/test\n");

        OpenBsdMetadata metadata = new OpenBsdMetadata(result, "");

        assertThat(metadata.name()).isEmpty();
        assertThat(metadata.version()).isEmpty();
    }

    @Test
    void nameVersionParsingEdgeCases() {
        // No version at all
        ContentsParser.ParseResult result1 = ContentsParser.parse("@name noversion\n");
        OpenBsdMetadata meta1 = new OpenBsdMetadata(result1, "");
        assertThat(meta1.name()).isEqualTo("noversion");
        assertThat(meta1.version()).isEmpty();

        // Version starting with non-digit
        ContentsParser.ParseResult result2 = ContentsParser.parse("@name pkg-vX.Y.Z\n");
        OpenBsdMetadata meta2 = new OpenBsdMetadata(result2, "");
        // Won't match pattern, returns full name as name
        assertThat(meta2.fullName()).isEqualTo("pkg-vX.Y.Z");
    }

    @Test
    void dependencyParsingWithMinimalFormat() {
        ContentsParser.ParseResult result = ContentsParser.parse("""
                @name test-1.0
                @depend simple
                """);

        OpenBsdMetadata metadata = new OpenBsdMetadata(result, "");

        assertThat(metadata.dependencies()).hasSize(1);
        assertThat(metadata.dependencies().get(0).name()).isEqualTo("simple");
    }

    @Test
    void parseContentsLargeFile() {
        StringBuilder sb = new StringBuilder();
        sb.append("@name test-1.0\n");
        for (int i = 0; i < 1000; i++) {
            sb.append("@depend devel/dep").append(i).append(":dep").append(i).append("-*:dep").append(i).append("-1.0\n");
        }
        for (int i = 0; i < 1000; i++) {
            sb.append("/usr/local/bin/file").append(i).append("\n");
        }

        ContentsParser.ParseResult result = ContentsParser.parse(sb.toString());

        assertThat(result.dependencies()).hasSize(1000);
        assertThat(result.files()).hasSize(1000);
    }

    @Test
    void metadataWithEmptyDescription() {
        ContentsParser.ParseResult result = ContentsParser.parse("@name test-1.0\n");

        OpenBsdMetadata metadata = new OpenBsdMetadata(result, "");

        assertThat(metadata.description()).isEmpty();
    }

    @Test
    void metadataWithAllOptionalFieldsMissing() {
        ContentsParser.ParseResult result = ContentsParser.parse("@name test-1.0\n");

        OpenBsdMetadata metadata = new OpenBsdMetadata(result, "");

        assertThat(metadata.pkgpath()).isEmpty();
        assertThat(metadata.summary()).isEmpty();
        assertThat(metadata.description()).isEmpty();
        assertThat(metadata.maintainer()).isEmpty();
        assertThat(metadata.url()).isEmpty();
        assertThat(metadata.arch()).isEmpty();
    }
}
