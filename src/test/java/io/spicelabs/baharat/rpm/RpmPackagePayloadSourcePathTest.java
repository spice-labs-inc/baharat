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
package io.spicelabs.baharat.rpm;

import io.spicelabs.baharat.Package;
import io.spicelabs.baharat.PackageEntry;
import io.spicelabs.baharat.PackageException;
import io.spicelabs.baharat.PackageFormat;
import io.spicelabs.baharat.PackageReader;
import io.spicelabs.baharat.rpm.payload.PayloadEntry;
import io.spicelabs.baharat.rpm.testdata.TestFiles;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the RPM payload source-path contract (enhancement plan
 * {@code plans/2026_09_01_rpm_payload_source_path_fix.md}, requirements R1-R5).
 *
 * <p>Background: in baharat 0.2.0, {@code RpmReader.read(Path)} parsed through
 * {@code read(InputStream)} and returned the stream-built {@code RpmPackage}
 * (sourcePath = null), so {@code Package.payload()} threw "Cannot stream payload
 * without source path" even for path-based reads. The other five readers all
 * re-wrap with the path. These tests lock the fix (T1, T1b, T2, T3, T4) and the
 * documented stream-based restriction (T6, T7).
 */
class RpmPackagePayloadSourcePathTest {

    /**
     * All RPM fixtures, failing loudly if the corpus directory is missing so the
     * corpus-driven tests cannot pass vacuously on an empty list.
     */
    private static List<Path> allRpmFixtures() throws IOException {
        List<Path> files = TestFiles.getAllRpmFiles();
        assertThat(files).as("RPM test corpus present").isNotEmpty();
        return files;
    }

    // ── T1 (R2): path-based reads retain the source path ─────────────────────

    /**
     * Tests that {@code RpmReader.read(Path)} returns a package whose
     * {@code sourcePath()} equals the input path, for every RPM fixture in the
     * test corpus.
     *
     * <p>Why: the fix (plan D1) re-wraps parsed components with the path in
     * {@code read(Path)}. Before the fix, {@code read(Path)} returned the
     * stream-built package with {@code sourcePath() == null}.
     *
     * <p>LLM note: parameterized over all 50 RPM fixtures; asserts object
     * identity of the path (not string equality) so a relative vs absolute
     * mismatch cannot slip through. Red today (returns null).
     */
    @Test
    void readPathRetainsSourcePath() throws Exception {
        for (Path fixture : allRpmFixtures()) {
            RpmPackage rpm = RpmReader.read(fixture);
            assertThat(rpm.sourcePath())
                    .as("RpmReader.read(%s).sourcePath()", fixture)
                    .isEqualTo(fixture);
        }
    }

    // ── T1b (R2): parsed state is identical between path and stream reads ────

    /**
     * Tests that the package from {@code RpmReader.read(Path)} exposes identical
     * parsed state to the package from {@code RpmReader.read(InputStream)} on the
     * same file: lead name, header entry counts, metadata name/version/release,
     * nevra, payload offset, and canonical purl.
     *
     * <p>Why: the fix (D1) copies lead/signatureHeader/header/payloadOffset into
     * a new {@code RpmPackage}. A wrong-component copy bug would pass the
     * source-path test (T1) and the payload test (T2) yet corrupt metadata — this
     * test guards the copy itself.
     *
     * <p>LLM note: the two packages are different objects; compare field-by-field
     * via accessors. Green today (read(Path) returns exactly the stream-built
     * object, so parity is trivial); it is a regression guard for the re-wrap.
     */
    @Test
    void readPathParsedStateParity() throws Exception {
        for (Path fixture : allRpmFixtures()) {
            RpmPackage fromPath = RpmReader.read(fixture);
            RpmPackage fromStream;
            try (InputStream in = Files.newInputStream(fixture)) {
                fromStream = RpmReader.read(in);
            }

            assertThat(fromPath.lead().name()).as("lead name %s", fixture)
                    .isEqualTo(fromStream.lead().name());
            assertThat(fromPath.signatureHeader().entries().size())
                    .as("signature header entry count %s", fixture)
                    .isEqualTo(fromStream.signatureHeader().entries().size());
            assertThat(fromPath.header().entries().size())
                    .as("main header entry count %s", fixture)
                    .isEqualTo(fromStream.header().entries().size());
            assertThat(fromPath.metadata().name()).as("name %s", fixture)
                    .isEqualTo(fromStream.metadata().name());
            assertThat(fromPath.metadata().version()).as("version %s", fixture)
                    .isEqualTo(fromStream.metadata().version());
            assertThat(fromPath.metadata().release()).as("release %s", fixture)
                    .isEqualTo(fromStream.metadata().release());
            assertThat(fromPath.nevra()).as("nevra %s", fixture)
                    .isEqualTo(fromStream.nevra());
            assertThat(fromPath.payloadOffset()).as("payload offset %s", fixture)
                    .isEqualTo(fromStream.payloadOffset());
            assertThat(fromPath.purl().toCanonical()).as("purl %s", fixture)
                    .isEqualTo(fromStream.purl().toCanonical());
        }
    }

