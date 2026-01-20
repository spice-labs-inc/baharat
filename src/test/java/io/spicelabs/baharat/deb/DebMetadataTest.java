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

import io.spicelabs.baharat.common.Dependency;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DebMetadata}.
 */
class DebMetadataTest {

    @Test
    void basicMetadata() throws Exception {
        Map<String, String> fields = DebControlParser.parse("""
                Package: nginx
                Version: 1.24.0-1ubuntu1
                Architecture: amd64
                """);

        DebMetadata metadata = new DebMetadata(fields);

        assertThat(metadata.name()).isEqualTo("nginx");
        assertThat(metadata.version()).isEqualTo("1.24.0-1ubuntu1");
        assertThat(metadata.arch()).isEqualTo("amd64");
    }

    @Test
    void maintainerField() throws Exception {
        Map<String, String> fields = DebControlParser.parse("""
                Package: test
                Version: 1.0
                Maintainer: Test User <test@example.com>
                """);

        DebMetadata metadata = new DebMetadata(fields);

        assertThat(metadata.maintainer()).contains("Test User <test@example.com>");
    }

    @Test
    void descriptionFields() throws Exception {
        Map<String, String> fields = DebControlParser.parse("""
                Package: test
                Version: 1.0
                Description: Short summary line
                 Long description that continues
                 over multiple lines.
                """);

        DebMetadata metadata = new DebMetadata(fields);

        assertThat(metadata.summary()).hasValueSatisfying(s ->
                assertThat(s).isEqualTo("Short summary line"));
        assertThat(metadata.description()).hasValueSatisfying(d ->
                assertThat(d).contains("Long description"));
    }

    @Test
    void descriptionOnlyShort() throws Exception {
        Map<String, String> fields = DebControlParser.parse("""
                Package: test
                Version: 1.0
                Description: Only short description
                """);

        DebMetadata metadata = new DebMetadata(fields);

        assertThat(metadata.summary()).contains("Only short description");
        // Long description may be empty or contain only the short description
    }

    @Test
    void installedSizeInKilobytes() throws Exception {
        Map<String, String> fields = DebControlParser.parse("""
                Package: test
                Version: 1.0
                Installed-Size: 2048
                """);

        DebMetadata metadata = new DebMetadata(fields);

        // Installed-Size is in KB, should be converted to bytes
        assertThat(metadata.installedSize()).isEqualTo(2048L * 1024);
    }

    @Test
    void installedSizeMissing() throws Exception {
        Map<String, String> fields = DebControlParser.parse("""
                Package: test
                Version: 1.0
                """);

        DebMetadata metadata = new DebMetadata(fields);

        assertThat(metadata.installedSize()).isEqualTo(0L);
    }

    @Test
    void urlField() throws Exception {
        Map<String, String> fields = DebControlParser.parse("""
                Package: test
                Version: 1.0
                Homepage: https://example.com/project
                """);

        DebMetadata metadata = new DebMetadata(fields);

        assertThat(metadata.url()).contains("https://example.com/project");
    }

    @Test
    void priorityAndSection() throws Exception {
        Map<String, String> fields = DebControlParser.parse("""
                Package: test
                Version: 1.0
                Priority: optional
                Section: utils
                """);

        DebMetadata metadata = new DebMetadata(fields);

        assertThat(metadata.priority()).contains("optional");
        assertThat(metadata.group()).contains("utils");
    }

    @Test
    void essentialPackage() throws Exception {
        Map<String, String> fields = DebControlParser.parse("""
                Package: base-files
                Version: 1.0
                Essential: yes
                """);

        DebMetadata metadata = new DebMetadata(fields);

        assertThat(metadata.isEssential()).isTrue();
    }

    @Test
    void nonEssentialPackage() throws Exception {
        Map<String, String> fields = DebControlParser.parse("""
                Package: test
                Version: 1.0
                Essential: no
                """);

        DebMetadata metadata = new DebMetadata(fields);

        assertThat(metadata.isEssential()).isFalse();
    }

    @Test
    void missingEssential() throws Exception {
        Map<String, String> fields = DebControlParser.parse("""
                Package: test
                Version: 1.0
                """);

        DebMetadata metadata = new DebMetadata(fields);

        assertThat(metadata.isEssential()).isFalse();
    }

    @Test
    void dependsField() throws Exception {
        Map<String, String> fields = DebControlParser.parse("""
                Package: test
                Version: 1.0
                Depends: libc6 (>= 2.34), libssl3, zlib1g (>= 1:1.2.11)
                """);

        DebMetadata metadata = new DebMetadata(fields);

        assertThat(metadata.dependencies()).hasSize(3);
        assertThat(metadata.dependencies())
                .extracting(Dependency::name)
                .containsExactlyInAnyOrder("libc6", "libssl3", "zlib1g");

        Dependency libc = metadata.dependencies().stream()
                .filter(d -> d.name().equals("libc6"))
                .findFirst()
                .orElseThrow();
        assertThat(libc.operator()).isEqualTo(Dependency.Operator.GREATER_THAN_OR_EQUAL);
        assertThat(libc.version()).contains("2.34");
    }

