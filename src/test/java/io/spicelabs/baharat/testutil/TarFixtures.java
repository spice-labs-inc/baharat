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
package io.spicelabs.baharat.testutil;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/**
 * Synthetic package fixtures for the Fresh Scent budget tests (Phase 5).
 */
public final class TarFixtures {

    public record ArMember(String name, byte[] content) {
        public static ArMember of(String name, byte[] content) {
            return new ArMember(name, content);
        }
    }

    public record Entry(String name, byte[] content, String linkTarget, boolean isLink) {
        public static Entry file(String name, byte[] content) {
            return new Entry(name, content, null, false);
        }

        public static Entry file(String name, String content) {
            return new Entry(name, content.getBytes(StandardCharsets.UTF_8), null, false);
        }

        public static Entry symlink(String name, String target) {
            return new Entry(name, new byte[0], target, true);
        }
    }

    private TarFixtures() {
    }

    public static byte[] tar(List<Entry> entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (TarArchiveOutputStream taos = new TarArchiveOutputStream(out)) {
            taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            for (Entry e : entries) {
                TarArchiveEntry tae;
                if (e.isLink()) {
                    // Explicit SYMLINK typeflag: setLinkName alone would write a regular
                    // file (typeflag '0') and the readers would never see a symlink.
                    tae = new TarArchiveEntry(e.name(), TarConstants.LF_SYMLINK);
                    tae.setLinkName(e.linkTarget());
                    tae.setSize(0);
                } else {
                    tae = new TarArchiveEntry(e.name());
                    byte[] content = e.content();
                    tae.setSize(content.length);
                    taos.putArchiveEntry(tae);
                    taos.write(content);
                    taos.closeArchiveEntry();
                    continue;
                }
                taos.putArchiveEntry(tae);
                taos.closeArchiveEntry();
            }
        }
        return out.toByteArray();
    }

    public static byte[] gzipTar(List<Entry> entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(tar(entries));
        }
        return out.toByteArray();
    }

    /** Minimal ar archive (deb container) with NAMED members. */
    public static byte[] ar(ArMember... members) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("!<arch>\n".getBytes(StandardCharsets.US_ASCII));
        for (ArMember member : members) {
            writeArMember(out, member.name(), member.content());
        }
        return out.toByteArray();
    }

    private static void writeArMember(ByteArrayOutputStream out, String name, byte[] content)
            throws IOException {
        StringBuilder header = new StringBuilder();
        header.append(String.format("%-16s", name));
        header.append(String.format("%-12d", 0));  // timestamp
        header.append(String.format("%-6d", 0));   // uid
        header.append(String.format("%-6d", 0));   // gid
        header.append(String.format("%-8s", "100644"));
        header.append(String.format("%-10d", content.length));
        header.append("`\n");
        out.write(header.toString().getBytes(StandardCharsets.US_ASCII));
        out.write(content);
        if (content.length % 2 != 0) {
            out.write('\n');
        }
    }

    /**
     * Hand-built GNU sparse 1.0 (PAX) tar: one entry with LOGICAL size 1 MiB and only 8
     * physical bytes. commons-compress delivers the logical size zero-filled, and
     * {@code getRealSize()} reports the value the stream actually delivers — readers must
     * use it, not the raw header size (finding B16).
     */
    public static byte[] gnuSparseTar() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String logical = "1048576";

        // x-header
        StringBuilder pax = new StringBuilder();
        pax.append(paxRecord("GNU.sparse.realsize=" + logical));
        pax.append(paxRecord("GNU.sparse.name=sparse.bin"));
        byte[] paxBytes = pax.toString().getBytes(StandardCharsets.US_ASCII);
        out.write(header("GNUFileParts.0/sparse.bin", 'x', paxBytes.length));
        out.write(paxBytes);
        int pad = (512 - (paxBytes.length % 512)) % 512;
        out.write(new byte[pad]);

        // real entry header + sparse number lines + data
        out.write(header("sparse.bin", '0', 8));
        out.write("2\n0\n4\n1048572\n4\n".getBytes(StandardCharsets.US_ASCII));
        out.write("AAAA".getBytes(StandardCharsets.US_ASCII));
        out.write("BBBB".getBytes(StandardCharsets.US_ASCII));
        out.write(new byte[512 - 17 - 8]); // pad data block to 512
        out.write(new byte[1024]); // end blocks
        return out.toByteArray();
    }

    private static String paxRecord(String kv) {
        for (int len = kv.length() + 2; ; len++) {
            if (len - String.valueOf(len).length() == kv.length() + 2) {
                return len + " " + kv + "\n";
            }
        }
    }

    private static byte[] header(String name, int typeflag, long size) throws IOException {        byte[] h = new byte[512];
        byte[] nb = name.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(nb, 0, h, 0, nb.length);
        writeOctal(h, 100, 0100644);
        writeOctal(h, 124, size);
        h[156] = (byte) typeflag;
        System.arraycopy("ustar\0".getBytes(StandardCharsets.US_ASCII), 0, h, 257, 6);
        h[263] = '0';
        h[264] = '0';
        writeChecksum(h);
        return h;
    }

    private static void writeOctal(byte[] buf, int offset, long value) {
        String s = String.format("%011o", value);
        byte[] b = s.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(b, 0, buf, offset, b.length);
        buf[offset + 11] = 0;
    }

    private static void writeChecksum(byte[] header) {
        for (int i = 148; i < 156; i++) {
            header[i] = ' ';
        }
        long sum = 0;
        for (byte b : header) {
            sum += (b & 0xFF);
        }
        String s = String.format("%06o", sum);
        byte[] b = s.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(b, 0, header, 148, b.length);
        header[154] = 0;
        header[155] = ' ';
    }
}