    // ── T2 (R2): payload() parity with the low-level streamPayload ───────────

    /**
     * Tests that {@code RpmReader.read(Path).payload()} yields the same ordered
     * list of entry paths as {@code RpmReader.streamPayload(Path)} for every RPM
     * fixture.
     *
     * <p>Why: {@code Package.payload()} is documented as the payload entry point;
     * parity with the low-level API proves no data loss, reordering, or path
     * corruption after the fix. Handles the legitimately empty payload
     * (basesystem meta-package) via equality rather than non-emptiness.
     *
     * <p>LLM note: ordered comparison is valid because both call sites share one
     * code path ({@code payload()} delegates to {@code streamPayload}). Red today
     * (payload() throws).
     */
    @Test
    void readPathPayloadParityWithStreamPayload() throws Exception {
        for (Path fixture : allRpmFixtures()) {
            RpmPackage rpm = RpmReader.read(fixture);
            List<String> viaPackage;
            try (var entries = rpm.payload()) {
                viaPackage = entries.map(PackageEntry::path).toList();
            }
            List<String> viaLowLevel;
            try (var entries = RpmReader.streamPayload(fixture)) {
                viaLowLevel = entries.map(PayloadEntry::path).toList();
            }
            assertThat(viaPackage).as("payload paths %s", fixture)
                    .isEqualTo(viaLowLevel);
        }
    }

