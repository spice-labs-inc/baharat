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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;

/**
 * Enumerates the supported package formats.
 *
 * <p>Each format is defined by:
 * <ul>
 *   <li>Primary file extension</li>
 *   <li>Optional magic bytes for detection</li>
 *   <li>Distribution family (Linux or BSD)</li>
 * </ul>
 *
 * <h2>Format Detection</h2>
 * <p>Formats can be detected by:
 * <ul>
 *   <li>Magic bytes at the start of the file</li>
 *   <li>File extension pattern matching</li>
 *   <li>Internal structure inspection (for ambiguous formats)</li>
 * </ul>
 */
public enum PackageFormat {
    /**
     * RPM (Red Hat Package Manager) format.
     * Used by: Fedora, RHEL, CentOS, openSUSE, Amazon Linux.
     * Structure: Lead + Headers + CPIO payload.
     */
    RPM(".rpm", new byte[]{(byte) 0xED, (byte) 0xAB, (byte) 0xEE, (byte) 0xDB}, Family.LINUX),

    /**
     * DEB (Debian) format.
     * Used by: Debian, Ubuntu, Linux Mint, Pop!_OS.
     * Structure: ar archive containing debian-binary, control.tar, data.tar.
     */
    DEB(".deb", new byte[]{'!', '<', 'a', 'r', 'c', 'h', '>', '\n'}, Family.LINUX),

    /**
     * Pacman/ALPM (Arch Linux Package Manager) format.
     * Used by: Arch Linux, Manjaro, EndeavourOS.
     * Structure: tar archive with .PKGINFO, .MTREE, and payload files.
     */
    PACMAN(".pkg.tar", null, Family.LINUX),

    /**
     * APK (Alpine Package Keeper) format.
     * Used by: Alpine Linux, postmarketOS.
     * Structure: tar.gz with .PKGINFO, .SIGN files, and payload.
     */
    APK(".apk", new byte[]{0x1F, (byte) 0x8B}, Family.LINUX),

    /**
     * FreeBSD pkg format.
     * Used by: FreeBSD 10+.
     * Structure: tar+xz/zst with +COMPACT_MANIFEST, +MANIFEST.
     */
    FREEBSD_PKG(".pkg", null, Family.BSD),

    /**
     * OpenBSD pkg format.
     * Used by: OpenBSD.
     * Structure: tar+gz with +CONTENTS, +DESC.
     */
    OPENBSD_PKG(".tgz", new byte[]{0x1F, (byte) 0x8B}, Family.BSD);

    private final String extension;
    private final byte[] magic;
    private final Family family;

    PackageFormat(String extension, byte[] magic, Family family) {
        this.extension = extension;
        this.magic = magic;
        this.family = family;
    }

    /**
     * Returns the primary file extension for this format.
     *
     * @return the extension (e.g., ".rpm", ".deb")
     */
    public @NotNull String extension() {
        return extension;
    }

    /**
     * Returns the magic bytes for this format, if any.
     *
     * @return an Optional containing the magic bytes, or empty if detection requires structure inspection
     */
    public @NotNull Optional<byte[]> magic() {
        return magic != null ? Optional.of(magic.clone()) : Optional.empty();
    }

    /**
     * Returns the distribution family (Linux or BSD).
     *
     * @return the family
     */
    public @NotNull Family family() {
        return family;
    }

    /**
     * Detects the package format from a file path.
     *
     * <p>Detection uses multiple strategies:
     * <ol>
     *   <li>Read magic bytes from file and match against known formats</li>
     *   <li>Use file extension as fallback</li>
     *   <li>Inspect internal structure for ambiguous cases</li>
     * </ol>
     *
     * @param path the path to the package file
     * @return an Optional containing the detected format, or empty if unknown
     * @throws IOException if the file cannot be read
     */
    public static @NotNull Optional<PackageFormat> detect(@NotNull Path path) throws IOException {
        String fileName = path.getFileName().toString().toLowerCase();
        try (InputStream in = Files.newInputStream(path)) {
            return detect(in, fileName);
        }
    }

