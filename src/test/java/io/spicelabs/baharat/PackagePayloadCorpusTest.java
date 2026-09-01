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
package io.spicelabs.baharat;

import io.spicelabs.baharat.testdata.PackageTestFiles;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Corpus-wide property test for the {@code Package.payload()} interface contract
 * (enhancement plan, requirement R3-general / T5).
 *
 * <p>The claim under test: for every real package fixture that
 * {@code PackageReader.read(Path)} can parse, {@code PackageReader.read(Path)
 * .payload()} succeeds and every entry has a non-blank path. Separately, at least
 * one fixture per format yields a non-empty payload.
 *
 * <p>Why a property test over the real corpus: the {@code Package} interface
 * documents {@code payload()} as the unified payload entry point; the RPM source-
 * path bug (plan) violated it for all 50 RPM fixtures while the other formats
 * already worked. This test is the guard rail that the fix restores the contract
 * corpus-wide and does not regress the other five formats.
 *
 * <p>LLM note: the parse-success precondition naturally excludes the ~13 broken
 * fixtures in the corpus (HTML 404 pages that do not parse) — there is no
 * hardcoded skip list, so corpus drift is tracked, not masked. The "at least one
 * non-empty per format" clause handles the legitimately empty RPM meta-package
 * (basesystem) without hardcoding a name.
 */
class PackagePayloadCorpusTest {

    private static final Map<PackageFormat, String> FORMAT_DIRS = Map.of(
            PackageFormat.RPM, PackageTestFiles.RPMS,
            PackageFormat.DEB, PackageTestFiles.DEBS,
            PackageFormat.PACMAN, PackageTestFiles.PACMAN,
            PackageFormat.APK, PackageTestFiles.APKS,
            PackageFormat.FREEBSD_PKG, PackageTestFiles.FREEBSD,
            PackageFormat.OPENBSD_PKG, PackageTestFiles.OPENBSD
    );

    private static final List<String> EXTENSIONS = List.of(
            ".rpm", ".deb", ".apk", ".pkg.tar.zst", ".pkg.tar.gz", ".pkg.tar.xz",
            ".pkg", ".txz", ".tgz");

    /**
     * Enumerates every regular file under a format's resource directory that
     * looks like a package by extension.
     */
    private static List<Path> fixtureFiles(PackageFormat format) throws IOException {
        List<Path> files = new ArrayList<>();
        for (String ext : EXTENSIONS) {
            files.addAll(PackageTestFiles.getAllFiles(FORMAT_DIRS.get(format), ext));
        }
        return files;
    }

    /**
     * Tests the corpus-wide property: every parseable fixture's
     * {@code payload()} succeeds with non-blank entry paths; every format has at
     * least one fixture with a non-empty payload.
     *
     * <p>Why: T5 in the plan. Red today for the RPM subset (all 50 RPM fixtures
     * fail {@code payload()} with "Cannot stream payload without source path").
     */
    @Test
    void payloadStreamsForAllParseableCorpusPackages() throws Exception {
        Map<PackageFormat, Integer> nonEmptyPerFormat = new EnumMap<>(PackageFormat.class);

        for (Map.Entry<PackageFormat, String> dir : FORMAT_DIRS.entrySet()) {
            PackageFormat format = dir.getKey();
            int parseable = 0;
            int nonEmpty = 0;

            for (Path fixture : fixtureFiles(format)) {
                Package pkg;
                try {
                    pkg = PackageReader.read(fixture);
                } catch (PackageException | IOException e) {
                    // Parse precondition not met (e.g., broken 404 fixtures) —
                    // the contract under test only applies to parseable packages.
                    // Only CHECKED failures are tolerated: an unchecked parser
                    // regression on a formerly-parseable fixture must fail this
                    // test, not be silently reclassified as "unparseable".
                    continue;
                }
                parseable++;

                try (var entries = pkg.payload()) {
                    List<PackageEntry> all = entries.toList();
                    for (PackageEntry entry : all) {
                        assertThat(entry.path())
                                .as("entry path in %s", fixture)
                                .isNotBlank();
                    }
                    if (!all.isEmpty()) {
                        nonEmpty++;
                    }
                }
            }

            assertThat(parseable)
                    .as("parseable fixtures for %s", format)
                    .isGreaterThan(0);
            nonEmptyPerFormat.put(format, nonEmpty);
        }

        for (Map.Entry<PackageFormat, Integer> e : nonEmptyPerFormat.entrySet()) {
            if (e.getValue() == 0) {
                fail("no fixture for " + e.getKey() + " yielded a non-empty payload");
            }
        }
    }
}
