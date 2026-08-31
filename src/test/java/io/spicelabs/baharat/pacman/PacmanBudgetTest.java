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
package io.spicelabs.baharat.pacman;

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
 * Pacman reader budget and symlink-policy tests.
 */
class PacmanBudgetTest {

    private static byte[] pkg(List<TarFixtures.Entry> extra) throws IOException {
        List<TarFixtures.Entry> all = new ArrayList<>();
        all.add(TarFixtures.Entry.file(".PKGINFO", "pkgname = demo\npkgver = 1.0\n"));
        all.addAll(extra);
        return TarFixtures.tar(all);
    }


    @Test
    void entryCountCapEnforced() throws IOException, PackageException {
        List<TarFixtures.Entry> many = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            many.add(TarFixtures.Entry.file("f" + i, "x"));
        }
        byte[] data = pkg(many);
        BudgetLimits tight = new BudgetLimits(1024 * 1024, 3, 1024 * 1024);
        assertThatThrownBy(() -> PacmanReader.readFromStream(
                new ByteArrayInputStream(data), "many.pkg.tar", tight))
                .isInstanceOf(PackageException.class)
                .hasMessageContaining("maximum entry count");
    }

    // Requirement: (uniform symlink policy)
    // Theory: dangerous symlink targets are SKIPPED (previously an EMPTY target was
    //         stored silently); absolute targets are valid metadata (D5 decision).
    @Test
    void dangerousSymlinkSkippedAbsoluteAllowed() throws IOException, PackageException {
        byte[] data = pkg(List.of(
                TarFixtures.Entry.symlink("etc/evil", "../../etc/passwd"),
                TarFixtures.Entry.symlink("etc/abs", "/etc/issue"),
                TarFixtures.Entry.file("usr/bin/ok", "ok")));
        PacmanPackage parsed = PacmanReader.readFromStream(new ByteArrayInputStream(data),
                "links.pkg.tar", BudgetLimits.DEFAULT);
        List<FileInfo> files = parsed.pacmanMetadata().files();
        assertThat(files.stream().anyMatch(f -> f.path().equals("etc/evil"))).isFalse();
        Optional<FileInfo> abs = files.stream()
                .filter(f -> f.path().equals("etc/abs"))
                .findFirst();
        assertThat(abs).isPresent();
        assertThat(abs.get().linkTarget()).contains("/etc/issue");
    }

    // Requirement: positive control
    @Test
    void validPacmanParsesUnderDefaultBudgets() throws IOException, PackageException {
        byte[] data = pkg(List.of(TarFixtures.Entry.file("usr/bin/demo", "#!/bin/sh\n")));
        PacmanPackage parsed = PacmanReader.readFromStream(new ByteArrayInputStream(data),
                "demo.pkg.tar", BudgetLimits.DEFAULT);
        assertThat(parsed.pacmanMetadata().name()).isEqualTo("demo");
    }
}
