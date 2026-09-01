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

import io.spicelabs.baharat.apk.ApkReader;
import io.spicelabs.baharat.deb.DebReader;
import io.spicelabs.baharat.freebsd.FreeBsdReader;
import io.spicelabs.baharat.openbsd.OpenBsdReader;
import io.spicelabs.baharat.pacman.PacmanReader;
import io.spicelabs.baharat.rpm.RpmReader;
import io.spicelabs.baharat.testdata.PackageTestFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

/**
 * Payload-restriction and checked-exception contract tests (enhancement plan,
 * requirements R4 and R6; tests T8, T9, T10).
 *
 * <p>R6 policy (approved): the library's exception hierarchy contains no
 * unchecked exception class except the single documented {@code
 * BaharatStreamException} at the lazy stream boundary (Java Stream APIs cannot
 * propagate checked exceptions), which always carries a checked cause. Every
 * eager public entry point throws only checked exceptions on hostile input.
 * Standard Java precondition guards (IllegalArgumentException on programmer
 * misuse) are unchanged.
 */
class PayloadRestrictionContractTest {

    /** Reader entry points that may throw checked exceptions (for T9). */
    @FunctionalInterface
    private interface ThrowingReader {
        Object read(Path path) throws Exception;
    }

    /** One known-good fixture per format, used by T8. */
    private static final Map<PackageFormat, String> FIXTURES = Map.of(
            PackageFormat.RPM, "rpms/v4/zstd-1.5.5-5.fc40.x86_64.rpm",
            PackageFormat.DEB, "debs/acl_2.3.2-1build1.1_amd64.deb",
            PackageFormat.PACMAN, "pacman/7zip-25.01-1-x86_64.pkg.tar.zst",
            PackageFormat.APK, "apks/acl-2.3.2-r0.apk",
            PackageFormat.FREEBSD_PKG, "freebsd/bzip2-1.0.8_1.pkg",
            PackageFormat.OPENBSD_PKG, "openbsd/a2ps-4.15.6.tgz"
    );

    /** Format-specific eager readers keyed by resource directory, used by T9. */
    private static final Map<String, ThrowingReader> EAGER_READERS = Map.of(
            PackageTestFiles.RPMS, RpmReader::read,
            PackageTestFiles.DEBS, DebReader::read,
            PackageTestFiles.PACMAN, PacmanReader::read,
            PackageTestFiles.APKS, ApkReader::read,
            PackageTestFiles.FREEBSD, FreeBsdReader::read,
            PackageTestFiles.OPENBSD, OpenBsdReader::read
    );

    /** Format-keyed resource directory, used by T9/T10 to select fixtures. */
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

    private static final int[] CUTS = {0, 16, 128, 50, 90};

    /** Assert an exception is checked: an Exception that is NOT a RuntimeException. */
    private static void assertChecked(Throwable t, String context) {
        assertThat(t instanceof RuntimeException)
                .as("unchecked RuntimeException escape in " + context)
                .isFalse();
        assertThat(t)
                .as("non-Exception escape in " + context)
                .isInstanceOf(Exception.class);
    }

    /** Enumerates package-like fixture files under a format's resource directory. */
    private static List<Path> fixtureFiles(PackageFormat format) throws IOException {
        List<Path> files = new ArrayList<>();
        for (String ext : EXTENSIONS) {
            files.addAll(PackageTestFiles.getAllFiles(FORMAT_DIRS.get(format), ext));
        }
        return files;
    }

    /** Writes a truncated prefix of a fixture, preserving the original name. */
    private static Path truncatedCopy(Path fixture, int cut, Path tempDir) throws IOException {
        byte[] full = Files.readAllBytes(fixture);
        int length = switch (cut) {
            case 0 -> 0;
            case 16 -> Math.min(16, full.length);
            case 128 -> Math.min(128, full.length);
            default -> (int) ((long) full.length * cut / 100);
        };
        Path out = tempDir.resolve("trunc-" + cut + "-" + fixture.getFileName());
        Files.write(out, java.util.Arrays.copyOf(full, length));
        return out;
    }

    // ── T8 (R4, D3): restriction message consistent across all six formats ──

    /**
     * Tests that every package class, when built from an InputStream (no source
     * path), throws a {@code PackageException} whose message keeps the historical
     * prefix AND names a workable alternative — consistently across all six
     * formats.
     *
     * <p>Why: R4 + approved D3 — the improved message is applied to all six
     * formats (they shared the identical bare string before). Red today for all
     * six (no guidance in any message).
     *
     * <p>LLM note: asserts prefix + guidance substrings ("streamPayload", "Path"),
     * never the exact full string; also asserts the format context is carried.
     */
    @Test
    void streamPayloadRestrictionMessageConsistentAcrossFormats() throws Exception {
        for (Map.Entry<PackageFormat, String> e : FIXTURES.entrySet()) {
            PackageFormat format = e.getKey();
            String resource = e.getValue();
            try (InputStream in = PackageTestFiles.class.getClassLoader()
                    .getResourceAsStream(resource)) {
                assertThat(in).as("fixture on classpath: %s", resource).isNotNull();
                Package pkg = PackageReader.read(in, format, resource);
                assertThatThrownBy(pkg::payload)
                        .as("payload() restriction for %s", format)
                        .isInstanceOf(PackageException.class)
                        .satisfies(ex -> assertThat(((PackageException) ex).getFormat())
                                .isEqualTo(format))
                        .hasMessageContaining("Cannot stream payload without source path")
                        .hasMessageContaining("streamPayload")
                        .hasMessageContaining("Path");
            }
        }
    }