    /**
     * Spot-checks that file CONTENT (not just paths) flows through
     * {@code Package.payload()}: the first file entry of a representative
     * fixture opens a non-empty content stream.
     *
     * <p>Why: path parity alone would not catch a regression where entry paths
     * stream but content streams are empty or broken.
     *
     * <p>LLM note: the fixture ({@code zstd}) is known to contain file entries;
     * if the first file entry has zero content the test fails loudly.
     */
    @Test
    void payloadFileContentStreams() throws Exception {
        Path fixture = TestFiles.getPath("v4/zstd-1.5.5-5.fc40.x86_64.rpm");
        RpmPackage rpm = RpmReader.read(fixture);
        try (var entries = rpm.payload()) {
            PackageEntry.FileEntry file = entries
                    .filter(PackageEntry.FileEntry.class::isInstance)
                    .map(PackageEntry.FileEntry.class::cast)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "expected at least one file entry in " + fixture));
            try (InputStream content = file.content()) {
                assertThat(content.read()).as("first byte of %s", file.path())
                        .isGreaterThanOrEqualTo(0);
                assertThat(file.size()).isGreaterThan(0);
            }
        }
    }

    // ── T3 (R1): PackageReader.readRpm(Path).payload() works ─────────────────

    /**
     * Tests that {@code PackageReader.readRpm(Path).payload()} streams entries
     * with path parity against {@code RpmReader.streamPayload(Path)} for every
     * RPM fixture.
     *
     * <p>Why: R1 — the ask names {@code PackageReader.readRpm(Path)} explicitly.
     * It delegates to {@code RpmReader.read(Path)}, so the fix flows through; this
     * guards the public entry point. Red today.
     */
    @Test
    void packageReaderReadRpmPathPayloadStreams() throws Exception {
        for (Path fixture : allRpmFixtures()) {
            io.spicelabs.baharat.rpm.RpmPackage rpm = PackageReader.readRpm(fixture);
            List<String> viaPackage;
            try (var entries = rpm.payload()) {
                viaPackage = entries.map(PackageEntry::path).toList();
            }
            List<String> viaLowLevel;
            try (var entries = RpmReader.streamPayload(fixture)) {
                viaLowLevel = entries.map(PayloadEntry::path).toList();
            }
            assertThat(viaPackage).as("PackageReader.readRpm(%s) payload paths", fixture)
                    .isEqualTo(viaLowLevel);
        }
    }

    // ── T4 (R3): auto-detect PackageReader.read(Path).payload() works ────────

    /**
     * Tests that {@code PackageReader.read(Path)} on an RPM fixture returns an
     * RPM package whose {@code payload()} streams entries with path parity against
     * {@code RpmReader.streamPayload(Path)}.
     *
     * <p>Why: R3 — this is the exact Goat Rodeo call sequence from the README
     * ({@code PackageReader.read(path)} then {@code pkg.payload()}), which failed
     * for RPM before the fix. Red today.
     */
    @Test
    void packageReaderAutoDetectRpmPayloadStreams() throws Exception {
        for (Path fixture : allRpmFixtures()) {
            Package pkg = PackageReader.read(fixture);
            assertThat(pkg.format()).as("format %s", fixture).isEqualTo(PackageFormat.RPM);
            List<String> viaPackage;
            try (var entries = pkg.payload()) {
                viaPackage = entries.map(PackageEntry::path).toList();
            }
            List<String> viaLowLevel;
            try (var entries = RpmReader.streamPayload(fixture)) {
                viaLowLevel = entries.map(PayloadEntry::path).toList();
            }
            assertThat(viaPackage).as("PackageReader.read(%s) payload paths", fixture)
                    .isEqualTo(viaLowLevel);
        }
    }

    // ── T6 (R4): stream reads throw with documented guidance ────────────────

    /**
     * Tests that packages read from an InputStream cannot stream payload and that
     * the {@code PackageException} message keeps the historical prefix AND names
     * a workable alternative ({@code streamPayload} / {@code Path}).
     *
     * <p>Why: R4 — the restriction must be explicit, documented behavior with
     * actionable guidance, not a bare statement. Red today (the current message
     * has no guidance).
     *
     * <p>LLM note: asserts on prefix + guidance substrings, never the exact full
     * string, so future wording tweaks do not churn the test. Also asserts the
     * exception carries {@code PackageFormat.RPM}.
     */
    @Test
    void streamReadPayloadThrowsWithDocumentedGuidance() throws Exception {
        Path fixture = TestFiles.getPath("v4/zstd-1.5.5-5.fc40.x86_64.rpm");

        // Via PackageReader.readRpm(InputStream)
        try (InputStream in = Files.newInputStream(fixture)) {
            io.spicelabs.baharat.rpm.RpmPackage rpm = PackageReader.readRpm(in);
            assertThatThrownBy(rpm::payload)
                    .isInstanceOf(PackageException.class)
                    .satisfies(e -> assertThat(((PackageException) e).getFormat())
                            .isEqualTo(PackageFormat.RPM))
                    .hasMessageContaining("Cannot stream payload without source path")
                    .hasMessageContaining("streamPayload")
                    .hasMessageContaining("Path");
        }

        // Via RpmReader.read(InputStream)
        try (InputStream in = Files.newInputStream(fixture)) {
            io.spicelabs.baharat.rpm.RpmPackage rpm = RpmReader.read(in);
            assertThatThrownBy(rpm::payload)
                    .isInstanceOf(PackageException.class)
                    .satisfies(e -> assertThat(((PackageException) e).getFormat())
                            .isEqualTo(PackageFormat.RPM))
                    .hasMessageContaining("Cannot stream payload without source path")
                    .hasMessageContaining("streamPayload")
                    .hasMessageContaining("Path");
        }
    }

    // ── T7 (R5): sourcePath() is null for stream reads ───────────────────────

    /**
     * Tests that {@code sourcePath()} is null for packages built from
     * InputStreams, through both public readers.
     *
     * <p>Why: R5 — the accessor must accurately reflect reality. Green today;
     * guards against a future half-fix that fabricates a path for stream reads.
     */
    @Test
    void streamReadSourcePathIsNull() throws Exception {
        Path fixture = TestFiles.getPath("v4/zstd-1.5.5-5.fc40.x86_64.rpm");
        try (InputStream in = Files.newInputStream(fixture)) {
            assertThat(PackageReader.readRpm(in).sourcePath()).isNull();
        }
        try (InputStream in = Files.newInputStream(fixture)) {
            assertThat(RpmReader.read(in).sourcePath()).isNull();
        }
    }
}
