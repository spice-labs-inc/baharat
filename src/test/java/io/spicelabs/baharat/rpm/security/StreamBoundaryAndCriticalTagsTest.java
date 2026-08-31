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

import io.spicelabs.baharat.BaharatStreamException;
import io.spicelabs.baharat.rpm.RpmReader;
import io.spicelabs.baharat.rpm.exception.InvalidFormatException;
import io.spicelabs.baharat.rpm.payload.CpioArchiveReader;
import io.spicelabs.baharat.rpm.payload.PayloadEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Stream-boundary and critical-tag tests.
 */
class StreamBoundaryAndCriticalTagsTest {


    // Theory: checked corruption detected MID-STREAM must surface as the documented
    //         BaharatStreamException (carrying the checked cause), never a bare
    //         RuntimeException or a silent end.
    // Revert-check: wrapping in bare RuntimeException fails the type assertion.
    @Test
    void cpioStreamTruncationSurfacesBaharatStreamException() throws IOException {
        // A header fragment: 100 bytes of a 110-byte CPIO header.
        byte[] partial = new byte[100];
        partial[0] = '0';
        partial[1] = '7';
        partial[2] = '0';
        partial[3] = '7';
        partial[4] = '0';
        partial[5] = '1';
        try (CpioArchiveReader reader = new CpioArchiveReader(new ByteArrayInputStream(partial))) {
            assertThatThrownBy(() -> reader.stream().toList())
                    .isInstanceOf(BaharatStreamException.class)
                    .hasCauseInstanceOf(InvalidFormatException.class);
        }
    }


    // Theory: an RPM whose main header lacks the mandatory NAME/VERSION/ARCH tags must be
    //         rejected LOUDLY at open — never surfaced as empty metadata.
    // Revert-check: removing requireCriticalTags lets read() succeed with name "".
    @Test
    void rpmMissingCriticalTagsRejected(@TempDir Path dir) throws IOException {
        byte[] rpm = SyntheticRpmBuilder.rpmWithEmptyMainHeader();
        Path rpmFile = dir.resolve("no-tags.rpm");
        Files.write(rpmFile, rpm);
        assertThatThrownBy(() -> RpmReader.read(rpmFile))
                .isInstanceOf(InvalidFormatException.class)
                .hasMessageContaining("missing required NAME tag");
    }

    // Requirement: positive control — a synthetic RPM WITH critical tags parses.
    @Test
    void rpmWithCriticalTagsParses(@TempDir Path dir)
            throws IOException, io.spicelabs.baharat.rpm.exception.FormatException {
        byte[] rpm = SyntheticRpmBuilder.rpmWithPayload(List.of(
                new SyntheticRpmBuilder.CpioEntrySpec("hello.txt", "hi", 0100644)));
        Path rpmFile = dir.resolve("ok.rpm");
        Files.write(rpmFile, rpm);
        assertThat(RpmReader.read(rpmFile).name()).isEqualTo("evil");
    }

    // Requirement: (RPM payload path)
    // Theory: PayloadReader.entries() traversal violations surface as the documented
    //         BaharatStreamException (never a bare RuntimeException).
    // Revert-check: wrapping in bare RuntimeException fails the type assertion.
    @Test
    void payloadTraversalSurfacesBaharatStreamException(@TempDir Path dir)
            throws IOException, io.spicelabs.baharat.rpm.exception.FormatException {
        byte[] rpm = SyntheticRpmBuilder.rpmWithPayload(List.of(
                new SyntheticRpmBuilder.CpioEntrySpec("../../evil", "x", 0100644)));
        Path rpmFile = dir.resolve("traversal.rpm");
        Files.write(rpmFile, rpm);
        assertThatThrownBy(() -> RpmReader.openPayload(rpmFile).entries().toList())
                .isInstanceOf(BaharatStreamException.class)
                .hasMessageContaining("Path traversal");
    }
}