    // ── T9 (R6): eager entry points never leak unchecked exceptions ─────────

    /**
     * Tests that eager public entry points throw only checked exceptions on
     * hostile (truncated) input: {@code PackageReader.read(Path)},
     * {@code PackageReader.readMetadata(Path)}, and each format's eager reader.
     *
     * <p>Why: R6 guard rail — hostile input through eager APIs must surface as
     * {@code PackageException}/{@code FormatException}/{@code IOException}, never
     * a bare {@code RuntimeException} or out-of-bounds escape. Green today; any
     * future eager-path change that leaks an unchecked escape trips it.
     *
     * <p>Entry points exercised (all eager): {@code PackageReader.read(Path)},
     * {@code PackageReader.read(Path, PackageFormat)},
     * {@code PackageReader.read(InputStream, PackageFormat, String)},
     * {@code PackageReader.readMetadata(Path)},
     * {@code PackageReader.readMetadata(InputStream, String)},
     * {@code PackageReader.detect(Path)}, {@code PackageReader.isPackage(Path)},
     * each format reader's {@code read(Path)}, and (for RPM)
     * {@code RpmReader.isRpm(Path)} and {@code RpmReader.openPayload(Path)}.
     *
     * <p>Sweep design (runtime-bounded): the primary entry point
     * {@code PackageReader.read(Path)} and the cheap non-parsing entry points
     * run over the FULL corpus at ALL five cuts (0, 16, 128, 50%, 90%). The
     * remaining entry points are redundant parse paths (they reach the same
     * parsers); to stay well under the 30s per-method timeout of the bomb fork
     * (zstd/xz native stream init costs ~10ms per parse call), they run on a
     * bounded sample — the first 10 fixtures per format at the 16-byte cut,
     * which is the most hostile header-truncation class.
     *
     * <p>LLM note: the truncation sweep is the hostile-input generator; the
     * assertion is purely about exception type, not messages. Uses the real
     * corpus (no synthetic bytes). Cuts that happen to parse cleanly are fine —
     * the assertion only constrains exceptions that DO occur.
     */
    @Test
    @org.junit.jupiter.api.Timeout(120)
    void eagerEntryPointsNeverLeakUncheckedExceptions(@TempDir Path tempDir) throws Exception {
        for (Map.Entry<PackageFormat, String> dir : FORMAT_DIRS.entrySet()) {
            PackageFormat format = dir.getKey();
            ThrowingReader eagerReader = EAGER_READERS.get(dir.getValue());
            List<Path> fixtures = fixtureFilesFor(dir.getValue());
            // Bounded sample for the redundant breadth entry points.
            List<Path> breadthSample = fixtures.stream().limit(10).toList();

            for (Path fixture : fixtures) {
                for (int cut : CUTS) {
                    Path truncated = truncatedCopy(fixture, cut, tempDir);
                    String context = fixture.getFileName() + " cut=" + cut;

                    // Primary eager entry point — every cut, full corpus (covers
                    // header truncation AND near-complete-file tail corruption).
                    try {
                        PackageReader.read(truncated);
                    } catch (Exception ex) {
                        assertChecked(ex, "PackageReader.read " + context);
                    }

                    // Cheap, non-parsing entry points — every cut, full corpus.
                    try {
                        PackageReader.detect(truncated);
                    } catch (Exception ex) {
                        assertChecked(ex, "PackageReader.detect " + context);
                    }
                    PackageReader.isPackage(truncated); // never throws; must not escape unchecked
                    if (format == PackageFormat.RPM) {
                        RpmReader.isRpm(truncated); // never throws; must not escape unchecked
                    }
                }
            }

            // Breadth: the remaining eager entry points on the sampled fixtures at
            // the most hostile cut (16-byte header truncation).
            for (Path fixture : breadthSample) {
                Path truncated = truncatedCopy(fixture, 16, tempDir);
                byte[] bytes = Files.readAllBytes(truncated);
                String context = fixture.getFileName() + " cut=16";

                try {
                    PackageReader.readMetadata(truncated);
                } catch (Exception ex) {
                    assertChecked(ex, "PackageReader.readMetadata " + context);
                }
                try {
                    eagerReader.read(truncated);
                } catch (Exception ex) {
                    assertChecked(ex, "format reader " + context);
                }
                try {
                    PackageReader.read(new java.io.ByteArrayInputStream(bytes), format,
                            truncated.getFileName().toString());
                } catch (Exception ex) {
                    assertChecked(ex, "PackageReader.read(stream, format) " + context);
                }
                try {
                    PackageReader.read(truncated, format);
                } catch (Exception ex) {
                    assertChecked(ex, "PackageReader.read(path, format) " + context);
                }
                try {
                    PackageReader.readMetadata(new java.io.ByteArrayInputStream(bytes),
                            truncated.getFileName().toString());
                } catch (Exception ex) {
                    assertChecked(ex, "PackageReader.readMetadata(stream) " + context);
                }
                if (format == PackageFormat.RPM) {
                    io.spicelabs.baharat.rpm.payload.PayloadReader pr = null;
                    try {
                        pr = RpmReader.openPayload(truncated);
                    } catch (Exception ex) {
                        assertChecked(ex, "RpmReader.openPayload " + context);
                    } finally {
                        if (pr != null) {
                            pr.close();
                        }
                    }
                }
            }
        }
    }

