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
package io.spicelabs.baharat.rpm.security;

import io.spicelabs.baharat.rpm.extract.Extractor;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Extractor symlink write-through tests (Fresh Scent Phase 6, finding B13, decision D7).
 *
 * <p>The deterministic exploit: payload order {@code [symlink "outside" -> <canaryDir>,
 * file "outside/secret.txt"]} with {@code overwrite=true} previously DELETED and replaced
 * the canary file OUTSIDE the target directory. The NOFOLLOW parent-chain verification
 * must refuse the write and leave the canary untouched.
 */
class ExtractorSymlinkWriteThroughTest {

    // Requirement: catalog §6/§7 / finding B13 / plan Phase 6.2
    // Theory: every write/delete must verify that the parent chain of the target path
    //         contains no symlink components (NOFOLLOW walk) and that the real path
    //         resolves inside the target directory.
    // Revert-check: removing verifySafeParents makes this test modify the canary file.
    @Test
    void symlinkThenFileCannotWriteOutsideTarget(@TempDir Path dir)
            throws IOException, io.spicelabs.baharat.rpm.exception.FormatException {
        Path canaryDir = dir.resolve("canary");
        Files.createDirectories(canaryDir);
        Path canaryFile = canaryDir.resolve("secret.txt");
        Files.writeString(canaryFile, "ORIGINAL-CANARY", StandardCharsets.UTF_8);

        Path targetDir = dir.resolve("target");
        Files.createDirectories(targetDir);

        byte[] rpm = SyntheticRpmBuilder.rpmWithPayload(List.of(
                new SyntheticRpmBuilder.CpioEntrySpec("outside", canaryDir.toString(), 0120777),
                new SyntheticRpmBuilder.CpioEntrySpec("outside/secret.txt", "PWNED", 0100644)));
        Path rpmFile = dir.resolve("evil.rpm");
        Files.write(rpmFile, rpm);

        Extractor.ExtractionResult result = Extractor.builder()
                .overwrite(true)
                .createSymlinks(true)
                .build()
                .extractTo(rpmFile, targetDir);

        // The canary must be untouched — the write-through is refused.
        assertThat(Files.readString(canaryFile, StandardCharsets.UTF_8))
                .isEqualTo("ORIGINAL-CANARY");
        assertThat(result.errors()).isNotEmpty();
        assertThat(result.errors().stream()
                .anyMatch(e -> e.message().contains("symlink"))).isTrue();
    }

    // Requirement: positive control — a benign payload still extracts cleanly.
    @Test
    void benignPayloadExtractsNormally(@TempDir Path dir)
            throws IOException, io.spicelabs.baharat.rpm.exception.FormatException {
        Path targetDir = dir.resolve("target");
        Files.createDirectories(targetDir);

        byte[] rpm = SyntheticRpmBuilder.rpmWithPayload(List.of(
                new SyntheticRpmBuilder.CpioEntrySpec("usr/share/hello.txt", "hello", 0100644)));
        Path rpmFile = dir.resolve("good.rpm");
        Files.write(rpmFile, rpm);

        Extractor.ExtractionResult result = Extractor.builder()
                .overwrite(true)
                .build()
                .extractTo(rpmFile, targetDir);

        assertThat(result.isSuccessful()).isTrue();
        assertThat(Files.readString(targetDir.resolve("usr/share/hello.txt")))
                .isEqualTo("hello");
    }

