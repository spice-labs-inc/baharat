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

import com.github.packageurl.MalformedPackageURLException;
import com.github.packageurl.PackageURL;
import com.github.packageurl.PackageURLBuilder;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Optional;
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
     * Returns the Package URL (PURL) for this package.
     *
     * <p>Package URLs provide a standard way to identify packages across ecosystems.
     * The format follows the <a href="https://github.com/package-url/purl-spec">PURL specification</a>:
     * {@code pkg:type/namespace/name@version?qualifiers#subpath}
     *
     * <p>PURL types for different formats:
     * <ul>
     *   <li>RPM: {@code pkg:rpm/...}</li>
     *   <li>DEB: {@code pkg:deb/...}</li>
     *   <li>Pacman/ALPM: {@code pkg:alpm/...}</li>
     *   <li>APK: {@code pkg:apk/...}</li>
     *   <li>FreeBSD pkg: {@code pkg:freebsd/...}</li>
     *   <li>OpenBSD pkg: {@code pkg:openbsd/...}</li>
     * </ul>
     *
     * <p>Example PURLs:
     * <ul>
     *   <li>{@code pkg:rpm/fedora/curl@7.50.3-1.fc25?arch=x86_64}</li>
     *   <li>{@code pkg:deb/debian/curl@7.50.3-1?arch=amd64}</li>
     *   <li>{@code pkg:alpm/arch/curl@7.50.3-1?arch=x86_64}</li>
     *   <li>{@code pkg:apk/alpine/curl@7.50.3-r0?arch=x86_64}</li>
     * </ul>
     *
     * <p>Format-specific implementations may override this method to provide
     * additional qualifiers or customized behavior.
     *
     * @return the Package URL object
     */
    default @NotNull PackageURL packageUrl() {
        return packageUrl(Optional.empty());
    }

    /**
     * Returns the Package URL (PURL) for this package with an optional namespace.
     *
     * <p>The namespace typically represents the distribution or repository,
     * such as "fedora", "debian", "alpine", "arch", etc.
     *
     * @param namespace the optional namespace (distribution name)
     * @return the Package URL object
     */
    default @NotNull PackageURL packageUrl(@NotNull Optional<String> namespace) {
        String type = switch (format()) {
            case RPM -> "rpm";
            case DEB -> "deb";
            case PACMAN -> "alpm";
            case APK -> "apk";
            case FREEBSD_PKG -> "freebsd";
            case OPENBSD_PKG -> "openbsd";
        };

        try {
            PackageURLBuilder builder = PackageURLBuilder.aPackageURL()
                    .withType(type)
                    .withName(metadata().name());

            // Add namespace if present
            namespace.ifPresent(builder::withNamespace);

            // Add version
            String version = buildVersionString(metadata());
            if (!version.isEmpty()) {
                builder.withVersion(version);
            }

            // Add qualifiers
            String arch = metadata().arch();
            if (!arch.isEmpty() && !arch.equals("noarch") && !arch.equals("all") && !arch.equals("any")) {
                builder.withQualifier("arch", arch);
            }

            // Add epoch for RPM if present
            if (format() == PackageFormat.RPM) {
                metadata().epoch().ifPresent(epoch -> {
                    if (epoch > 0) {
                        builder.withQualifier("epoch", String.valueOf(epoch));
                    }
                });
            }

            return builder.build();
        } catch (MalformedPackageURLException e) {
            // This should not happen with valid package data
            throw new IllegalStateException("Failed to build Package URL for package: " + metadata().name(), e);
        }
    }

    /**
     * Builds the version string for the PURL.
     *
     * <p>For formats with separate release fields, this combines version and release.
     * For formats where release is already part of the version string (like APK),
     * it returns the version as-is to avoid duplication.
     *
     * @param metadata the package metadata
     * @return the combined version string
     */
    private static String buildVersionString(PackageMetadata metadata) {
        String version = metadata.version();
        Optional<String> release = metadata.release();

        if (release.isPresent() && !release.get().isEmpty()) {
            String rel = release.get();
            // Don't append release if it's already part of the version string
            // This handles formats like APK where version is "1.0-r1" and release is "r1"
            if (version.endsWith("-" + rel)) {
                return version;
            }
            return version + "-" + rel;
        }
        return version;
    }
}