    /** Fixture files under a resource directory (extension-based selection). */
    private static List<Path> fixtureFilesFor(String resourceDir) throws IOException {
        List<Path> files = new ArrayList<>();
        for (String ext : EXTENSIONS) {
            files.addAll(PackageTestFiles.getAllFiles(resourceDir, ext));
        }
        return files;
    }

    // ── T10 (R6): stream-boundary wrapper carries a checked cause ────────────

    /**
     * Tests the lazy stream boundary contract: construction-time failures of
     * {@code PackageReader.streamPayload(Path, format)} are checked exceptions
     * (declared by the method signature); mid-stream consumption failures are
     * {@code BaharatStreamException} with a non-null checked cause
     * ({@code PackageException}/{@code FormatException}/{@code IOException} or
     * subclass) — never a bare unchecked exception.
     *
     * <p>Why: R6 — the documented boundary design: Java Streams cannot propagate
     * checked exceptions, so mid-stream corruption surfaces as the wrapper that
     * carries the recoverable checked cause. Complements the existing
     * {@code StreamBoundaryAndCriticalTagsTest} (which asserts the type for two
     * RPM cases) by asserting the cause contract across the whole corpus.
     *
     * <p>LLM note: conditional property — cuts that parse cleanly to EOF are
     * fine; failures must fail the documented way. No assertion on which cuts
     * fail, so decompressor EOF behavior (e.g., gzip ending silently on some
     * cuts, which may skip the wrapper branch for gzip-family formats on some
     * cuts) does not flake the test. The corpus-wide counter (below) guarantees
     * the branch fires at least once, so the test cannot pass vacuously.
     */
    @Test
    void streamBoundaryWrapperCarriesCheckedCause(@TempDir Path tempDir) throws Exception {
        int midStreamFailures = 0;
        for (Map.Entry<PackageFormat, String> dir : FORMAT_DIRS.entrySet()) {
            PackageFormat format = dir.getKey();
            for (Path fixture : fixtureFilesFor(dir.getValue())) {
                for (int cut : CUTS) {
                    Path truncated = truncatedCopy(fixture, cut, tempDir);
                    String context = format + " " + fixture.getFileName() + " cut=" + cut;

                    Stream<PackageEntry> stream;
                    try {
                        stream = PackageReader.streamPayload(truncated, format);
                    } catch (RuntimeException e) {
                        fail("unchecked exception from streamPayload construction in " + context
                                + ": " + e.getClass().getName() + ": " + e.getMessage());
                        return;
                    } catch (Exception e) {
                        // Construction-time checked failure (PackageException /
                        // IOException) is legal and declared by the signature.
                        assertChecked(e, "streamPayload construction " + context);
                        continue;
                    }

                    try (var entries = stream) {
                        try {
                            entries.toList();
                        } catch (BaharatStreamException e) {
                            // Mid-stream failure: documented wrapper with checked cause.
                            midStreamFailures++;
                            assertThat(e.getCause())
                                    .as("cause of BaharatStreamException " + context)
                                    .isNotNull();
                            assertChecked(e.getCause(), "BaharatStreamException cause " + context);
                        } catch (RuntimeException e) {
                            fail("bare unchecked exception from stream consumption in " + context
                                    + ": " + e.getClass().getName() + ": " + e.getMessage());
                        } catch (Exception e) {
                            // A checked exception escaping CONSUMPTION is not declared by
                            // any Stream method — a contract gap to investigate, not accept.
                            fail("checked exception escaped stream consumption instead of "
                                    + "BaharatStreamException in " + context + ": " + e);
                        }
                    }
                }
            }
        }
        // Guard against a trivial pass: the mid-stream branch must actually fire on
        // the corpus (mid-file truncation of real payloads), or this test would not
        // verify anything. RPM cpio payloads truncated at 50% deterministically fail
        // mid-stream, so at least one hit is expected.
        assertThat(midStreamFailures)
                .as("mid-stream BaharatStreamException occurrences across the corpus")
                .isGreaterThan(0);
    }
}
