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

import io.spicelabs.baharat.BaharatStreamException;
import io.spicelabs.baharat.rpm.exception.InvalidFormatException;
import io.spicelabs.baharat.rpm.io.BinaryReader;
import io.spicelabs.baharat.rpm.io.BoundedInputStream;
import io.spicelabs.baharat.rpm.payload.CpioArchiveReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Truncation-sweep and no-progress tests (Fresh Scent Phase 4, findings B3/B8/B15,
 * catalog §5/§6). Lives in the {@code bomb} package so a reintroduced hang or unbounded
 * loop poisons only the isolated fork.
 */
class TruncationSweepBombTest {

    private static byte[] validCpio() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(cpioEntry("a.txt", "AAAAAAAAAA", 0100644));
        out.write(cpioEntry("b.txt", "BBBB", 0100644));
        out.write(cpioEntry("TRAILER!!!", "", 0));
        return out.toByteArray();
    }

    private static byte[] cpioEntry(String name, String content, int mode) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] nameBytes = (name + "\0").getBytes(StandardCharsets.US_ASCII);
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        String header = String.format(
                "%s%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X",
                "070701", 0, mode, 0, 0, 1, 0, contentBytes.length,
                0, 0, 0, 0, nameBytes.length, 0);
        out.write(header.getBytes(StandardCharsets.US_ASCII));
        out.write(nameBytes);
        int pad1 = (4 - ((110 + nameBytes.length) % 4)) % 4;
        out.write(new byte[pad1]);
        out.write(contentBytes);
        int pad2 = (4 - (contentBytes.length % 4)) % 4;
        out.write(new byte[pad2]);
        return out.toByteArray();
    }

    // Requirement: catalog §6 / finding B3 / plan Phase 4.2
    // Theory: EVERY strict prefix of a valid CPIO archive must either parse completely
    //         (full length) or throw — never silently end the iteration with partial data.
    //         Entries delivered before the cut must be IDENTICAL to the full parse
    //         (content compare, not length — catalog §13).
    // Revert-check: restoring `finished = true; return null` on short headers makes every
    //               mid-archive cut silently "succeed" with fewer entries.
    @Test
    @Timeout(30)
    void cpioTruncationSweepThrowsOrParsesCompletely() throws IOException, InvalidFormatException {
        byte[] full = validCpio();
        List<String> fullNames = readAllNames(full);
        assertThat(fullNames).containsExactly("a.txt", "b.txt");

        for (int cut = 0; cut < full.length; cut++) {
            byte[] prefix = new byte[cut];
            System.arraycopy(full, 0, prefix, 0, cut);
            try {
                List<String> names = readAllNames(prefix);
                // Parsing without error is only legal for the full archive, or a cut inside
                // the trailing padding bytes of the final TRAILER entry (those bytes carry
                // no information — all entries and their content were already delivered).
                assertThat(names).containsExactlyElementsOf(fullNames);
                assertThat(cut).isGreaterThanOrEqualTo(full.length - 4);
            } catch (InvalidFormatException | IOException e) {
                // Loud rejection — the required outcome for every truncated prefix.
            }
        }
    }

    private static List<String> readAllNames(byte[] archive)
            throws IOException, InvalidFormatException {
        List<String> names = new ArrayList<>();
        try (CpioArchiveReader reader = new CpioArchiveReader(new ByteArrayInputStream(archive))) {
            CpioArchiveReader.CpioEntry entry;
            while ((entry = reader.nextEntry()) != null) {
                names.add(entry.name());
            }
        }
        return names;
    }

    // Requirement: catalog §6 / finding B15 / plan Phase 4.3
    // Theory: a member whose content is cut off must surface loudly on read — the bounded
    //         entry stream throws instead of returning silently partial bytes.
    // Revert-check: removing checkTruncated in BoundedInputStream makes readAllBytes
    //               return the partial data (the old silent behavior).
    @Test
    void truncatedBoundedSectionThrows() throws IOException {
        BoundedInputStream stream = new BoundedInputStream(
                new ByteArrayInputStream("abc".getBytes()), 10);
        assertThatThrownBy(stream::readAllBytes).isInstanceOf(IOException.class);
    }

    // Requirement: catalog §5 / finding B8 / plan Phase 4.5
    // Theory: a no-progress stream (read returns 0 forever) must terminate with an error,
    //         not spin — BinaryReader.readBytes treats read <= 0 as failure.
    @Test
    @Timeout(10)
    void noProgressBinaryReaderTerminates() {
        InputStream zeroReader = new InputStream() {
            @Override
            public int read() {
                return 0;
            }

            @Override
            public int read(byte[] b, int off, int len) {
                return 0;
            }
        };
        BinaryReader reader = new BinaryReader(zeroReader);
        assertThatThrownBy(() -> reader.readBytes(16)).isInstanceOf(IOException.class);
    }

    // Requirement: catalog §5 / finding B8 / plan Phase 4.5
    // Theory: CpioArchiveReader.readFully treats read <= 0 as end (no spin); the caller's
    //         short-read check then throws InvalidFormatException — loud and terminating.
    @Test
    @Timeout(10)
    void noProgressCpioTerminates() {
        InputStream zeroReader = new InputStream() {
            @Override
            public int read() {
                return 0;
            }

            @Override
            public int read(byte[] b, int off, int len) {
                return 0;
            }
        };
        CpioArchiveReader reader = new CpioArchiveReader(zeroReader);
        assertThatThrownBy(reader::nextEntry).isInstanceOf(InvalidFormatException.class);
    }

    // Requirement: catalog §5 / finding B3 / plan Phase 4.2 — mid-archive IOException in
    //         a tar payload stream surfaces as BaharatStreamException, not silent end.
    // Theory: covered via the tar readers' TarEntryIterator pending-failure (tested in
    //         Phase 5 with real tar fixtures through each reader).
    // Here: a placeholder pin to keep the exception contract visible in the bomb fork.
    @Test
    void baharatStreamExceptionCarriesCheckedCause() {
        IOException cause = new IOException("boom");
        BaharatStreamException ex = new BaharatStreamException("Corrupt tar", cause);
        assertThat(ex.getCause()).isSameAs(cause);
    }
}
