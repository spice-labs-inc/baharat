package io.spicelabs.baharat.testdata;

import io.spicelabs.baharat.Package;
import io.spicelabs.baharat.PackageMetadata;
import io.spicelabs.baharat.PackageReader;
import io.spicelabs.baharat.common.Dependency;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Regenerates {@code src/test/resources/expected/<dir>.json} from the readers:
 *
 * <pre>
 *   mvn -q test-compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
 *       -Dexec.classpathScope=test -Dexec.mainClass=io.spicelabs.baharat.testdata.ExpectedPackagesGenerator
 * </pre>
 *
 * Run it after a reader fix and review the diff: the tables are expectations, so a change
 * in them is a change in behaviour. Existing {@code tier} values are kept.
 */
public final class ExpectedPackagesGenerator {

    /** Small, hand-verified packages Surveyor fetches on every run (tier 0). */
    static final Set<String> TIER0 = Set.of(
            "apks/libcap-2.70-r0.apk",
            "debs/hostname_3.23+nmu2ubuntu2_amd64.deb",
            "freebsd/tree-2.2.1.pkg",
            "openbsd/tree-0.62.tgz",
            "pacman/tree-2.2.1-1-x86_64.pkg.tar.zst",
            "rpms/v4/sed-4.9-1.fc40.x86_64.rpm",
            "rpms/v4/grep-3.11-7.fc40.x86_64.rpm",
            "rpms/v4/gzip-1.13-1.fc40.x86_64.rpm");

    public static void main(String[] args) throws IOException {
        Files.createDirectories(ExpectedPackages.TABLE_DIR);
        for (String dir : ExpectedPackages.DIRS) {
            List<ExpectedPackages.Expected> previous = ExpectedPackages.forDir(dir);
            List<ExpectedPackages.Expected> rows = new ArrayList<>();
            Path root = Path.of("src/test/resources").resolve(dir);
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(root)) {
                for (Path p : (Iterable<Path>) files.filter(Files::isRegularFile).sorted(Comparator.comparing(Path::toString))::iterator) {
                    String id = Path.of("src/test/resources").relativize(p).toString().replace('\\', '/');
                    if (id.endsWith(".json") || id.endsWith(".md") || id.endsWith(".txt")) {
                        continue;
                    }
                    rows.add(describe(id, p, previous));
                }
            }
            try (Writer w = Files.newBufferedWriter(ExpectedPackages.tableFor(dir))) {
                ExpectedPackages.gson().toJson(rows, w);
                w.write("\n");
            }
            System.out.println(ExpectedPackages.tableFor(dir) + ": " + rows.size() + " packages");
        }
    }

    static ExpectedPackages.Expected describe(String id, Path p, List<ExpectedPackages.Expected> previous) {
        ExpectedPackages.Expected e = new ExpectedPackages.Expected();
        e.id = id;
        e.file = p.getFileName().toString();
        e.tier = previous.stream().filter(x -> id.equals(x.id)).map(x -> x.tier).findFirst()
                .orElse(TIER0.contains(id) ? 0 : 1);
        try {
            Package pkg = PackageReader.read(p);
            PackageMetadata m = pkg.metadata();
            e.format = pkg.format().name();
            e.name = m.name();
            e.version = m.version();
            e.release = m.release().orElse(null);
            e.epoch = m.epoch().orElse(null);
            e.arch = m.arch();
            e.purl = m.purl().toCanonical();
            e.dependencies = m.dependencies().stream().map(Dependency::toVersionedString).sorted().toList();
            e.provides = m.provides().stream().map(Dependency::toVersionedString).sorted().toList();
            e.fileCount = m.files().size();
        } catch (Exception ex) {
            e.error = ex.getClass().getSimpleName() + ": " + ex.getMessage();
        }
        return e;
    }

    private ExpectedPackagesGenerator() {}
}
