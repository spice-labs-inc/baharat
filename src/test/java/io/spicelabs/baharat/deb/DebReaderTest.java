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
import io.spicelabs.baharat.common.Dependency;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for DEB control file parsing.
 */
class DebReaderTest {

    @Test
    void parseControlFile() throws Exception {
        String control = """
                Package: nginx
                Version: 1.24.0-1
                Architecture: amd64
                Maintainer: Debian Nginx Team <team@example.org>
                Depends: libc6 (>= 2.34), libpcre3
                Description: High performance web server
                 Nginx is a web server that can also be used as a
                 reverse proxy, load balancer, and HTTP cache.
                """;

        Map<String, String> fields = DebControlParser.parse(control);

        assertThat(fields.get("Package")).isEqualTo("nginx");
        assertThat(fields.get("Version")).isEqualTo("1.24.0-1");
        assertThat(fields.get("Architecture")).isEqualTo("amd64");
        assertThat(fields.get("Maintainer")).isEqualTo("Debian Nginx Team <team@example.org>");
        assertThat(fields.get("Depends")).isEqualTo("libc6 (>= 2.34), libpcre3");
        assertThat(fields.get("Description")).startsWith("High performance web server");
        assertThat(fields.get("Description")).contains("Nginx is a web server");
    }

    @Test
    void parseControlFileWithBlankLinesInDescription() throws Exception {
        String control = """
                Package: test
                Version: 1.0
                Description: Short description
                 .
                 Long description after blank line.
                """;

        Map<String, String> fields = DebControlParser.parse(control);

        assertThat(fields.get("Description")).contains("\n\n");
        assertThat(fields.get("Description")).contains("Long description after blank line");
    }

    @Test
    void parseControlFileRejectsInvalidLine() {
        String control = """
                Package: test
                invalid line without colon
                Version: 1.0
                """;

        assertThatThrownBy(() -> DebControlParser.parse(control))
                .isInstanceOf(PackageException.InvalidPackageException.class);
    }

    @Test
    void parseControlFileRejectsContinuationWithoutField() {
        String control = """
                 continuation without field
                Package: test
                """;

        assertThatThrownBy(() -> DebControlParser.parse(control))
                .isInstanceOf(PackageException.InvalidPackageException.class);
    }

    @Test
    void debMetadataParseDependencies() throws Exception {
        String control = """
                Package: nginx
                Version: 1.24.0
                Architecture: amd64
                Depends: libc6 (>= 2.34), libpcre3, libssl3 (<< 4.0)
                Pre-Depends: dpkg (>= 1.19.0)
                Recommends: logrotate
                Suggests: nginx-doc
                Conflicts: nginx-light
                Replaces: nginx-common (<< 1.20)
                Provides: httpd, httpd-cgi
                """;

        Map<String, String> fields = DebControlParser.parse(control);
        DebMetadata metadata = new DebMetadata(fields);

        assertThat(metadata.name()).isEqualTo("nginx");
        assertThat(metadata.version()).isEqualTo("1.24.0");
        assertThat(metadata.arch()).isEqualTo("amd64");

        // Check dependencies
        assertThat(metadata.dependencies()).hasSize(4); // Depends + Pre-Depends
        assertThat(metadata.dependencies())
                .extracting(Dependency::name)
                .contains("libc6", "libpcre3", "libssl3", "dpkg");

        // Check dependency versions
        Dependency libc = metadata.dependencies().stream()
                .filter(d -> d.name().equals("libc6"))
                .findFirst()
                .orElseThrow();
        assertThat(libc.operator()).isEqualTo(Dependency.Operator.GREATER_THAN_OR_EQUAL);
        assertThat(libc.version()).contains("2.34");

        Dependency libssl = metadata.dependencies().stream()
                .filter(d -> d.name().equals("libssl3"))
                .findFirst()
                .orElseThrow();
        assertThat(libssl.operator()).isEqualTo(Dependency.Operator.LESS_THAN);
        assertThat(libssl.version()).contains("4.0");

        // Check provides
        assertThat(metadata.provides())
                .extracting(Dependency::name)
                .containsExactlyInAnyOrder("httpd", "httpd-cgi");

        // Check recommends, suggests, conflicts, replaces
        assertThat(metadata.recommends())
                .extracting(Dependency::name)
                .contains("logrotate");
        assertThat(metadata.suggests())
                .extracting(Dependency::name)
                .contains("nginx-doc");
        assertThat(metadata.conflicts())
                .extracting(Dependency::name)
                .contains("nginx-light");
        assertThat(metadata.replaces())
                .extracting(Dependency::name)
                .contains("nginx-common");
    }

    @Test
    void debMetadataSize() throws Exception {
        String control = """
                Package: test
                Version: 1.0
                Architecture: all
                Installed-Size: 1024
                """;

        Map<String, String> fields = DebControlParser.parse(control);
        DebMetadata metadata = new DebMetadata(fields);

        // Installed-Size is in KB
        assertThat(metadata.installedSize()).isEqualTo(1024 * 1024);
    }

    @Test
    void debMetadataSummary() throws Exception {
        String control = """
                Package: test
                Version: 1.0
                Architecture: all
                Description: Short description
                 Long description continues here
                 over multiple lines.
                """;

        Map<String, String> fields = DebControlParser.parse(control);
        DebMetadata metadata = new DebMetadata(fields);

        assertThat(metadata.summary()).hasValueSatisfying(s -> assertThat(s).contains("Short description"));
        assertThat(metadata.summary().get()).doesNotContain("Long description");
        assertThat(metadata.description()).hasValueSatisfying(d -> assertThat(d).contains("Long description"));
    }

    @Test
    void debMetadataPriority() throws Exception {
        String control = """
                Package: test
                Version: 1.0
                Architecture: all
                Priority: optional
                Section: utils
                Essential: yes
                """;

        Map<String, String> fields = DebControlParser.parse(control);
        DebMetadata metadata = new DebMetadata(fields);

        assertThat(metadata.priority()).contains("optional");
        assertThat(metadata.group()).contains("utils");
        assertThat(metadata.isEssential()).isTrue();
    }
}