    // Requirement: finding B13 — a DIRECTORY entry whose path passes through a previously
    //         created symlink must also be refused (createDirectories follows links).
    @Test
    void directoryThroughSymlinkRefused(@TempDir Path dir)
            throws IOException, io.spicelabs.baharat.rpm.exception.FormatException {
        Path canaryDir = dir.resolve("canary");
        Files.createDirectories(canaryDir);
        Path canaryFile = canaryDir.resolve("keep.txt");
        Files.writeString(canaryFile, "KEEP", StandardCharsets.UTF_8);

        Path targetDir = dir.resolve("target");
        Files.createDirectories(targetDir);

        byte[] rpm = SyntheticRpmBuilder.rpmWithPayload(List.of(
                new SyntheticRpmBuilder.CpioEntrySpec("link", canaryDir.toString(), 0120777),
                new SyntheticRpmBuilder.CpioEntrySpec("link/subdir", "", 0040755),
                new SyntheticRpmBuilder.CpioEntrySpec("link/subdir/evil.txt", "NOPE", 0100644)));
        Path rpmFile = dir.resolve("evil2.rpm");
        Files.write(rpmFile, rpm);

        Extractor.ExtractionResult result = Extractor.builder()
                .overwrite(true)
                .build()
                .extractTo(rpmFile, targetDir);

        assertThat(Files.exists(canaryDir.resolve("subdir"))).isFalse();
        assertThat(result.errors()).isNotEmpty();
    }

    @Provide
    Arbitrary<List<SyntheticRpmBuilder.CpioEntrySpec>> entryPermutations() {
        List<SyntheticRpmBuilder.CpioEntrySpec> pool = List.of(
                new SyntheticRpmBuilder.CpioEntrySpec("link", "/tmp/canary-holder", 0120777),
                new SyntheticRpmBuilder.CpioEntrySpec("link/pwn.txt", "PWNED", 0100644),
                new SyntheticRpmBuilder.CpioEntrySpec("dir", "", 0040755),
                new SyntheticRpmBuilder.CpioEntrySpec("dir/file.txt", "safe", 0100644),
                new SyntheticRpmBuilder.CpioEntrySpec("benign.txt", "ok", 0100644));
        return Arbitraries.integers().between(2, pool.size())
                .flatMap(n -> Arbitraries.shuffle(pool).map(all -> all.subList(0, n)));
    }

    // Requirement: catalog §6 / finding B13 / plan Phase 7 (entry-order property)
    // Theory: NO ordering of symlink/file/dir entries may let the extractor write through a
    //         symlink — the canary file outside the target must survive every permutation.
    // Revert-check: removing verifySafeParents lets orderings where the symlink precedes the
    //               file clobber the canary.
    @Property(tries = 100)
    void noOrderingWritesThroughSymlinks(
            @ForAll("entryPermutations") List<SyntheticRpmBuilder.CpioEntrySpec> order)
            throws Exception {
        Path dir = Files.createTempDirectory("extractor-order");
        try {
            assertNoOrderingWritesThroughSymlinks(order, dir);
        } finally {
            deleteRecursively(dir);
        }
    }

    private void assertNoOrderingWritesThroughSymlinks(
            List<SyntheticRpmBuilder.CpioEntrySpec> order, Path dir) throws Exception {
        Path canaryDir = dir.resolve("canary");
        Files.createDirectories(canaryDir);
        Path canaryFile = canaryDir.resolve("pwn.txt");
        Files.writeString(canaryFile, "CANARY", StandardCharsets.UTF_8);

        // Point the hostile symlink at the canary directory.
        List<SyntheticRpmBuilder.CpioEntrySpec> entries = new ArrayList<>();
        for (SyntheticRpmBuilder.CpioEntrySpec spec : order) {
            entries.add(spec.name().equals("link")
                    ? new SyntheticRpmBuilder.CpioEntrySpec("link", canaryDir.toString(), 0120777)
                    : spec);
        }

        Path targetDir = dir.resolve("target");
        Files.createDirectories(targetDir);
        byte[] rpm = SyntheticRpmBuilder.rpmWithPayload(entries);
        Path rpmFile = dir.resolve("order.rpm");
        Files.write(rpmFile, rpm);

        try {
            Extractor.builder().overwrite(true).createSymlinks(true).build()
                    .extractTo(rpmFile, targetDir);
        } catch (RuntimeException e) {
            // The extractor may surface loud refusals; the invariant is the canary.
        }

        assertThat(Files.readString(canaryFile, StandardCharsets.UTF_8))
                .isEqualTo("CANARY");
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best effort cleanup
                }
            });
        }
    }
}
