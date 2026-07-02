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

import io.spicelabs.coordinates.Purl;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.stream.Stream;

/**
 * Interface representing any Linux/BSD package.
 *
 * <p>This interface provides a unified API for accessing package information
 * regardless of the underlying format. Use instanceof pattern matching for
 * format-specific access:
 *
 * <pre>{@code
 * Package pkg = PackageReader.read(path);
 *
 * String formatInfo = switch (pkg) {
 *     case RpmPackage rpm -> "RPM: " + rpm.nevra();
 *     case DebPackage deb -> "DEB: " + deb.name() + "_" + deb.version();
 *     case PacmanPackage pac -> "Pacman: " + pac.name() + "-" + pac.version();
 *     case ApkPackage apk -> "APK: " + apk.name() + "-" + apk.version();
 *     case FreeBsdPackage fbsd -> "FreeBSD: " + fbsd.name() + "-" + fbsd.version();
 *     case OpenBsdPackage obsd -> "OpenBSD: " + obsd.name() + "-" + obsd.version();
 *     default -> "Unknown format";
 * };
 * }</pre>
 *
 * <h2>Common Operations</h2>
 * <p>All packages support these operations:
 * <ul>
 *   <li>{@link #format()} - Get the package format type</li>
 *   <li>{@link #metadata()} - Access package metadata (name, version, dependencies, etc.)</li>
 *   <li>{@link #payload()} - Stream payload entries (files, directories, symlinks)</li>
 *   <li>{@link #purl()} - Get the canonical Package URL from the package metadata</li>
 * </ul>
 *
 * <h2>Format-Specific Access</h2>
 * <p>For format-specific features, cast to the appropriate implementation:
 * <pre>{@code
 * if (pkg instanceof RpmPackage rpm) {
 *     // Access RPM-specific features
 *     Header signatureHeader = rpm.signatureHeader();
 *     Lead lead = rpm.lead();
 * }
 * }</pre>
 *
 * @see PackageReader
 * @see PackageFormat
 * @see PackageMetadata
 */
public interface Package {

    /**
     * Returns the package format.
     *
     * @return the format
     */
    @NotNull PackageFormat format();

    /**
     * Returns the package metadata.
     *
     * <p>The metadata provides access to common fields like name, version,
     * architecture, dependencies, and file lists.
     *
     * @return the metadata
     */
    @NotNull PackageMetadata metadata();

    /**
     * Streams the payload entries.
     *
     * <p>This method allows streaming access to the package contents without
     * extracting the entire payload into memory. The returned stream must
     * be closed after use to release resources:
     *
     * <pre>{@code
     * try (Stream<PackageEntry> entries = pkg.payload()) {
     *     entries.forEach(entry -> {
     *         System.out.println(entry.path());
     *     });
     * }
     * }</pre>
     *
     * @return a stream of payload entries
     * @throws IOException if an I/O error occurs
     * @throws PackageException if the payload cannot be read
     */
    @NotNull Stream<PackageEntry> payload() throws IOException, PackageException;

    /**
     * Returns the package name.
     *
     * @return the name
     */
    default @NotNull String name() {
        return metadata().name();
    }

    /**
     * Returns the package version.
     *
     * @return the version
     */
    default @NotNull String version() {
        return metadata().version();
    }

    /**
     * Returns the package architecture.
     *
     * @return the architecture
     */
    default @NotNull String arch() {
        return metadata().arch();
    }

    /**
     * Returns the canonical Package URL (PURL) for this package.
     *
     * <p>The PURL is produced exactly once by the package metadata. Calling
     * this method is equivalent to calling {@code metadata().purl()}.
     *
     * @return the Package URL object
     */
    default @NotNull Purl purl() {
        return metadata().purl();
    }
}
