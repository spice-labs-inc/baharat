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

import io.spicelabs.baharat.common.Dependency;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link OpenBsdMetadata}.
 */
class OpenBsdMetadataTest {

    @Test
    void basicMetadata() {
        ContentsParser.ParseResult result = ContentsParser.parse("""
                @name nginx-1.24.0
                @pkgpath www/nginx
                """);

        OpenBsdMetadata metadata = new OpenBsdMetadata(result, "");

        assertThat(metadata.name()).isEqualTo("nginx");
        assertThat(metadata.version()).isEqualTo("1.24.0");
        assertThat(metadata.fullName()).isEqualTo("nginx-1.24.0");
    }

    @Test
    void nameVersionParsing() {
        // Standard format: name-version
        ContentsParser.ParseResult result1 = ContentsParser.parse("@name curl-8.5.0\n");
        OpenBsdMetadata meta1 = new OpenBsdMetadata(result1, "");
        assertThat(meta1.name()).isEqualTo("curl");
        assertThat(meta1.version()).isEqualTo("8.5.0");

        // Name with hyphen: multi-word-name-1.0
        ContentsParser.ParseResult result2 = ContentsParser.parse("@name some-package-1.2.3\n");
        OpenBsdMetadata meta2 = new OpenBsdMetadata(result2, "");
        assertThat(meta2.name()).isEqualTo("some-package");
        assertThat(meta2.version()).isEqualTo("1.2.3");

        // Complex version: name-1.2.3p4
        ContentsParser.ParseResult result3 = ContentsParser.parse("@name test-1.2.3p4\n");
        OpenBsdMetadata meta3 = new OpenBsdMetadata(result3, "");
        assertThat(meta3.name()).isEqualTo("test");
        assertThat(meta3.version()).isEqualTo("1.2.3p4");
    }

    @Test
    void pkgpath() {
        ContentsParser.ParseResult result = ContentsParser.parse("""
                @name test-1.0
                @pkgpath www/test
                """);

        OpenBsdMetadata metadata = new OpenBsdMetadata(result, "");

        assertThat(metadata.pkgpath()).contains("www/test");
    }

    @Test
    void summaryFromComment() {
        ContentsParser.ParseResult result = ContentsParser.parse("""
                @name test-1.0
                @comment Short description
                """);

        OpenBsdMetadata metadata = new OpenBsdMetadata(result, "");

        assertThat(metadata.summary()).contains("Short description");
    }

    @Test
    void descriptionFromDescFile() {
        ContentsParser.ParseResult result = ContentsParser.parse("@name test-1.0\n");
        String description = """
                Test package for unit testing.

                This is a longer description that spans
                multiple lines.
                """;

        OpenBsdMetadata metadata = new OpenBsdMetadata(result, description);

        assertThat(metadata.description()).isPresent();
        assertThat(metadata.description().get()).contains("Test package for unit testing");
    }

    @Test
    void maintainer() {
        ContentsParser.ParseResult result = ContentsParser.parse("""
                @name test-1.0
                @maintainer test@openbsd.org
                """);

        OpenBsdMetadata metadata = new OpenBsdMetadata(result, "");

        assertThat(metadata.maintainer()).contains("test@openbsd.org");
    }

    @Test
    void url() {
        ContentsParser.ParseResult result = ContentsParser.parse("""
                @name test-1.0
                @homepage https://openbsd.org
                """);

        OpenBsdMetadata metadata = new OpenBsdMetadata(result, "");

        assertThat(metadata.url()).contains("https://openbsd.org");
    }

    @Test
    void dependencies() {
        ContentsParser.ParseResult result = ContentsParser.parse("""
                @name test-1.0
                @depend devel/pcre2:pcre2-*:pcre2-10.42
                @depend security/openssl:openssl-*:openssl-3.0
                @wantlib c.99.0
                @wantlib crypto.50.1
                """);

        OpenBsdMetadata metadata = new OpenBsdMetadata(result, "");

        assertThat(metadata.dependencies()).hasSize(4);

        // Package dependencies
        assertThat(metadata.dependencies())
                .extracting(Dependency::name)
                .contains("pcre2-10.42", "openssl-3.0");

        // Library dependencies (wantlib)
        assertThat(metadata.dependencies())
                .extracting(Dependency::name)
                .contains("lib:c.99.0", "lib:crypto.50.1");
    }

    @Test
    void installedSize() {
        ContentsParser.ParseResult result = ContentsParser.parse("""
                @name test-1.0
                @size /usr/local/bin/test1=1000
                @size /usr/local/bin/test2=2000
                /usr/local/bin/test1
                /usr/local/bin/test2
                """);

        OpenBsdMetadata metadata = new OpenBsdMetadata(result, "");

        // Installed size is sum of all file sizes
        assertThat(metadata.installedSize()).isEqualTo(3000L);
    }

    @Test
    void files() {
        ContentsParser.ParseResult result = ContentsParser.parse("""
                @name test-1.0
                @sha /usr/local/bin/test=abc123
                @size /usr/local/bin/test=1024
                /usr/local/bin/test
                /usr/local/share/test/README
                """);

        OpenBsdMetadata metadata = new OpenBsdMetadata(result, "");

        assertThat(metadata.files()).hasSize(2);
        assertThat(metadata.files())
                .extracting(f -> f.path())
                .containsExactly("/usr/local/bin/test", "/usr/local/share/test/README");
    }

    @Test
    void emptyLists() {
        ContentsParser.ParseResult result = ContentsParser.parse("@name test-1.0\n");

        OpenBsdMetadata metadata = new OpenBsdMetadata(result, "");

        assertThat(metadata.dependencies()).isEmpty();
        assertThat(metadata.files()).isEmpty();
    }

    @Test
    void nameWithoutVersion() {
        ContentsParser.ParseResult result = ContentsParser.parse("@name noversion\n");

        OpenBsdMetadata metadata = new OpenBsdMetadata(result, "");

        assertThat(metadata.name()).isEqualTo("noversion");
        assertThat(metadata.version()).isEmpty();
    }

    @Test
    void missingName() {
        ContentsParser.ParseResult result = ContentsParser.parse("@pkgpath www/test\n");

        OpenBsdMetadata metadata = new OpenBsdMetadata(result, "");

        assertThat(metadata.name()).isEmpty();
        assertThat(metadata.version()).isEmpty();
        assertThat(metadata.fullName()).isEmpty();
    }

    @Test
    void getMetadata() {
        ContentsParser.ParseResult result = ContentsParser.parse("""
                @name test-1.0
                @pkgpath www/test
                @comment Description
                """);

        OpenBsdMetadata metadata = new OpenBsdMetadata(result, "");

        assertThat(metadata.getMetadata()).containsKeys("name", "pkgpath", "comment");
        assertThat(metadata.getMetadata().get("name")).isEqualTo("test-1.0");
    }

    @Test
    void arch() {
        ContentsParser.ParseResult result = ContentsParser.parse("""
                @name test-1.0
                @arch amd64
                """);

        OpenBsdMetadata metadata = new OpenBsdMetadata(result, "");

        assertThat(metadata.arch()).isEqualTo("amd64");
    }
}
