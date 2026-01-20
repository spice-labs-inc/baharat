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
package io.spicelabs.baharat;

import io.spicelabs.baharat.apk.ApkPackage;
import io.spicelabs.baharat.apk.ApkReader;
import io.spicelabs.baharat.deb.DebPackage;
import io.spicelabs.baharat.deb.DebReader;
import io.spicelabs.baharat.freebsd.FreeBsdPackage;
import io.spicelabs.baharat.freebsd.FreeBsdReader;
import io.spicelabs.baharat.openbsd.OpenBsdPackage;
import io.spicelabs.baharat.openbsd.OpenBsdReader;
import io.spicelabs.baharat.pacman.PacmanPackage;
import io.spicelabs.baharat.pacman.PacmanReader;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Main entry point for reading package files from any supported format.
 *
 * <p>This class provides static methods for:
 * <ul>
 *   <li>Auto-detecting and reading packages ({@link #read(Path)})</li>
 *   <li>Reading format-specific packages ({@link #readRpm(Path)}, {@link #readDeb(Path)}, etc.)</li>
 *   <li>Detecting package format ({@link #detect(Path)})</li>
 *   <li>Streaming payload contents ({@link #streamPayload(Path)})</li>
 * </ul>
 *
 * <h2>Supported Formats</h2>
 * <table>
 *   <tr><th>Format</th><th>Extension</th><th>Distributions</th></tr>
 *   <tr><td>RPM</td><td>.rpm</td><td>Fedora, RHEL, CentOS, openSUSE</td></tr>
 *   <tr><td>DEB</td><td>.deb</td><td>Debian, Ubuntu, Linux Mint</td></tr>
 *   <tr><td>Pacman</td><td>.pkg.tar.*</td><td>Arch Linux, Manjaro</td></tr>
 *   <tr><td>APK</td><td>.apk</td><td>Alpine Linux</td></tr>
 *   <tr><td>FreeBSD pkg</td><td>.pkg, .txz</td><td>FreeBSD</td></tr>
 *   <tr><td>OpenBSD pkg</td><td>.tgz</td><td>OpenBSD</td></tr>
 * </table>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Auto-detect format and read
 * Package pkg = PackageReader.read(Path.of("package.deb"));
 * System.out.println("Name: " + pkg.name());
 * System.out.println("Version: " + pkg.version());
 *
 * // Format-specific reading with type safety
 * DebPackage deb = PackageReader.readDeb(Path.of("package.deb"));
 * System.out.println("Priority: " + deb.debMetadata().priority());
 *
 * // Stream payload entries
 * try (Stream<PackageEntry> entries = PackageReader.streamPayload(Path.of("package.rpm"))) {
 *     entries.forEach(entry -> System.out.println(entry.path()));
 * }
 * }</pre>
 *
 * @see Package
 * @see PackageFormat
 * @see PackageMetadata
 */
public final class PackageReader {

    private static final Logger log = LoggerFactory.getLogger(PackageReader.class);

    private PackageReader() {
        // Utility class
    }

    /**
     * Reads a package from a file path, auto-detecting the format.
     *
     * @param path the path to the package file
     * @return the parsed package
     * @throws PackageException if the package cannot be read or format is unknown
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull Package read(@NotNull Path path) throws PackageException, IOException {
        log.debug("Reading package from: {}", path);

        Optional<PackageFormat> format = PackageFormat.detect(path);
        if (format.isEmpty()) {
            throw new PackageException.UnsupportedPackageException(
                    "Unknown package format: " + path.getFileName());
        }

        return read(path, format.get());
    }

    /**
     * Reads a package from a file path with an explicit format.
     *
     * @param path the path to the package file
     * @param format the package format
     * @return the parsed package
     * @throws PackageException if the package cannot be read
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull Package read(@NotNull Path path, @NotNull PackageFormat format)
            throws PackageException, IOException {
        log.debug("Reading {} package from: {}", format, path);

        return switch (format) {
            case RPM -> readRpm(path);
            case DEB -> readDeb(path);
            case PACMAN -> readPacman(path);
            case APK -> readApk(path);
            case FREEBSD_PKG -> readFreeBsd(path);
            case OPENBSD_PKG -> readOpenBsd(path);
        };
    }

    /**
     * Reads an RPM package from a file path.
     *
     * @param path the path to the RPM file
     * @return the parsed RPM package
     * @throws PackageException if the package cannot be read
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull io.spicelabs.baharat.rpm.RpmPackage readRpm(@NotNull Path path) throws PackageException, IOException {
        try {
            return io.spicelabs.baharat.rpm.RpmReader.read(path);
        } catch (io.spicelabs.baharat.rpm.exception.FormatException e) {
            throw new PackageException("Failed to read RPM: " + e.getMessage(), PackageFormat.RPM, e);
        }
    }

    /**
     * Reads a DEB package from a file path.
     *
     * @param path the path to the DEB file
     * @return the parsed DEB package
     * @throws PackageException if the package cannot be read
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull DebPackage readDeb(@NotNull Path path) throws PackageException, IOException {
        return DebReader.read(path);
    }

    /**
     * Reads a Pacman package from a file path.
     *
     * @param path the path to the Pacman package file
     * @return the parsed Pacman package
     * @throws PackageException if the package cannot be read
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull PacmanPackage readPacman(@NotNull Path path) throws PackageException, IOException {
        return PacmanReader.read(path);
    }

    /**
     * Reads an APK package from a file path.
     *
     * @param path the path to the APK file
     * @return the parsed APK package
     * @throws PackageException if the package cannot be read
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull ApkPackage readApk(@NotNull Path path) throws PackageException, IOException {
        return ApkReader.read(path);
    }

    /**
     * Reads a FreeBSD package from a file path.
     *
     * @param path the path to the FreeBSD package file
     * @return the parsed FreeBSD package
     * @throws PackageException if the package cannot be read
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull FreeBsdPackage readFreeBsd(@NotNull Path path) throws PackageException, IOException {
        return FreeBsdReader.read(path);
    }

    /**
     * Reads an OpenBSD package from a file path.
     *
     * @param path the path to the OpenBSD package file
     * @return the parsed OpenBSD package
     * @throws PackageException if the package cannot be read
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull OpenBsdPackage readOpenBsd(@NotNull Path path) throws PackageException, IOException {
        return OpenBsdReader.read(path);
    }

    /**
     * Detects the package format from a file path.
     *
     * @param path the path to the package file
     * @return the detected format, or empty if unknown
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull Optional<PackageFormat> detect(@NotNull Path path) throws IOException {
        return PackageFormat.detect(path);
    }

    /**
     * Checks if a file appears to be a supported package.
     *
     * @param path the path to the file
     * @return true if the file appears to be a package
     */
    public static boolean isPackage(@NotNull Path path) {
        try {
            return PackageFormat.detect(path).isPresent();
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Streams the payload entries from a package file.
     * The returned stream must be closed after use to release resources.
     *
     * @param path the path to the package file
     * @return a stream of payload entries
     * @throws PackageException if the package cannot be read
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull Stream<PackageEntry> streamPayload(@NotNull Path path)
            throws PackageException, IOException {
        Optional<PackageFormat> format = PackageFormat.detect(path);
        if (format.isEmpty()) {
            throw new PackageException.UnsupportedPackageException(
                    "Unknown package format: " + path.getFileName());
        }

        return streamPayload(path, format.get());
    }

    /**
     * Streams the payload entries from a package file with explicit format.
     *
     * @param path the path to the package file
     * @param format the package format
     * @return a stream of payload entries
     * @throws PackageException if the package cannot be read
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull Stream<PackageEntry> streamPayload(@NotNull Path path, @NotNull PackageFormat format)
            throws PackageException, IOException {
        return switch (format) {
            case RPM -> {
                try {
                    yield io.spicelabs.baharat.rpm.RpmReader.streamPayload(path)
                            .map(PackageReader::convertRpmPayloadEntry);
                } catch (io.spicelabs.baharat.rpm.exception.FormatException e) {
                    throw new PackageException("Failed to stream RPM payload: " + e.getMessage(),
                            PackageFormat.RPM, e);
                }
            }
            case DEB -> DebReader.streamPayload(path);
            case PACMAN -> PacmanReader.streamPayload(path);
            case APK -> ApkReader.streamPayload(path);
            case FREEBSD_PKG -> FreeBsdReader.streamPayload(path);
            case OPENBSD_PKG -> OpenBsdReader.streamPayload(path);
        };
    }

    private static @NotNull PackageEntry convertRpmPayloadEntry(
            @NotNull io.spicelabs.baharat.rpm.payload.PayloadEntry entry) {
        return switch (entry) {
            case io.spicelabs.baharat.rpm.payload.PayloadEntry.FileEntry f ->
                    new PackageEntry.FileEntry(f.path(), f.mode(), f.mtime(), f.userName(), f.groupName(),
                            f.size(), f.content());
            case io.spicelabs.baharat.rpm.payload.PayloadEntry.DirectoryEntry d ->
                    new PackageEntry.DirectoryEntry(d.path(), d.mode(), d.mtime(), d.userName(), d.groupName());
            case io.spicelabs.baharat.rpm.payload.PayloadEntry.SymlinkEntry s ->
                    new PackageEntry.SymlinkEntry(s.path(), s.mode(), s.mtime(), s.userName(), s.groupName(),
                            s.target());
        };
    }

    /**
     * Reads just the package metadata without processing the payload.
     * This is more efficient when you only need header information.
     *
     * @param path the path to the package file
     * @return the package metadata
     * @throws PackageException if the package cannot be read
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull PackageMetadata readMetadata(@NotNull Path path) throws PackageException, IOException {
        return read(path).metadata();
    }
}
