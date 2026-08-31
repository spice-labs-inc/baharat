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
package io.spicelabs.baharat.bomb;

import io.spicelabs.baharat.PackageException;
import io.spicelabs.baharat.PackageReader;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Fuzz harness for {@link PackageReader} (Fresh Scent Phase 7, catalog §13).
 *
 * <p>Invariant: arbitrary bytes through the public read path must TERMINATE and throw only
 * the documented checked types ({@code PackageException}/{@code IOException}). The harness
 * deliberately does NOT catch {@code RuntimeException} (the §13 self-inversion trap) — an
 * unchecked escape or an {@code Error} (StackOverflowError/OOM) fails the property.
 */
class PackageReaderFuzzBombTest {

    private static final Path TEMP_DIR = createTempDir();

    private static Path createTempDir() {
        try {
            return Files.createTempDirectory("baharat-fuzz");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Provide
    Arbitrary<byte[]> hostileBytes() {
        // Skew toward archive magic prefixes + binary noise so the detectors actually
        // commit to a parser instead of rejecting on magic.
        Arbitrary<byte[]> magic = Arbitraries.of(
                new byte[]{(byte) 0xED, (byte) 0xAB, (byte) 0xEE, (byte) 0xDB}, // rpm
                "!<arch>\n".getBytes(),                                         // deb
                new byte[]{0x1F, (byte) 0x8B, 0x08},                             // gzip
                new byte[]{'B', 'Z', 'h', '9'},                                  // bzip2
                new byte[]{(byte) 0xFD, '7', 'z', 'X', 'Z', 0x00},               // xz
                new byte[]{(byte) 0x28, (byte) 0xB5, 0x2F, (byte) 0xFD});        // zstd
        Arbitrary<byte[]> tail = Arbitraries.bytes().array(byte[].class).ofMaxSize(2048);
        return magic.flatMap(m -> tail.map(t -> concat(m, t)));
    }

    // Requirement: catalog §13 / plan Phase 7
    // Theory: no input may hang the reader, escape an unchecked exception, or die with an
    //         Error. Only PackageException/IOException subclasses are tolerated.
    // Revert-check: any unchecked escape reintroduced in the readers fails the property.
    @Property(tries = 200)
    void arbitraryBytesNeverHangOrEscapeUnchecked(@ForAll("hostileBytes") byte[] data)
            throws IOException {
        Path f = TEMP_DIR.resolve("fuzz-" + System.nanoTime() + ".bin");
        Files.write(f, data);
        try {
            PackageReader.read(f);
        } catch (PackageException | IOException e) {
            // documented checked rejection
        }
    }

    // Requirement: catalog §13 / plan Phase 7 (truncation fuzz)
    // Theory: every PREFIX of a hostile sample must also terminate with checked types only
    //         (truncation is the classic silent-wrong-data trigger).
    @Property(tries = 100)
    void arbitraryPrefixesNeverHangOrEscapeUnchecked(
            @ForAll("hostileBytes") byte[] data,
            @ForAll @net.jqwik.api.constraints.IntRange(min = 0, max = 2048) int cut)
            throws IOException {
        Path f = TEMP_DIR.resolve("fuzz-" + System.nanoTime() + ".bin");
        byte[] prefix = new byte[Math.min(cut, data.length)];
        System.arraycopy(data, 0, prefix, 0, prefix.length);
        Files.write(f, prefix);
        try {
            PackageReader.read(f);
        } catch (PackageException | IOException e) {
            // documented checked rejection
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