    public static @NotNull Optional<PackageFormat> detect(@NotNull InputStream in, @NotNull String fileName) throws IOException {

        // Read magic bytes
        byte[] header = new byte[8];
        int bytesRead;

        try {
            if (in.markSupported())
                in.mark(header.length);
            bytesRead = in.read(header);
        } finally {
            if (in.markSupported())
                in.reset();
        }

        if (bytesRead < 4) {
            return Optional.empty();
        }

        // Check RPM magic (most specific)
        if (matchesMagic(header, RPM.magic)) {
            return Optional.of(RPM);
        }

        // Check DEB magic (ar archive)
        if (matchesMagic(header, DEB.magic)) {
            return Optional.of(DEB);
        }

        // Check for gzip magic (APK, OpenBSD, or compressed Pacman)
        if (header[0] == 0x1F && header[1] == (byte) 0x8B) {
            // Distinguish by extension
            if (fileName.endsWith(".apk")) {
                return Optional.of(APK);
            }
            if (fileName.endsWith(".tgz")) {
                // Could be OpenBSD pkg - need to check internal structure
                return detectFromTarGz(fileName);
            }
            if (fileName.contains(".pkg.tar")) {
                return Optional.of(PACMAN);
            }
        }

        // Check for xz magic (common for FreeBSD and Pacman)
        if (header[0] == (byte) 0xFD && header[1] == '7' && header[2] == 'z' &&
                header[3] == 'X' && header[4] == 'Z' && header[5] == 0x00) {
            if (fileName.endsWith(".pkg") || fileName.endsWith(".txz")) {
                return detectFromTarXz(fileName);
            }
            if (fileName.contains(".pkg.tar")) {
                return Optional.of(PACMAN);
            }
        }

        // Check for zstd magic (common for modern Pacman and FreeBSD)
        if (header[0] == 0x28 && header[1] == (byte) 0xB5 && header[2] == 0x2F && header[3] == (byte) 0xFD) {
            if (fileName.contains(".pkg.tar")) {
                return Optional.of(PACMAN);
            }
            if (fileName.endsWith(".pkg")) {
                return Optional.of(FREEBSD_PKG);
            }
        }

        // Fall back to extension-based detection
        return detectFromExtension(fileName);
    }

    /**
     * Detects the package format from magic bytes.
     *
     * @param magic the first bytes of the file (at least 8 bytes recommended)
     * @return an Optional containing the detected format, or empty if unknown
     */
    public static @NotNull Optional<PackageFormat> detect(byte[] magic) {
        if (magic == null || magic.length < 4) {
            return Optional.empty();
        }

        // Check RPM magic
        if (matchesMagic(magic, RPM.magic)) {
            return Optional.of(RPM);
        }

        // Check DEB magic
        if (magic.length >= 8 && matchesMagic(magic, DEB.magic)) {
            return Optional.of(DEB);
        }

        // Gzip magic - could be APK, OpenBSD, or compressed tar
        if (magic[0] == 0x1F && magic[1] == (byte) 0x8B) {
            // Without filename, we can't distinguish - return empty
            return Optional.empty();
        }

        return Optional.empty();
    }

    private static @NotNull Optional<PackageFormat> detectFromExtension(@NotNull String fileName) {
        if (fileName.endsWith(".rpm")) {
            return Optional.of(RPM);
        }
        if (fileName.endsWith(".deb")) {
            return Optional.of(DEB);
        }
        if (fileName.contains(".pkg.tar")) {
            return Optional.of(PACMAN);
        }
        if (fileName.endsWith(".apk")) {
            return Optional.of(APK);
        }
        if (fileName.endsWith(".txz") || (fileName.endsWith(".pkg") && !fileName.contains(".pkg.tar"))) {
            return Optional.of(FREEBSD_PKG);
        }
        if (fileName.endsWith(".tgz")) {
            return Optional.of(OPENBSD_PKG);
        }
        return Optional.empty();
    }

    private static @NotNull Optional<PackageFormat> detectFromTarGz(@NotNull String fileName) throws IOException {
        // For now, use extension heuristics
        // Full detection would decompress and check for +CONTENTS (OpenBSD) vs .PKGINFO (APK)
        if (fileName.endsWith(".tgz")) {
            return Optional.of(OPENBSD_PKG);
        }
        if (fileName.endsWith(".apk")) {
            return Optional.of(APK);
        }
        return Optional.empty();
    }

    private static @NotNull Optional<PackageFormat> detectFromTarXz(@NotNull String fileName) throws IOException {
        // For now, use extension heuristics
        // Full detection would decompress and check for +MANIFEST (FreeBSD) vs .PKGINFO (Pacman)
        if (fileName.endsWith(".pkg") || fileName.endsWith(".txz")) {
            return Optional.of(FREEBSD_PKG);
        }
        if (fileName.contains(".pkg.tar")) {
            return Optional.of(PACMAN);
        }
        return Optional.empty();
    }

    private static boolean matchesMagic(byte[] data, byte[] magic) {
        if (magic == null || data.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (data[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Distribution family classification.
     */
    public enum Family {
        /** Linux-based distributions */
        LINUX,
        /** BSD-based distributions */
        BSD
    }
}
