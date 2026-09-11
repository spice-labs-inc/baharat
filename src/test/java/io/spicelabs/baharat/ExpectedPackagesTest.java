package io.spicelabs.baharat;

import static org.assertj.core.api.Assertions.assertThat;

import io.spicelabs.baharat.common.Dependency;
import io.spicelabs.baharat.testdata.ExpectedPackages;
import io.spicelabs.baharat.testdata.ExpectedPackages.Expected;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every committed test package matches its row in {@code src/test/resources/expected/<dir>.json}.
 * The tables are the one definition shared with Surveyor's black-box integration tests, which
 * survey the same packages through {@code spice} and check name, version, arch and purl by the
 * same {@code id}.
 */
class ExpectedPackagesTest {

    static Stream<Expected> all() throws IOException {
        return ExpectedPackages.all().stream().filter(e -> e.error == null);
    }

    @Test
    void everyPackageHasExactlyOneRowAndIdsAreUnique() throws IOException {
        Set<String> ids = new HashSet<>();
        List<Expected> rows = ExpectedPackages.all();
        assertThat(rows).as("rows in " + ExpectedPackages.TABLE_DIR).isNotEmpty();
        for (Expected e : rows) {
            assertThat(ids.add(e.id)).as("duplicate id " + e.id).isTrue();
            assertThat(Files.isRegularFile(ExpectedPackages.pathOf(e))).as("package for " + e.id).isTrue();
        }
        for (String dir : ExpectedPackages.DIRS) {
            Path root = Path.of("src/test/resources").resolve(dir);
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> files = Files.walk(root)) {
                for (Path p : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                    String id = Path.of("src/test/resources").relativize(p).toString().replace('\\', '/');
                    if (id.endsWith(".json") || id.endsWith(".md") || id.endsWith(".txt")) continue;
                    assertThat(ids).as("row for " + id + " (run ExpectedPackagesGenerator)").contains(id);
                }
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("all")
    void readerMatchesTheTable(Expected e) throws Exception {
        {
            Package pkg = PackageReader.read(ExpectedPackages.pathOf(e));
            PackageMetadata m = pkg.metadata();
            assertThat(pkg.format().name()).isEqualTo(e.format);
            assertThat(m.name()).isEqualTo(e.name);
            assertThat(m.version()).isEqualTo(e.version);
            assertThat(m.release().orElse(null)).isEqualTo(e.release);
            assertThat(m.epoch().orElse(null)).isEqualTo(e.epoch);
            assertThat(m.arch()).isEqualTo(e.arch);
            assertThat(m.purl().toCanonical()).isEqualTo(e.purl);
            assertThat(m.dependencies().stream().map(Dependency::toVersionedString).sorted().toList()).isEqualTo(e.dependencies);
            assertThat(m.provides().stream().map(Dependency::toVersionedString).sorted().toList()).isEqualTo(e.provides);
            if (e.fileCount != null) {
                assertThat(m.files().size()).isEqualTo(e.fileCount);
            }
        }
    }
}
