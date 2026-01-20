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
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ArArchiveReader}.
 */
class ArArchiveReaderTest {

    private static final byte[] AR_MAGIC = "!<arch>\n".getBytes(StandardCharsets.US_ASCII);

    @Test
    void readHeaderValidMagic() throws Exception {
        ByteArrayInputStream in = new ByteArrayInputStream(AR_MAGIC);
        ArArchiveReader reader = new ArArchiveReader(in);

        reader.readHeader();
        // Should not throw - header is valid
    }

    @Test
    void readHeaderInvalidMagic() {
        byte[] invalid = "invalid!".getBytes(StandardCharsets.US_ASCII);
        ByteArrayInputStream in = new ByteArrayInputStream(invalid);
        ArArchiveReader reader = new ArArchiveReader(in);

        assertThatThrownBy(reader::readHeader)
                .isInstanceOf(PackageException.InvalidPackageException.class)
                .hasMessageContaining("Invalid ar archive magic");
    }

    @Test
    void readHeaderTooSmall() {
        byte[] tooSmall = "!<ar".getBytes(StandardCharsets.US_ASCII);
        ByteArrayInputStream in = new ByteArrayInputStream(tooSmall);
        ArArchiveReader reader = new ArArchiveReader(in);

        assertThatThrownBy(reader::readHeader)
                .isInstanceOf(PackageException.InvalidPackageException.class)
                .hasMessageContaining("too small");
    }

    @Test
    void readSingleEntry() throws Exception {
        byte[] archive = createArArchive(List.of(
                new TestEntry("test.txt", "Hello, World!")
        ));
        ByteArrayInputStream in = new ByteArrayInputStream(archive);
        ArArchiveReader reader = new ArArchiveReader(in);

        ArArchiveReader.ArEntry entry = reader.nextEntry();

        assertThat(entry).isNotNull();
        assertThat(entry.name()).isEqualTo("test.txt");
        assertThat(entry.size()).isEqualTo(13);
    }

    @Test
    void readMultipleEntries() throws Exception {
        byte[] archive = createArArchive(List.of(
                new TestEntry("file1.txt", "First"),
                new TestEntry("file2.txt", "Second")
        ));
        ByteArrayInputStream in = new ByteArrayInputStream(archive);
        ArArchiveReader reader = new ArArchiveReader(in);

        ArArchiveReader.ArEntry entry1 = reader.nextEntry();
        assertThat(entry1.name()).isEqualTo("file1.txt");

        // Skip entry1 content
        reader.skipCurrentEntry();

        ArArchiveReader.ArEntry entry2 = reader.nextEntry();
        assertThat(entry2.name()).isEqualTo("file2.txt");

        ArArchiveReader.ArEntry entry3 = reader.nextEntry();
        assertThat(entry3).isNull(); // End of archive
    }

    @Test
    void readEntryContent() throws Exception {
        String content = "Test content";
        byte[] archive = createArArchive(List.of(
                new TestEntry("test.txt", content)
        ));
        ByteArrayInputStream in = new ByteArrayInputStream(archive);
        ArArchiveReader reader = new ArArchiveReader(in);

        ArArchiveReader.ArEntry entry = reader.nextEntry();
        byte[] buf = reader.getEntryInputStream(entry).readAllBytes();

        assertThat(new String(buf, StandardCharsets.UTF_8)).isEqualTo(content);
    }

    @Test
    void readEntryWithOddSize() throws Exception {
        // Odd-sized content should be padded
        String content = "Odd"; // 3 bytes
        byte[] archive = createArArchive(List.of(
                new TestEntry("odd.txt", content),
                new TestEntry("next.txt", "Next")
        ));
        ByteArrayInputStream in = new ByteArrayInputStream(archive);
        ArArchiveReader reader = new ArArchiveReader(in);

        ArArchiveReader.ArEntry entry1 = reader.nextEntry();
        assertThat(entry1.size()).isEqualTo(3);
        reader.skipCurrentEntry();

        ArArchiveReader.ArEntry entry2 = reader.nextEntry();
        assertThat(entry2).isNotNull();
        assertThat(entry2.name()).isEqualTo("next.txt");
    }

    @Test
    void readEntryTimestamp() throws Exception {
        byte[] archive = createArArchiveWithTimestamp("test.txt", "content", 1699574400);
        ByteArrayInputStream in = new ByteArrayInputStream(archive);
        ArArchiveReader reader = new ArArchiveReader(in);

        ArArchiveReader.ArEntry entry = reader.nextEntry();

        assertThat(entry.timestamp()).isEqualTo(1699574400L);
    }

    @Test
    void readEntryMode() throws Exception {
        byte[] archive = createArArchiveWithMode("test.txt", "content", 0100644);
        ByteArrayInputStream in = new ByteArrayInputStream(archive);
        ArArchiveReader reader = new ArArchiveReader(in);

        ArArchiveReader.ArEntry entry = reader.nextEntry();

        assertThat(entry.mode()).isEqualTo(0100644);
    }

    @Test
    void readEntryOwnerGroup() throws Exception {
        byte[] archive = createArArchiveWithOwner("test.txt", "content", 1000, 1000);
        ByteArrayInputStream in = new ByteArrayInputStream(archive);
        ArArchiveReader reader = new ArArchiveReader(in);

        ArArchiveReader.ArEntry entry = reader.nextEntry();

        assertThat(entry.ownerId()).isEqualTo(1000);
        assertThat(entry.groupId()).isEqualTo(1000);
    }

    @Test
    void readEntryNameWithTrailingSlash() throws Exception {
        byte[] archive = createArArchiveWithName("test/", "content");
        ByteArrayInputStream in = new ByteArrayInputStream(archive);
        ArArchiveReader reader = new ArArchiveReader(in);

        ArArchiveReader.ArEntry entry = reader.nextEntry();

        assertThat(entry.name()).isEqualTo("test"); // Trailing slash removed
    }

