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
package io.spicelabs.baharat.openbsd;

import io.spicelabs.baharat.PackageException;
import io.spicelabs.baharat.common.BudgetLimits;
import io.spicelabs.baharat.testutil.TarFixtures;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OpenBSD reader budget tests.
 */
class OpenBsdBudgetTest {

    private static byte[] pkg(List<TarFixtures.Entry> extra) throws IOException {
        java.util.List<TarFixtures.Entry> all = new java.util.ArrayList<>();
        all.add(TarFixtures.Entry.file("+CONTENTS", "@name demo-1.0\n"));
        all.add(TarFixtures.Entry.file("+DESC", "demo package"));
        all.addAll(extra);
        return TarFixtures.gzipTar(all);
    }


    // Theory: the +DESC member read is capped — oversized members fail loud.
    @Test
    void oversizedDescRejected() throws IOException, PackageException {
        byte[] data = TarFixtures.gzipTar(List.of(
                TarFixtures.Entry.file("+CONTENTS", "@name demo-1.0\n"),
                TarFixtures.Entry.file("+DESC", "x".repeat(64 * 1024))));
        BudgetLimits tight = new BudgetLimits(1024 * 1024, 10_000, 1024);
        assertThatThrownBy(() -> OpenBsdReader.readFromStream(
                new ByteArrayInputStream(data), "big-desc.tgz", tight))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Decompressed data exceeds");
    }


    @Test
    void entryCountCapEnforced() throws IOException, PackageException {
        java.util.List<TarFixtures.Entry> many = new java.util.ArrayList<>();
        many.add(TarFixtures.Entry.file("+CONTENTS", "@name demo-1.0\n"));
        many.add(TarFixtures.Entry.file("+DESC", "demo package"));
        for (int i = 0; i < 10; i++) {
            many.add(TarFixtures.Entry.file("f" + i, "x"));
        }
        byte[] data = TarFixtures.gzipTar(many);
        BudgetLimits tight = new BudgetLimits(1024 * 1024, 3, 1024 * 1024);
        assertThatThrownBy(() -> OpenBsdReader.readFromStream(
                new ByteArrayInputStream(data), "many.tgz", tight))
                .isInstanceOf(PackageException.class)
                .hasMessageContaining("maximum entry count");
    }

    // Requirement: positive control
    @Test
    void validOpenBsdParsesUnderDefaultBudgets() throws IOException, PackageException {
        byte[] data = pkg(List.of(TarFixtures.Entry.file("usr/bin/demo", "#!/bin/sh\n")));
        OpenBsdPackage parsed = OpenBsdReader.readFromStream(new ByteArrayInputStream(data),
                "demo.tgz", BudgetLimits.DEFAULT);
        assertThat(parsed.openBsdMetadata().name()).isEqualTo("demo");
    }
}
