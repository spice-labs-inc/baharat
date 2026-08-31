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
package io.spicelabs.baharat.apk;

import io.spicelabs.baharat.PackageException;
import io.spicelabs.baharat.common.BudgetLimits;
import io.spicelabs.baharat.common.FileInfo;
import io.spicelabs.baharat.testutil.TarFixtures;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * APK reader budget, symlink-policy, and sparse-entry tests.
 */
class ApkBudgetTest {

    private static byte[] apk(List<TarFixtures.Entry> entries) throws IOException {
        List<TarFixtures.Entry> all = new ArrayList<>();
        all.add(TarFixtures.Entry.file(".PKGINFO", "pkgname = demo\npkgver = 1.0\n"));
        all.addAll(entries);
        return TarFixtures.gzipTar(all);
    }


    // Theory: read() inflates the whole tar with no cap — the injected cap must trip loud.
    @Test
    void decompressionBombRejected() throws IOException, PackageException {
        byte[] pkg = apk(List.of(
                TarFixtures.Entry.file("usr/share/huge.bin", new byte[1024 * 1024])));
        BudgetLimits tight = new BudgetLimits(100 * 1024, 10_000, 10 * 1024 * 1024);
        assertThatThrownBy(() -> ApkReader.readFromStream(
                new ByteArrayInputStream(pkg), "bomb.apk", tight))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Decompressed data exceeds");
    }


    @Test
    void entryCountCapEnforced() throws IOException, PackageException {
        List<TarFixtures.Entry> many = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            many.add(TarFixtures.Entry.file("f" + i, "x"));
        }
        byte[] pkg = apk(many);
        BudgetLimits tight = new BudgetLimits(1024 * 1024, 3, 1024 * 1024);
        assertThatThrownBy(() -> ApkReader.readFromStream(
                new ByteArrayInputStream(pkg), "many.apk", tight))
                .isInstanceOf(PackageException.class)
                .hasMessageContaining("maximum entry count");
    }

    // Requirement: (uniform symlink policy)
    // Theory: a dangerous symlink (target escapes root) is SKIPPED in read() — never
    //         silently stored with an empty target (the previous behavior).
    //         An ABSOLUTE target is valid metadata and must be preserved (D5 decision).
    @Test
    void dangerousSymlinkSkippedAbsoluteAllowed() throws IOException, PackageException {
        byte[] pkg = apk(List.of(
                TarFixtures.Entry.symlink("etc/evil", "../../etc/passwd"),
                TarFixtures.Entry.symlink("etc/abs", "/etc/issue"),
                TarFixtures.Entry.file("usr/bin/ok", "ok")));
        ApkPackage parsed = ApkReader.readFromStream(new ByteArrayInputStream(pkg),
                "links.apk", BudgetLimits.DEFAULT);
        List<FileInfo> files = parsed.apkMetadata().files();
        assertThat(files.stream().anyMatch(f -> f.path().equals("etc/evil"))).isFalse();
        Optional<FileInfo> abs = files.stream()
                .filter(f -> f.path().equals("etc/abs"))
                .findFirst();
        assertThat(abs).isPresent();
        assertThat(abs.get().linkTarget()).contains("/etc/issue");
    }


    // Theory: GNU sparse entries carry an attacker-controlled LOGICAL size (here 1 MiB
    //         for 8 real bytes); readers must expose the REAL size.
    // Revert-check: reverting to entry.getSize() reports 1 MiB for an 8-byte member.
    @Test
    void sparseEntryExposesRealSize() throws IOException, PackageException {
        byte[] sparseTar = TarFixtures.gnuSparseTar();
        // Splice the sparse member after a .PKGINFO tar: TarArchiveInputStream stops at
        // the end-of-archive marker, so strip the preamble's end blocks.
        byte[] preamble = TarFixtures.tar(List.of(
                TarFixtures.Entry.file(".PKGINFO", "pkgname = demo\npkgver = 1.0\n")));
        java.io.ByteArrayOutputStream combined = new java.io.ByteArrayOutputStream();
        combined.write(preamble, 0, preamble.length - 1024);
        combined.write(sparseTar);
        byte[] pkg = new java.io.ByteArrayOutputStream() {{
            try (var gz = new java.util.zip.GZIPOutputStream(this)) {
                gz.write(combined.toByteArray());
            }
        }}.toByteArray();

        ApkPackage parsed = ApkReader.readFromStream(new ByteArrayInputStream(pkg),
                "sparse.apk", BudgetLimits.DEFAULT);
        Optional<FileInfo> sparse = parsed.apkMetadata().files().stream()
                .filter(f -> f.path().equals("sparse.bin"))
                .findFirst();
        assertThat(sparse).isPresent();
        // commons-compress delivers the LOGICAL size zero-filled for sparse entries and
        // reports it via getRealSize(); the raw header size (8) would under-report what
        // the stream delivers. Readers must use getRealSize().
        assertThat(sparse.get().size()).isEqualTo(1024L * 1024L);
    }

    // Requirement: positive control
    @Test
    void validApkParsesUnderDefaultBudgets() throws IOException, PackageException {
        byte[] pkg = apk(List.of(TarFixtures.Entry.file("usr/bin/demo", "#!/bin/sh\n")));
        ApkPackage parsed = ApkReader.readFromStream(new ByteArrayInputStream(pkg),
                "demo.apk", BudgetLimits.DEFAULT);
        assertThat(parsed.apkMetadata().name()).isEqualTo("demo");
    }
}
