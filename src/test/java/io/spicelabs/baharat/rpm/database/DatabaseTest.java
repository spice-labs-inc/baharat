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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for RPM database reading functionality.
 */
class DatabaseTest {

    @TempDir
    Path tempDir;

    @Test
    void installedPackageRecordProperties() {
        InstalledPackage pkg = new InstalledPackage(
                "test-package",
                Optional.of(1),
                "2.0.0",
                "1.fc40",
                "x86_64",
                Instant.parse("2024-01-15T10:30:00Z"),
                1024000,
                Optional.of("A test package"),
                Optional.of("Test Vendor"),
                Optional.of("Test Packager <test@example.com>")
        );

        assertThat(pkg.name()).isEqualTo("test-package");
        assertThat(pkg.epoch()).hasValue(1);
        assertThat(pkg.version()).isEqualTo("2.0.0");
        assertThat(pkg.release()).isEqualTo("1.fc40");
        assertThat(pkg.arch()).isEqualTo("x86_64");
        assertThat(pkg.installTime()).isEqualTo(Instant.parse("2024-01-15T10:30:00Z"));
        assertThat(pkg.size()).isEqualTo(1024000);
        assertThat(pkg.summary()).hasValue("A test package");
        assertThat(pkg.vendor()).hasValue("Test Vendor");
        assertThat(pkg.packager()).hasValue("Test Packager <test@example.com>");
    }

    @Test
    void installedPackageNevraWithEpoch() {
        InstalledPackage pkg = new InstalledPackage(
                "kernel",
                Optional.of(2),
                "6.5.0",
                "1.fc40",
                "x86_64",
                Instant.now(),
                0,
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );

        assertThat(pkg.nevra()).isEqualTo("kernel-2:6.5.0-1.fc40.x86_64");
        assertThat(pkg.nevr()).isEqualTo("kernel-2:6.5.0-1.fc40");
        assertThat(pkg.nvr()).isEqualTo("kernel-6.5.0-1.fc40");
    }

    @Test
    void installedPackageNevraWithoutEpoch() {
        InstalledPackage pkg = new InstalledPackage(
                "bash",
                Optional.empty(),
                "5.2.21",
                "1.fc40",
                "x86_64",
                Instant.now(),
                0,
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );

        assertThat(pkg.nevra()).isEqualTo("bash-5.2.21-1.fc40.x86_64");
        assertThat(pkg.nevr()).isEqualTo("bash-5.2.21-1.fc40");
        assertThat(pkg.nvr()).isEqualTo("bash-5.2.21-1.fc40");
    }

    @Test
    void installedPackageNevraWithZeroEpoch() {
        InstalledPackage pkg = new InstalledPackage(
                "coreutils",
                Optional.of(0),
                "9.4",
                "1.fc40",
                "x86_64",
                Instant.now(),
                0,
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );

        // Epoch 0 should not appear in NEVRA
        assertThat(pkg.nevra()).isEqualTo("coreutils-9.4-1.fc40.x86_64");
    }

    @Test
    void installedPackageToString() {
        InstalledPackage pkg = new InstalledPackage(
                "vim",
                Optional.of(2),
                "9.0",
                "1.fc40",
                "x86_64",
                Instant.now(),
                0,
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );

        assertThat(pkg.toString()).isEqualTo("vim-2:9.0-1.fc40.x86_64");
    }

    @Test
    void openThrowsForNonexistentFile() {
        Path nonexistent = tempDir.resolve("nonexistent.sqlite");

        assertThatThrownBy(() -> Database.open(nonexistent))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void openThrowsForDirectory() throws IOException {
        Path dir = tempDir.resolve("directory");
        Files.createDirectory(dir);

        assertThatThrownBy(() -> Database.open(dir))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("not a file");
    }

    @Test
    void openThrowsForNonSqliteFile() throws IOException {
        Path textFile = tempDir.resolve("not-sqlite.txt");
        Files.writeString(textFile, "This is not a SQLite database");

        assertThatThrownBy(() -> Database.open(textFile))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("not a valid SQLite database");
    }

    @Test
    void isSystemDatabaseAvailableReturnsBoolean() {
        // Just verify it doesn't throw
        boolean available = Database.isSystemDatabaseAvailable();
        // The result depends on whether we're running on an RPM-based system
        assertThat(available).isIn(true, false);
    }

    @Test
    void defaultDbPathIsCorrect() {
        assertThat(Database.DEFAULT_DB_PATH.toString())
                .isEqualTo("/var/lib/rpm/rpmdb.sqlite");
    }

    @Test
    void legacyDbPathIsCorrect() {
        assertThat(Database.LEGACY_DB_PATH.toString())
                .isEqualTo("/var/lib/rpm/Packages");
    }
}
