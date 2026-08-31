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
import io.spicelabs.baharat.common.BudgetLimits;
import io.spicelabs.baharat.testutil.TarFixtures;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * DEB reader budget tests (Fresh Scent Phase 5, finding B2/B5, catalog §9).
 *
 * <p>Lives in package {@code deb} to use the package-private capped
 * {@code readFromStream(InputStream, String, BudgetLimits)} overload.
 */
class DebBudgetTest {

    private static byte[] controlTar() throws IOException {
        return TarFixtures.tar(List.of(TarFixtures.Entry.file("control",
                "Package: demo\nVersion: 1.0\nArchitecture: all\n")));
    }

    private static byte[] dataTar(List<TarFixtures.Entry> files) throws IOException {
        return TarFixtures.tar(files);
    }

    private static byte[] deb(byte[] controlTar, byte[] dataTar) throws IOException {
        return TarFixtures.ar(
                TarFixtures.ArMember.of("debian-binary", "2.0\n".getBytes()),
                TarFixtures.ArMember.of("control.tar", controlTar),
                TarFixtures.ArMember.of("data.tar", dataTar));
    }

    // Requirement: catalog §9 / finding B2 / plan Phase 5.1
    // Theory: read() decompresses the whole data.tar (headers + skips) with NO cap
    //         previously — a bomb-sized payload inflated unbounded. The cap must trip
    //         LOUDLY (IOException), never silently truncate.
    // Boundaries: cap < payload size (injected small cap).
    // Revert-check: removing CountedLimitedInputStream lets the 1 MB payload parse.
    @Test
    void dataTarDecompressionBombRejected() throws IOException, PackageException {
        byte[] data = dataTar(List.of(
                TarFixtures.Entry.file("usr/share/huge.bin", new byte[1024 * 1024])));
        byte[] pkg = deb(controlTar(), data);
        BudgetLimits tight = new BudgetLimits(100 * 1024, 10_000, 10 * 1024 * 1024);
        assertThatThrownBy(() -> DebReader.readFromStream(
                new ByteArrayInputStream(pkg), "bomb.deb", tight))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Decompressed data exceeds");
    }

    // Requirement: catalog §2 / finding B5 / plan Phase 5.2
    // Theory: the debian-binary member is capped (10 MiB default; small injected here).
    @Test
    void oversizedDebianBinaryRejected() throws IOException, PackageException {
        byte[] pkg = TarFixtures.ar(
                TarFixtures.ArMember.of("debian-binary",
                        ("2.0\n" + "x".repeat(64 * 1024)).getBytes()),
                TarFixtures.ArMember.of("control.tar", controlTar()),
                TarFixtures.ArMember.of("data.tar", dataTar(List.of())));
        BudgetLimits tight = new BudgetLimits(1024 * 1024, 10_000, 1024);
        assertThatThrownBy(() -> DebReader.readFromStream(
                new ByteArrayInputStream(pkg), "big-member.deb", tight))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Decompressed data exceeds");
    }

    // Requirement: catalog §9 / plan Phase 5.1
    // Theory: the ar member-count cap fails loud beyond the bound.
    @Test
    void memberCountCapEnforced() throws IOException, PackageException {
        List<TarFixtures.Entry> many = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            many.add(TarFixtures.Entry.file("f" + i, "x"));
        }
        byte[] pkg = deb(controlTar(), dataTar(many));
        BudgetLimits tight = new BudgetLimits(1024 * 1024, 3, 1024 * 1024);
        assertThatThrownBy(() -> DebReader.readFromStream(
                new ByteArrayInputStream(pkg), "many.deb", tight))
                .isInstanceOf(PackageException.class)
                .hasMessageContaining("maximum entry count");
    }

    // Requirement: positive control — a valid deb still parses under default budgets.
    @Test
    void validDebParsesUnderDefaultBudgets() throws IOException, PackageException {
        byte[] pkg = deb(controlTar(), dataTar(List.of(
                TarFixtures.Entry.file("usr/bin/demo", "#!/bin/sh\n"),
                TarFixtures.Entry.symlink("usr/bin/demo-link", "demo"))));
        DebPackage parsed = DebReader.readFromStream(new ByteArrayInputStream(pkg),
                "demo.deb", BudgetLimits.DEFAULT);
        assertThat(parsed.debMetadata().name()).isEqualTo("demo");
    }
}