    @Test
    void preDepends() throws Exception {
        Map<String, String> fields = DebControlParser.parse("""
                Package: test
                Version: 1.0
                Pre-Depends: dpkg (>= 1.19.0)
                """);

        DebMetadata metadata = new DebMetadata(fields);

        assertThat(metadata.dependencies())
                .extracting(Dependency::name)
                .contains("dpkg");
        assertThat(metadata.dependencies())
                .extracting(Dependency::type)
                .contains(Dependency.Type.PRE_DEPENDS);
    }

    @Test
    void providesField() throws Exception {
        Map<String, String> fields = DebControlParser.parse("""
                Package: test
                Version: 1.0
                Provides: httpd, httpd-cgi
                """);

        DebMetadata metadata = new DebMetadata(fields);

        assertThat(metadata.provides())
                .extracting(Dependency::name)
                .containsExactlyInAnyOrder("httpd", "httpd-cgi");
    }

    @Test
    void conflictsField() throws Exception {
        Map<String, String> fields = DebControlParser.parse("""
                Package: test
                Version: 1.0
                Conflicts: test-old, another-pkg
                """);

        DebMetadata metadata = new DebMetadata(fields);

        assertThat(metadata.conflicts())
                .extracting(Dependency::name)
                .containsExactlyInAnyOrder("test-old", "another-pkg");
    }

    @Test
    void replacesField() throws Exception {
        Map<String, String> fields = DebControlParser.parse("""
                Package: test
                Version: 1.0
                Replaces: test-old (<< 2.0)
                """);

        DebMetadata metadata = new DebMetadata(fields);

        assertThat(metadata.replaces())
                .extracting(Dependency::name)
                .contains("test-old");
        assertThat(metadata.replaces().get(0).operator())
                .isEqualTo(Dependency.Operator.LESS_THAN);
    }

    @Test
    void recommendsField() throws Exception {
        Map<String, String> fields = DebControlParser.parse("""
                Package: test
                Version: 1.0
                Recommends: suggested-pkg, another-suggestion
                """);

        DebMetadata metadata = new DebMetadata(fields);

        assertThat(metadata.recommends())
                .extracting(Dependency::name)
                .containsExactlyInAnyOrder("suggested-pkg", "another-suggestion");
    }

    @Test
    void suggestsField() throws Exception {
        Map<String, String> fields = DebControlParser.parse("""
                Package: test
                Version: 1.0
                Suggests: optional-pkg
                """);

        DebMetadata metadata = new DebMetadata(fields);

        assertThat(metadata.suggests())
                .extracting(Dependency::name)
                .contains("optional-pkg");
    }

    @Test
    void dependencyWithEpoch() throws Exception {
        Map<String, String> fields = DebControlParser.parse("""
                Package: test
                Version: 1.0
                Depends: zlib1g (>= 1:1.2.11)
                """);

        DebMetadata metadata = new DebMetadata(fields);

        Dependency zlib = metadata.dependencies().get(0);
        assertThat(zlib.version()).contains("1:1.2.11");
    }

    @Test
    void dependencyOperators() throws Exception {
        Map<String, String> fields = DebControlParser.parse("""
                Package: test
                Version: 1.0
                Depends: pkg1 (= 1.0), pkg2 (>> 2.0), pkg3 (<< 3.0), pkg4 (>= 4.0), pkg5 (<= 5.0)
                """);

        DebMetadata metadata = new DebMetadata(fields);

        assertThat(metadata.dependencies()).hasSize(5);

        Dependency pkg1 = findByName(metadata, "pkg1");
        assertThat(pkg1.operator()).isEqualTo(Dependency.Operator.EQUAL);

        Dependency pkg2 = findByName(metadata, "pkg2");
        assertThat(pkg2.operator()).isEqualTo(Dependency.Operator.GREATER_THAN);

        Dependency pkg3 = findByName(metadata, "pkg3");
        assertThat(pkg3.operator()).isEqualTo(Dependency.Operator.LESS_THAN);

        Dependency pkg4 = findByName(metadata, "pkg4");
        assertThat(pkg4.operator()).isEqualTo(Dependency.Operator.GREATER_THAN_OR_EQUAL);

        Dependency pkg5 = findByName(metadata, "pkg5");
        assertThat(pkg5.operator()).isEqualTo(Dependency.Operator.LESS_THAN_OR_EQUAL);
    }

    @Test
    void emptyDependencies() throws Exception {
        Map<String, String> fields = DebControlParser.parse("""
                Package: test
                Version: 1.0
                """);

        DebMetadata metadata = new DebMetadata(fields);

        assertThat(metadata.dependencies()).isEmpty();
        assertThat(metadata.provides()).isEmpty();
        assertThat(metadata.conflicts()).isEmpty();
        assertThat(metadata.replaces()).isEmpty();
        assertThat(metadata.recommends()).isEmpty();
        assertThat(metadata.suggests()).isEmpty();
    }

    @Test
    void source() throws Exception {
        Map<String, String> fields = DebControlParser.parse("""
                Package: nginx
                Version: 1.24.0
                Source: nginx (1.24.0-1)
                """);

        DebMetadata metadata = new DebMetadata(fields);

        assertThat(metadata.source()).contains("nginx (1.24.0-1)");
    }

    private Dependency findByName(DebMetadata metadata, String name) {
        return metadata.dependencies().stream()
                .filter(d -> d.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Dependency not found: " + name));
    }
}
