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

import io.spicelabs.baharat.common.Dependency;
import io.spicelabs.coordinates.Purl;
import io.spicelabs.baharat.common.FileInfo;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Common metadata interface for all package formats.
 *
 * <p>This interface provides access to the most common metadata fields that are
 * present across different package formats. Each field is mapped from the
 * format-specific representation to a common model.
 *
 * <h2>Field Mapping Across Formats</h2>
 * <table>
 *   <tr><th>Field</th><th>RPM</th><th>DEB</th><th>Pacman</th><th>APK</th><th>FreeBSD</th><th>OpenBSD</th></tr>
 *   <tr><td>name</td><td>NAME</td><td>Package</td><td>pkgname</td><td>pkgname</td><td>name</td><td>@name</td></tr>
 *   <tr><td>version</td><td>VERSION</td><td>Version</td><td>pkgver</td><td>pkgver</td><td>version</td><td>@name</td></tr>
 *   <tr><td>release</td><td>RELEASE</td><td>-</td><td>-</td><td>-r suffix</td><td>-</td><td>-</td></tr>
 *   <tr><td>arch</td><td>ARCH</td><td>Architecture</td><td>arch</td><td>arch</td><td>arch</td><td>-</td></tr>
 *   <tr><td>description</td><td>DESCRIPTION</td><td>Description</td><td>pkgdesc</td><td>pkgdesc</td><td>comment</td><td>+DESC</td></tr>
 *   <tr><td>maintainer</td><td>PACKAGER</td><td>Maintainer</td><td>packager</td><td>maintainer</td><td>maintainer</td><td>-</td></tr>
 *   <tr><td>url</td><td>URL</td><td>Homepage</td><td>url</td><td>url</td><td>www</td><td>-</td></tr>
 *   <tr><td>license</td><td>LICENSE</td><td>-</td><td>license</td><td>license</td><td>licenses</td><td>-</td></tr>
 * </table>
 *
 * @see Package
 * @see Dependency
 * @see FileInfo
 */
public interface PackageMetadata {

    /**
     * Returns the package name.
     *
     * @return the package name
     */
    @NotNull String name();

    /**
     * Returns the package version.
     *
     * @return the version string
     */
    @NotNull String version();

    /**
     * Returns the package release (RPM-specific concept).
     *
     * <p>Many formats don't have a separate release field - for those,
     * the release may be embedded in the version or empty.
     *
     * @return an Optional containing the release, or empty if not applicable
     */
    default @NotNull Optional<String> release() {
        return Optional.empty();
    }

    /**
     * Returns the package epoch (RPM-specific concept).
     *
     * <p>The epoch is used to handle version number resets. Most formats
     * don't support this concept.
     *
     * @return an Optional containing the epoch, or empty if not set
     */
    default @NotNull Optional<Integer> epoch() {
        return Optional.empty();
    }

    /**
     * Returns the package architecture.
     *
     * @return the architecture (e.g., "x86_64", "amd64", "noarch", "all")
     */
    @NotNull String arch();

    /**
     * Returns the package summary/short description.
     *
     * @return an Optional containing the summary, or empty if not set
     */
    default @NotNull Optional<String> summary() {
        return Optional.empty();
    }

    /**
     * Returns the package description.
     *
     * @return an Optional containing the description, or empty if not set
     */
    default @NotNull Optional<String> description() {
        return Optional.empty();
    }

    /**
     * Returns the package maintainer or packager.
     *
     * @return an Optional containing the maintainer, or empty if not set
     */
    default @NotNull Optional<String> maintainer() {
        return Optional.empty();
    }

    /**
     * Returns the package URL/homepage.
     *
     * @return an Optional containing the URL, or empty if not set
     */
    default @NotNull Optional<String> url() {
        return Optional.empty();
    }

    /**
     * Returns the package license.
     *
     * @return an Optional containing the license, or empty if not set
     */
    default @NotNull Optional<String> license() {
        return Optional.empty();
    }

    /**
     * Returns the installed size in bytes.
     *
     * @return the installed size
     */
    long installedSize();

    /**
     * Returns the build timestamp.
     *
     * @return an Optional containing the build time, or empty if not available
     */
    default @NotNull Optional<Instant> buildTime() {
        return Optional.empty();
    }

    /**
     * Returns the list of package dependencies.
     *
     * @return an unmodifiable list of dependencies
     */
    default @NotNull List<Dependency> dependencies() {
        return Collections.emptyList();
    }

    /**
     * Returns the list of capabilities this package provides.
     *
     * @return an unmodifiable list of provides
     */
    default @NotNull List<Dependency> provides() {
        return Collections.emptyList();
    }

    /**
     * Returns the list of files in this package.
     *
     * @return an unmodifiable list of file information
     */
    default @NotNull List<FileInfo> files() {
        return Collections.emptyList();
    }

    /**
     * Returns the vendor or distribution name.
     *
     * @return an Optional containing the vendor, or empty if not set
     */
    default @NotNull Optional<String> vendor() {
        return Optional.empty();
    }

    /**
     * Returns the package group or section.
     *
     * @return an Optional containing the group, or empty if not set
     */
    default @NotNull Optional<String> group() {
        return Optional.empty();
    }

    /**
     * Returns the Package URL (PURL) for this package.
     *
     * <p>Package URL is a standardized format for identifying software packages
     * across different ecosystems. The format is:
     * {@code pkg:<type>/<namespace>/<name>@<version>?<qualifiers>}
     *
     * <p>Examples:
     * <ul>
     *   <li>RPM: {@code pkg:rpm/fedora/curl@7.50.3-1.fc25?arch=x86_64}</li>
     *   <li>DEB: {@code pkg:deb/debian/curl@7.50.3-1?arch=amd64}</li>
     *   <li>APK: {@code pkg:apk/alpine/curl@7.79.1-r0?arch=x86_64}</li>
     *   <li>Pacman: {@code pkg:alpm/arch/curl@7.79.1-1?arch=x86_64}</li>
     *   <li>FreeBSD: {@code pkg:freebsd/curl@7.79.1?arch=amd64}</li>
     *   <li>OpenBSD: {@code pkg:openbsd/curl@7.79.1}</li>
     * </ul>
     *
     * @return the Package URL object
     * @see <a href="https://github.com/package-url/purl-spec">Package URL Specification</a>
     * @see Purl
     */
    @NotNull Purl purl();
}
