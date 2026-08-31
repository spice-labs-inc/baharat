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
package io.spicelabs.baharat.rpm.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SQLite-magic detection tests.
 *
 * <p>The implementation reads exactly 16 bytes via {@code readNBytes(16)} — the previous
 * {@code Files.readAllBytes} materialized the entire RPM database (hundreds of MB).
 * The byte-count pin is code-level (readNBytes(16)); the sparse-file test below guards the
 * observable consequence (a huge database file stays cheap to probe).
 */
class DatabaseMagicReadTest {

    private static final byte[] SQLITE_MAGIC = "SQLite format 3\0".getBytes();

    @Test
    void detectsSqliteMagic(@TempDir Path dir) throws IOException {
        Path db = dir.resolve("rpmdb.sqlite");
        Files.write(db, SQLITE_MAGIC);
        assertThat(Database.isSqliteDatabase(db)).isTrue();
    }

    @Test
    void rejectsNonSqlite(@TempDir Path dir) throws IOException {
        Path db = dir.resolve("notdb.sqlite");
        Files.write(db, "not a sqlite database at all".getBytes());
        assertThat(Database.isSqliteDatabase(db)).isFalse();
    }

    @Test
    void rejectsShortFile(@TempDir Path dir) throws IOException {
        Path db = dir.resolve("short.sqlite");
        Files.write(db, new byte[]{'S', 'Q'});
        assertThat(Database.isSqliteDatabase(db)).isFalse();
    }


    // Theory: probing a multi-hundred-MB RPM database must not materialize it. The 100 MB
    //         sparse fixture stays cheap with readNBytes(16); a revert to readAllBytes
    //         materializes the whole file (and at production DB sizes OOMs the fork).
    // Revert-check: restoring Files.readAllBytes makes this test consume 100 MB heap —
    //               and would be visible in the bomb fork's memory behavior.
    @Test
    void largeSparseDatabaseProbedCheaply(@TempDir Path dir) throws IOException {
        Path db = dir.resolve("big.sqlite");
        try (RandomAccessFile raf = new RandomAccessFile(db.toFile(), "rw")) {
            raf.setLength(100L * 1024 * 1024);
            raf.seek(0);
            raf.write(SQLITE_MAGIC);
        }
        assertThat(Database.isSqliteDatabase(db)).isTrue();
    }
}
