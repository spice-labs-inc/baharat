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
package io.spicelabs.baharat.freebsd;

import io.spicelabs.baharat.PackageException;
import io.spicelabs.baharat.common.BudgetLimits;
import io.spicelabs.baharat.testutil.TarFixtures;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FreeBSD reader budget and JSON-depth tests.
 */
class FreeBsdBudgetTest {


    // Theory: the +MANIFEST member read is capped — an oversized member fails loud.
    @Test
    void oversizedManifestRejected() throws IOException, PackageException {
        byte[] pkg = TarFixtures.tar(List.of(
                TarFixtures.Entry.file("+MANIFEST", "x".repeat(64 * 1024))));
        BudgetLimits tight = new BudgetLimits(1024 * 1024, 10_000, 1024);
        assertThatThrownBy(() -> FreeBsdReader.readFromStream(
                new ByteArrayInputStream(pkg), "big-manifest.pkg", tight))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Decompressed data exceeds");
    }

    // Requirement: (JSON depth guard)
    // Theory: a deep-nesting +MANIFEST must be rejected loudly — GSON recursion has no
    //         depth limit and would otherwise die with an uncatchable StackOverflowError.
    @Test
    void manifestDepthBombRejected() throws IOException, PackageException {
        String bomb = "{\"name\":" + "[".repeat(600) + "0" + "]".repeat(600) + "}";
        byte[] pkg = TarFixtures.tar(List.of(TarFixtures.Entry.file("+MANIFEST", bomb)));
        assertThatThrownBy(() -> FreeBsdReader.readFromStream(
                new ByteArrayInputStream(pkg), "bomb.pkg", BudgetLimits.DEFAULT))
                .isInstanceOf(PackageException.class)
                .hasMessageContaining("nesting depth");
    }


    @Test
    void entryCountCapEnforced() throws IOException, PackageException {
        // +MANIFEST placed AFTER the files: the entry cap must fire before it is reached
        // (readFromStream breaks as soon as +MANIFEST is found, so a leading manifest
        // would mask the cap).
        List<TarFixtures.Entry> many = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            many.add(TarFixtures.Entry.file("f" + i, "x"));
        }
        many.add(TarFixtures.Entry.file("+MANIFEST", "{\"name\":\"demo\",\"version\":\"1.0\"}"));
        byte[] pkg = TarFixtures.tar(many);
        BudgetLimits tight = new BudgetLimits(1024 * 1024, 3, 1024 * 1024);
        assertThatThrownBy(() -> FreeBsdReader.readFromStream(
                new ByteArrayInputStream(pkg), "many.pkg", tight))
                .isInstanceOf(PackageException.class)
                .hasMessageContaining("maximum entry count");
    }

    // Requirement: positive control
    @Test
    void validFreeBsdParsesUnderDefaultBudgets() throws IOException, PackageException {
        byte[] pkg = TarFixtures.tar(List.of(
                TarFixtures.Entry.file("+MANIFEST", "{\"name\":\"demo\",\"version\":\"1.0\"}")));
        FreeBsdPackage parsed = FreeBsdReader.readFromStream(new ByteArrayInputStream(pkg),
                "demo.pkg", BudgetLimits.DEFAULT);
        assertThat(parsed.freeBsdMetadata().name()).isEqualTo("demo");
    }
}