    @Test
    void readEmptyArchive() throws Exception {
        ByteArrayInputStream in = new ByteArrayInputStream(AR_MAGIC);
        ArArchiveReader reader = new ArArchiveReader(in);

        ArArchiveReader.ArEntry entry = reader.nextEntry();

        assertThat(entry).isNull();
    }

    @Test
    void rejectTruncatedEntryHeader() {
        byte[] truncated = new byte[AR_MAGIC.length + 30]; // Less than 60-byte header
        System.arraycopy(AR_MAGIC, 0, truncated, 0, AR_MAGIC.length);

        ByteArrayInputStream in = new ByteArrayInputStream(truncated);
        ArArchiveReader reader = new ArArchiveReader(in);

        assertThatThrownBy(reader::nextEntry)
                .isInstanceOf(PackageException.InvalidPackageException.class)
                .hasMessageContaining("Truncated");
    }

    @Test
    void rejectInvalidEntryMagic() {
        byte[] archive = new byte[AR_MAGIC.length + 60];
        System.arraycopy(AR_MAGIC, 0, archive, 0, AR_MAGIC.length);
        // Fill header but with wrong entry magic bytes
        archive[AR_MAGIC.length + 58] = 'X';
        archive[AR_MAGIC.length + 59] = 'X';

        ByteArrayInputStream in = new ByteArrayInputStream(archive);
        ArArchiveReader reader = new ArArchiveReader(in);

        assertThatThrownBy(reader::nextEntry)
                .isInstanceOf(PackageException.InvalidPackageException.class)
                .hasMessageContaining("Invalid ar entry magic");
    }

    @Test
    void closeClosesInputStream() throws Exception {
        // Use a wrapper to track if close was called
        boolean[] closeCalled = {false};
        ByteArrayInputStream baseIn = new ByteArrayInputStream(AR_MAGIC);
        java.io.FilterInputStream in = new java.io.FilterInputStream(baseIn) {
            @Override
            public void close() throws java.io.IOException {
                closeCalled[0] = true;
                super.close();
            }
        };

        ArArchiveReader reader = new ArArchiveReader(in);
        reader.close();

        assertThat(closeCalled[0]).isTrue();
    }

    @Test
    void readHeaderOnlyOnce() throws Exception {
        ByteArrayInputStream in = new ByteArrayInputStream(AR_MAGIC);
        ArArchiveReader reader = new ArArchiveReader(in);

        reader.readHeader();
        reader.readHeader(); // Should be no-op

        // Should not throw
    }

    // Helper methods to create test ar archives

    private record TestEntry(String name, String content) {}

    private byte[] createArArchive(List<TestEntry> entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(AR_MAGIC);

        for (TestEntry entry : entries) {
            writeEntry(out, entry.name, entry.content.getBytes(StandardCharsets.UTF_8),
                    System.currentTimeMillis() / 1000, 0, 0, 0100644);
        }

        return out.toByteArray();
    }

    private byte[] createArArchiveWithTimestamp(String name, String content, long timestamp) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(AR_MAGIC);
        writeEntry(out, name, content.getBytes(StandardCharsets.UTF_8), timestamp, 0, 0, 0100644);
        return out.toByteArray();
    }

    private byte[] createArArchiveWithMode(String name, String content, int mode) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(AR_MAGIC);
        writeEntry(out, name, content.getBytes(StandardCharsets.UTF_8), 0, 0, 0, mode);
        return out.toByteArray();
    }

    private byte[] createArArchiveWithOwner(String name, String content, int uid, int gid) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(AR_MAGIC);
        writeEntry(out, name, content.getBytes(StandardCharsets.UTF_8), 0, uid, gid, 0100644);
        return out.toByteArray();
    }

    private byte[] createArArchiveWithName(String name, String content) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(AR_MAGIC);
        writeEntry(out, name, content.getBytes(StandardCharsets.UTF_8), 0, 0, 0, 0100644);
        return out.toByteArray();
    }

    private void writeEntry(ByteArrayOutputStream out, String name, byte[] content,
                           long timestamp, int uid, int gid, int mode) throws IOException {
        // Entry header is 60 bytes
        byte[] header = new byte[60];
        java.util.Arrays.fill(header, (byte) ' ');

        // Name (16 bytes)
        byte[] nameBytes = name.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(nameBytes, 0, header, 0, Math.min(nameBytes.length, 16));

        // Timestamp (12 bytes)
        String ts = String.valueOf(timestamp);
        System.arraycopy(ts.getBytes(StandardCharsets.US_ASCII), 0, header, 16, ts.length());

        // Owner ID (6 bytes)
        String uidStr = String.valueOf(uid);
        System.arraycopy(uidStr.getBytes(StandardCharsets.US_ASCII), 0, header, 28, uidStr.length());

        // Group ID (6 bytes)
        String gidStr = String.valueOf(gid);
        System.arraycopy(gidStr.getBytes(StandardCharsets.US_ASCII), 0, header, 34, gidStr.length());

        // Mode (8 bytes, octal)
        String modeStr = Integer.toOctalString(mode);
        System.arraycopy(modeStr.getBytes(StandardCharsets.US_ASCII), 0, header, 40, modeStr.length());

        // Size (10 bytes)
        String sizeStr = String.valueOf(content.length);
        System.arraycopy(sizeStr.getBytes(StandardCharsets.US_ASCII), 0, header, 48, sizeStr.length());

        // Entry magic (2 bytes)
        header[58] = '`';
        header[59] = '\n';

        out.write(header);
        out.write(content);

        // Padding for odd-sized content
        if (content.length % 2 != 0) {
            out.write('\n');
        }
    }
}
