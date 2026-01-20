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
 * Tests for OpenBSD +CONTENTS parsing.
 */
class OpenBsdReaderTest {

    @Test
    void parseContents() throws Exception {
        String contents = """
                @name nginx-1.24.0
                @pkgpath www/nginx
                @comment High performance web server
                @depend www/pcre2:pcre2-*:pcre2-10.42
                @depend security/openssl:openssl-*:openssl-3.1.0
                @wantlib c.99.0
                @sha /usr/local/sbin/nginx=abc123def456
                @size /usr/local/sbin/nginx=1048576
                /usr/local/sbin/nginx
                /usr/local/etc/nginx/nginx.conf
                """;

        ContentsParser.ParseResult result = ContentsParser.parse(contents);

        assertThat(result.metadata().get("name")).isEqualTo("nginx-1.24.0");
        assertThat(result.metadata().get("pkgpath")).isEqualTo("www/nginx");
        assertThat(result.metadata().get("comment")).isEqualTo("High performance web server");

        // Dependencies
        assertThat(result.dependencies()).hasSize(3);
        assertThat(result.dependencies())
                .contains("www/pcre2:pcre2-*:pcre2-10.42",
                        "security/openssl:openssl-*:openssl-3.1.0",
                        "lib:c.99.0");

        // Files
        assertThat(result.files()).hasSize(2);
        assertThat(result.files())
                .extracting(f -> f.path())
                .containsExactly("/usr/local/sbin/nginx", "/usr/local/etc/nginx/nginx.conf");

        // Check file attributes from @sha and @size
        assertThat(result.files().get(0).digest()).contains("abc123def456");
        assertThat(result.files().get(0).size()).isEqualTo(1048576L);
    }

    @Test
    void openBsdMetadataNameVersion() throws Exception {
        String contents = """
                @name curl-8.5.0
                @pkgpath www/curl
                """;

        ContentsParser.ParseResult result = ContentsParser.parse(contents);
        OpenBsdMetadata metadata = new OpenBsdMetadata(result, "");

        assertThat(metadata.name()).isEqualTo("curl");
        assertThat(metadata.version()).isEqualTo("8.5.0");
        assertThat(metadata.fullName()).isEqualTo("curl-8.5.0");
        assertThat(metadata.pkgpath()).contains("www/curl");
    }

    @Test
    void openBsdMetadataDependencies() throws Exception {
        String contents = """
                @name test-1.0
                @depend devel/pcre2:pcre2-*:pcre2-10.42
                @depend security/openssl:openssl-*:openssl-3.0
                @wantlib c.99.0
                @wantlib crypto.50.1
                """;

        ContentsParser.ParseResult result = ContentsParser.parse(contents);
        OpenBsdMetadata metadata = new OpenBsdMetadata(result, "");

        assertThat(metadata.dependencies()).hasSize(4);

        // Package dependencies use the actual name (last part)
        assertThat(metadata.dependencies())
                .extracting(Dependency::name)
                .contains("pcre2-10.42", "openssl-3.0");

        // Library dependencies are prefixed with lib:
        assertThat(metadata.dependencies())
                .extracting(Dependency::name)
                .contains("lib:c.99.0", "lib:crypto.50.1");
    }

    @Test
    void openBsdMetadataDescription() throws Exception {
        String contents = """
                @name test-1.0
                @comment Short description
                @maintainer test@openbsd.org
                @homepage https://example.com
                """;

        String description = """
                Test package for unit testing.

                This is a longer description that spans
                multiple lines.
                """;

        ContentsParser.ParseResult result = ContentsParser.parse(contents);
        OpenBsdMetadata metadata = new OpenBsdMetadata(result, description);

        assertThat(metadata.summary()).hasValueSatisfying(s -> assertThat(s).contains("Short description"));
        assertThat(metadata.description()).hasValueSatisfying(d -> assertThat(d).contains("Test package for unit testing"));
        assertThat(metadata.maintainer()).hasValueSatisfying(m -> assertThat(m).contains("test@openbsd.org"));
        assertThat(metadata.url()).hasValueSatisfying(u -> assertThat(u).contains("https://example.com"));
    }

    @Test
    void parseContentsWithTimestamp() throws Exception {
        String contents = """
                @name test-1.0
                @ts 1699574400
                /usr/local/bin/test
                """;

        ContentsParser.ParseResult result = ContentsParser.parse(contents);

        assertThat(result.metadata().get("timestamp")).isEqualTo("1699574400");
    }

    @Test
    void parseContentsIgnoresOtherDirectives() throws Exception {
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

        // Should only have the file, not metadata for ignored directives
        assertThat(result.files()).hasSize(1);
        assertThat(result.files().get(0).path()).isEqualTo("/usr/local/bin/test");
    }

    @Test
    void openBsdMetadataInstalledSize() throws Exception {
        String contents = """
                @name test-1.0
                @size /usr/local/bin/test1=1000
                @size /usr/local/bin/test2=2000
                /usr/local/bin/test1
                /usr/local/bin/test2
                """;

        ContentsParser.ParseResult result = ContentsParser.parse(contents);
        OpenBsdMetadata metadata = new OpenBsdMetadata(result, "");

        // Installed size is sum of all file sizes
        assertThat(metadata.installedSize()).isEqualTo(3000L);
    }
}
