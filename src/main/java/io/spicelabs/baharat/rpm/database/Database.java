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

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Reads the RPM database to query installed packages.
 *
 * <p>Modern RPM (4.16+) uses SQLite by default for its database, stored
 * at /var/lib/rpm/rpmdb.sqlite. This class reads that database to provide
 * information about installed packages.
 *
 * <p>Example usage:
 * <pre>{@code
 * // Open the default system RPM database
 * try (Database db = Database.openSystem()) {
 *     // List all installed packages
 *     db.listPackages().forEach(System.out::println);
 *
 *     // Find a specific package
 *     Optional<InstalledPackage> bash = db.findPackage("bash");
 *     bash.ifPresent(pkg -> System.out.println("bash version: " + pkg.version()));
 *
 *     // Search for packages by name pattern
 *     List<InstalledPackage> kernels = db.searchPackages("kernel%");
 * }
 * }</pre>
 *
 * @see InstalledPackage
 */
public final class Database implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(Database.class);

    /**
     * Default path to the RPM database on modern systems.
     */
    public static final Path DEFAULT_DB_PATH = Path.of("/var/lib/rpm/rpmdb.sqlite");

    /**
     * Legacy Berkeley DB path (for older systems).
     */
    public static final Path LEGACY_DB_PATH = Path.of("/var/lib/rpm/Packages");

    private final Connection connection;
    private final Path dbPath;

    private Database(@NotNull Connection connection, @NotNull Path dbPath) {
        this.connection = connection;
        this.dbPath = dbPath;
    }

    /**
     * Opens the system RPM database.
     *
     * <p>This method attempts to open the SQLite database at the default
     * location (/var/lib/rpm/rpmdb.sqlite).
     *
     * @return an open database connection
     * @throws IOException if the database cannot be opened
     */
    public static @NotNull Database openSystem() throws IOException {
        return open(DEFAULT_DB_PATH);
    }

    /**
     * Opens an RPM database at the specified path.
     *
     * @param dbPath path to the rpmdb.sqlite file
     * @return an open database connection
     * @throws IOException if the database cannot be opened
     */
    public static @NotNull Database open(@NotNull Path dbPath) throws IOException {
        if (!Files.exists(dbPath)) {
            throw new IOException("RPM database not found: " + dbPath);
        }

        if (!Files.isRegularFile(dbPath)) {
            throw new IOException("RPM database path is not a file: " + dbPath);
        }

        // Verify it's a SQLite database
        if (!isSqliteDatabase(dbPath)) {
            throw new IOException("File is not a valid SQLite database: " + dbPath);
        }

        log.debug("Opening RPM database: {}", dbPath);
        try {
            String jdbcUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();
            Connection conn = DriverManager.getConnection(jdbcUrl);
            conn.setAutoCommit(false);
            // Set read-only mode for safety
            conn.setReadOnly(true);
            log.info("Opened RPM database: {}", dbPath);
            return new Database(conn, dbPath);
        } catch (SQLException e) {
            throw new IOException("Failed to open RPM database: " + e.getMessage(), e);
        }
    }

    /**
     * Returns the path to the opened database.
     *
     * @return the database path
     */
    public @NotNull Path path() {
        return dbPath;
    }

    /**
     * Returns the number of installed packages.
     *
     * @return the package count
     * @throws IOException if a database error occurs
     */
    public int packageCount() throws IOException {
        String sql = "SELECT COUNT(*) FROM Packages";
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        } catch (SQLException e) {
            throw new IOException("Failed to count packages: " + e.getMessage(), e);
        }
    }

    /**
     * Lists all installed packages.
     *
     * @return a stream of installed packages
     * @throws IOException if a database error occurs
     */
    public @NotNull Stream<InstalledPackage> listPackages() throws IOException {
        return listPackagesInternal().stream();
    }

    /**
     * Lists all installed packages as a List.
     *
     * @return list of installed packages
     * @throws IOException if a database error occurs
     */
    public @NotNull List<InstalledPackage> listPackagesAsList() throws IOException {
        return listPackagesInternal();
    }

    private List<InstalledPackage> listPackagesInternal() throws IOException {
        String sql = """
            SELECT hnum, blob FROM Packages
            """;

        List<InstalledPackage> packages = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                byte[] headerBlob = rs.getBytes("blob");
                if (headerBlob != null) {
                    parseHeaderBlob(headerBlob).ifPresent(packages::add);
                }
            }
        } catch (SQLException e) {
            throw new IOException("Failed to list packages: " + e.getMessage(), e);
        }

        log.debug("Listed {} packages from database", packages.size());
        return packages;
    }

    /**
     * Finds a package by exact name.
     *
     * @param name the package name
     * @return the package if found
     * @throws IOException if a database error occurs
     */
    public @NotNull Optional<InstalledPackage> findPackage(@NotNull String name) throws IOException {
        String sql = """
            SELECT p.hnum, p.blob FROM Packages p
            JOIN Name n ON p.hnum = n.hnum
            WHERE n.name = ?
            """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    byte[] headerBlob = rs.getBytes("blob");
                    if (headerBlob != null) {
                        return parseHeaderBlob(headerBlob);
                    }
                }
            }
        } catch (SQLException e) {
            throw new IOException("Failed to find package: " + e.getMessage(), e);
        }

        return Optional.empty();
    }

    /**
     * Searches for packages by name pattern (SQL LIKE syntax).
     *
     * @param pattern the name pattern (use % for wildcard)
     * @return list of matching packages
     * @throws IOException if a database error occurs
     */
    public @NotNull List<InstalledPackage> searchPackages(@NotNull String pattern) throws IOException {
        String sql = """
            SELECT p.hnum, p.blob FROM Packages p
            JOIN Name n ON p.hnum = n.hnum
            WHERE n.name LIKE ?
            """;

        List<InstalledPackage> packages = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, pattern);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    byte[] headerBlob = rs.getBytes("blob");
                    if (headerBlob != null) {
                        parseHeaderBlob(headerBlob).ifPresent(packages::add);
                    }
                }
            }
        } catch (SQLException e) {
            throw new IOException("Failed to search packages: " + e.getMessage(), e);
        }

        log.debug("Found {} packages matching pattern: {}", packages.size(), pattern);
        return packages;
    }

    /**
     * Checks if a package is installed.
     *
     * @param name the package name
     * @return true if the package is installed
     * @throws IOException if a database error occurs
     */
    public boolean isInstalled(@NotNull String name) throws IOException {
        String sql = """
            SELECT COUNT(*) FROM Name WHERE name = ?
            """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            throw new IOException("Failed to check package: " + e.getMessage(), e);
        }

        return false;
    }

    @Override
    public void close() throws IOException {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                log.debug("Closed RPM database connection");
            }
        } catch (SQLException e) {
            throw new IOException("Failed to close database: " + e.getMessage(), e);
        }
    }

    /**
     * Checks if the system has an SQLite RPM database.
     *
     * @return true if the SQLite database exists
     */
    public static boolean isSystemDatabaseAvailable() {
        return Files.exists(DEFAULT_DB_PATH) && isSqliteDatabase(DEFAULT_DB_PATH);
    }

    private static boolean isSqliteDatabase(Path path) {
        try {
            byte[] header = Files.readAllBytes(path);
            if (header.length < 16) {
                return false;
            }
            // SQLite database files start with "SQLite format 3"
            return header[0] == 'S' && header[1] == 'Q' && header[2] == 'L' &&
                   header[3] == 'i' && header[4] == 't' && header[5] == 'e';
        } catch (IOException e) {
            return false;
        }
    }

    private Optional<InstalledPackage> parseHeaderBlob(byte[] blob) {
        try {
            // The blob is an RPM header structure
            // Parse it to extract package metadata
            return parseRpmHeader(blob);
        } catch (Exception e) {
            log.warn("Failed to parse package header: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<InstalledPackage> parseRpmHeader(byte[] headerData) {
        // The header data in the database is a serialized RPM header
        // We need to parse it to extract the tag values

        if (headerData.length < 16) {
            return Optional.empty();
        }

        try {
            // Skip the header magic and version (8 bytes)
            // Then read entry count and data size
            int offset = 0;

            // Check for header magic 0x8EADE801
            if (headerData.length > 4) {
                int magic = ((headerData[0] & 0xFF) << 24) |
                            ((headerData[1] & 0xFF) << 16) |
                            ((headerData[2] & 0xFF) << 8) |
                            (headerData[3] & 0xFF);

                if (magic == 0x8EADE801) {
                    offset = 8; // Skip magic (4) + reserved (4)
                }
            }

            if (offset + 8 > headerData.length) {
                return Optional.empty();
            }

            int entryCount = ((headerData[offset] & 0xFF) << 24) |
                             ((headerData[offset + 1] & 0xFF) << 16) |
                             ((headerData[offset + 2] & 0xFF) << 8) |
                             (headerData[offset + 3] & 0xFF);

            int dataSize = ((headerData[offset + 4] & 0xFF) << 24) |
                           ((headerData[offset + 5] & 0xFF) << 16) |
                           ((headerData[offset + 6] & 0xFF) << 8) |
                           (headerData[offset + 7] & 0xFF);

            // Sanity checks
            if (entryCount < 0 || entryCount > 10000 || dataSize < 0 || dataSize > headerData.length) {
                return Optional.empty();
            }

            int indexStart = offset + 8;
            int dataStart = indexStart + (entryCount * 16);

            if (dataStart + dataSize > headerData.length) {
                return Optional.empty();
            }

            // Parse index entries to find the tags we need
            String name = null;
            String version = null;
            String release = null;
            String arch = null;
            Integer epoch = null;
            Long installTime = null;
            Long size = null;
            String summary = null;
            String vendor = null;
            String packager = null;

            for (int i = 0; i < entryCount; i++) {
                int entryOffset = indexStart + (i * 16);
                if (entryOffset + 16 > headerData.length) {
                    break;
                }

                int tag = ((headerData[entryOffset] & 0xFF) << 24) |
                          ((headerData[entryOffset + 1] & 0xFF) << 16) |
                          ((headerData[entryOffset + 2] & 0xFF) << 8) |
                          (headerData[entryOffset + 3] & 0xFF);

                int type = ((headerData[entryOffset + 4] & 0xFF) << 24) |
                           ((headerData[entryOffset + 5] & 0xFF) << 16) |
                           ((headerData[entryOffset + 6] & 0xFF) << 8) |
                           (headerData[entryOffset + 7] & 0xFF);

                int dataOffset = ((headerData[entryOffset + 8] & 0xFF) << 24) |
                                 ((headerData[entryOffset + 9] & 0xFF) << 16) |
                                 ((headerData[entryOffset + 10] & 0xFF) << 8) |
                                 (headerData[entryOffset + 11] & 0xFF);

                int absDataOffset = dataStart + dataOffset;
                if (absDataOffset >= headerData.length) {
                    continue;
                }

                // Common RPM header tags
                switch (tag) {
                    case 1000 -> name = readString(headerData, absDataOffset); // NAME
                    case 1001 -> version = readString(headerData, absDataOffset); // VERSION
                    case 1002 -> release = readString(headerData, absDataOffset); // RELEASE
                    case 1003 -> epoch = readInt(headerData, absDataOffset); // EPOCH
                    case 1004 -> summary = readString(headerData, absDataOffset); // SUMMARY
                    case 1009 -> size = readLong(headerData, absDataOffset); // SIZE
                    case 1011 -> vendor = readString(headerData, absDataOffset); // VENDOR
                    case 1015 -> packager = readString(headerData, absDataOffset); // PACKAGER
                    case 1022 -> arch = readString(headerData, absDataOffset); // ARCH
                    case 1008 -> installTime = readLong(headerData, absDataOffset); // INSTALLTIME
                }
            }

            if (name == null || version == null || release == null) {
                return Optional.empty();
            }

            return Optional.of(new InstalledPackage(
                    name,
                    Optional.ofNullable(epoch),
                    version,
                    release,
                    arch != null ? arch : "unknown",
                    installTime != null ? Instant.ofEpochSecond(installTime) : Instant.EPOCH,
                    size != null ? size : 0,
                    Optional.ofNullable(summary),
                    Optional.ofNullable(vendor),
                    Optional.ofNullable(packager)
            ));

        } catch (Exception e) {
            log.trace("Failed to parse header: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String readString(byte[] data, int offset) {
        if (offset >= data.length) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = offset; i < data.length && data[i] != 0; i++) {
            sb.append((char) data[i]);
        }
        return sb.toString();
    }

    private Integer readInt(byte[] data, int offset) {
        if (offset + 4 > data.length) {
            return null;
        }
        return ((data[offset] & 0xFF) << 24) |
               ((data[offset + 1] & 0xFF) << 16) |
               ((data[offset + 2] & 0xFF) << 8) |
               (data[offset + 3] & 0xFF);
    }

    private Long readLong(byte[] data, int offset) {
        Integer val = readInt(data, offset);
        return val != null ? val.longValue() : null;
    }
}
