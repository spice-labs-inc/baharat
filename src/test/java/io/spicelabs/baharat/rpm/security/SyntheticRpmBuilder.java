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

import io.spicelabs.baharat.rpm.header.HeaderTag;
import io.spicelabs.baharat.rpm.header.TagType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Minimal synthetic RPM builder for the security tests.
 *
 * <p>Builds a structurally valid RPM (lead + empty signature header + main header with
 * string tags + gzip-compressed CPIO payload) so the write-through and critical-tag tests
 * exercise the REAL reader path end to end.
 */
final class SyntheticRpmBuilder {

    private SyntheticRpmBuilder() {
    }

    record CpioEntrySpec(String name, String content, int mode) {
    }

    /** Builds an RPM whose payload contains the given CPIO entries (symlinks allowed). */
    static byte[] rpmWithPayload(List<CpioEntrySpec> payloadEntries) throws IOException {
        byte[] cpio = buildCpio(payloadEntries);
        ByteArrayOutputStream payloadBos = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(payloadBos)) {
            gz.write(cpio);
        }
        byte[] payload = payloadBos.toByteArray();

        Map<HeaderTag, String> tags = new LinkedHashMap<>();
        tags.put(HeaderTag.NAME, "evil");
        tags.put(HeaderTag.VERSION, "1.0");
        tags.put(HeaderTag.RELEASE, "1");
        tags.put(HeaderTag.ARCH, "x86_64");
        tags.put(HeaderTag.PAYLOADFORMAT, "cpio");
        tags.put(HeaderTag.PAYLOADCOMPRESSOR, "gzip");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(lead());
        out.write(emptySignatureHeader());
        out.write(mainHeader(tags));
        out.write(payload);
        return out.toByteArray();
    }

    static byte[] rpmWithEmptyMainHeader() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(lead());
        out.writeBytes(emptySignatureHeader());
        out.writeBytes(emptyHeader());
        return out.toByteArray();
    }

    private static byte[] lead() throws IOException {
        byte[] lead = new byte[96];
        lead[0] = (byte) 0xED;
        lead[1] = (byte) 0xAB;
        lead[2] = (byte) 0xEE;
        lead[3] = (byte) 0xDB;
        lead[4] = 3; // major
        lead[5] = 0; // minor
        return lead;
    }

    private static byte[] emptySignatureHeader() throws IOException {
        return emptyHeader();
    }

    private static byte[] emptyHeader() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{(byte) 0x8E, (byte) 0xAD, (byte) 0xE8, 0x01});
        out.write(intToBytes(0)); // reserved
        out.write(intToBytes(0)); // entry count
        out.write(intToBytes(0)); // data size
        return out.toByteArray();
    }

    private static byte[] mainHeader(Map<HeaderTag, String> tags) throws IOException {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        List<int[]> entries = new ArrayList<>();
        for (Map.Entry<HeaderTag, String> tag : tags.entrySet()) {
            byte[] value = (tag.getValue() + "\0").getBytes(StandardCharsets.UTF_8);
            entries.add(new int[]{tag.getKey().tag(), TagType.STRING.code(), data.size(), 1});
            data.write(value, 0, value.length);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{(byte) 0x8E, (byte) 0xAD, (byte) 0xE8, 0x01});
        out.write(intToBytes(0)); // reserved
        out.write(intToBytes(entries.size()));
        out.write(intToBytes(data.size()));
        for (int[] entry : entries) {
            for (int v : entry) {
                out.write(intToBytes(v));
            }
        }
        out.write(data.toByteArray(), 0, data.size());
        return out.toByteArray();
    }

    private static byte[] buildCpio(List<CpioEntrySpec> entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (CpioEntrySpec spec : entries) {
            out.write(cpioEntry(spec.name(), spec.content(), spec.mode()));
        }
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

    private static byte[] intToBytes(int v) {
        return new byte[]{(byte) (v >> 24), (byte) (v >> 16), (byte) (v >> 8), (byte) v};
    }
}
