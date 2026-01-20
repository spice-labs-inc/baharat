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

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ContentsParser}.
 */
class ContentsParserTest {

    @Test
    void parseNameDirective() {
        String contents = "@name nginx-1.24.0\n";

        ContentsParser.ParseResult result = ContentsParser.parse(contents);

        assertThat(result.metadata().get("name")).isEqualTo("nginx-1.24.0");
    }

    @Test
    void parsePkgpathDirective() {
        String contents = """
                @name test-1.0
                @pkgpath www/test
                """;

        ContentsParser.ParseResult result = ContentsParser.parse(contents);

        assertThat(result.metadata().get("pkgpath")).isEqualTo("www/test");
    }

    @Test
    void parseCommentDirective() {
        String contents = """
                @name test-1.0
                @comment Test package description
                """;

        ContentsParser.ParseResult result = ContentsParser.parse(contents);

        assertThat(result.metadata().get("comment")).isEqualTo("Test package description");
    }

    @Test
    void parseDependDirective() {
        String contents = """
                @name test-1.0
                @depend www/pcre2:pcre2-*:pcre2-10.42
                @depend security/openssl:openssl-*:openssl-3.1.0
                """;

        ContentsParser.ParseResult result = ContentsParser.parse(contents);

        assertThat(result.dependencies()).hasSize(2);
        assertThat(result.dependencies())
                .contains("www/pcre2:pcre2-*:pcre2-10.42", "security/openssl:openssl-*:openssl-3.1.0");
    }

    @Test
    void parseWantlibDirective() {
        String contents = """
                @name test-1.0
                @wantlib c.99.0
                @wantlib crypto.50.1
                """;

        ContentsParser.ParseResult result = ContentsParser.parse(contents);

        assertThat(result.dependencies())
                .contains("lib:c.99.0", "lib:crypto.50.1");
    }

    @Test
    void parseShaDirective() {
        String contents = """
                @name test-1.0
                @sha /usr/local/bin/test=abc123def456
                /usr/local/bin/test
                """;

        ContentsParser.ParseResult result = ContentsParser.parse(contents);

        assertThat(result.files()).hasSize(1);
        assertThat(result.files().get(0).digest()).contains("abc123def456");
    }

    @Test
    void parseSizeDirective() {
        String contents = """
                @name test-1.0
                @size /usr/local/bin/test=1048576
                /usr/local/bin/test
                """;

        ContentsParser.ParseResult result = ContentsParser.parse(contents);

        assertThat(result.files()).hasSize(1);
        assertThat(result.files().get(0).size()).isEqualTo(1048576L);
    }

    @Test
    void parseTimestampDirective() {
        String contents = """
                @name test-1.0
                @ts 1699574400
                """;

        ContentsParser.ParseResult result = ContentsParser.parse(contents);

        assertThat(result.metadata().get("timestamp")).isEqualTo("1699574400");
    }

    @Test
    void parseFilePaths() {
        String contents = """
                @name test-1.0
                /usr/local/bin/test
                /usr/local/share/test/README
                /usr/local/etc/test.conf
                """;

        ContentsParser.ParseResult result = ContentsParser.parse(contents);

        assertThat(result.files()).hasSize(3);
        assertThat(result.files())
                .extracting(f -> f.path())
                .containsExactly("/usr/local/bin/test", "/usr/local/share/test/README", "/usr/local/etc/test.conf");
    }

    @Test
    void ignoreOtherDirectives() {
        String contents = """
                @name test-1.0
                @cwd /usr/local
                @exec mkdir -p %D/share/test
                @unexec rm -rf %D/share/test
                @group wheel
                @owner root
                /usr/local/bin/test
                """;

        ContentsParser.ParseResult result = ContentsParser.parse(contents);

        // Should only have the file
        assertThat(result.files()).hasSize(1);
        assertThat(result.files().get(0).path()).isEqualTo("/usr/local/bin/test");
    }

    @Test
    void skipEmptyLines() {
        String contents = """
                @name test-1.0

                /usr/local/bin/test

                /usr/local/bin/test2

                """;

        ContentsParser.ParseResult result = ContentsParser.parse(contents);

        assertThat(result.files()).hasSize(2);
    }

    @Test
    void parseFromInputStream() throws Exception {
        String contents = "@name test-1.0\n/usr/local/bin/test\n";
        ByteArrayInputStream in = new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8));

        ContentsParser.ParseResult result = ContentsParser.parse(in);

        assertThat(result.metadata().get("name")).isEqualTo("test-1.0");
        assertThat(result.files()).hasSize(1);
    }

    @Test
    void parseEmptyContents() {
        ContentsParser.ParseResult result = ContentsParser.parse("");

        assertThat(result.metadata()).isEmpty();
        assertThat(result.dependencies()).isEmpty();
        assertThat(result.files()).isEmpty();
    }

    @Test
    void parseArchDirective() {
        String contents = """
                @name test-1.0
                @arch amd64
                """;

        ContentsParser.ParseResult result = ContentsParser.parse(contents);

        assertThat(result.metadata().get("arch")).isEqualTo("amd64");
    }

    @Test
    void parseHomepageDirective() {
        String contents = """
                @name test-1.0
                @homepage https://example.com
                """;

        ContentsParser.ParseResult result = ContentsParser.parse(contents);

        assertThat(result.metadata().get("homepage")).isEqualTo("https://example.com");
    }

    @Test
    void parseMaintainerDirective() {
        String contents = """
                @name test-1.0
                @maintainer test@openbsd.org
                """;

        ContentsParser.ParseResult result = ContentsParser.parse(contents);

        assertThat(result.metadata().get("maintainer")).isEqualTo("test@openbsd.org");
    }

    @Test
    void parseDirectiveWithoutValue() {
        String contents = """
                @name test-1.0
                @novalue
                """;

        ContentsParser.ParseResult result = ContentsParser.parse(contents);

        // Directive with no value should be handled gracefully
        assertThat(result.metadata().get("name")).isEqualTo("test-1.0");
    }

    @Test
    void fileSizeAndDigestCombined() {
        String contents = """
                @name test-1.0
                @sha /usr/local/bin/test=sha256abc
                @size /usr/local/bin/test=2048
                /usr/local/bin/test
                """;

        ContentsParser.ParseResult result = ContentsParser.parse(contents);

        assertThat(result.files()).hasSize(1);
        assertThat(result.files().get(0).digest()).contains("sha256abc");
        assertThat(result.files().get(0).size()).isEqualTo(2048L);
    }
}
