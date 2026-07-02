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

import io.spicelabs.baharat.Package;
import io.spicelabs.baharat.PackageEntry;
import io.spicelabs.baharat.PackageException;
import io.spicelabs.baharat.PackageFormat;
import io.spicelabs.baharat.PackageMetadata;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Represents a parsed Debian (.deb) package.
 *
 * <p>DEB files are ar archives containing:
 * <ul>
 *   <li>{@code debian-binary} - Version string "2.0\n"</li>
 *   <li>{@code control.tar.*} - Package metadata (control file, scripts, etc.)</li>
 *   <li>{@code data.tar.*} - Package payload (actual files to install)</li>
 * </ul>
 *
 * <h2>Control File Format</h2>
 * <p>The control file uses RFC 822-like format:
 * <pre>
 * Package: nginx
 * Version: 1.24.0-1
 * Architecture: amd64
 * Maintainer: Debian Nginx Team &lt;team@example.org&gt;
 * Depends: libc6 (&gt;= 2.34), libpcre3
 * Description: High performance web server
 *  Nginx is a web server that can also be used as a
 *  reverse proxy, load balancer, and HTTP cache.
 * </pre>
 *
 * @see DebReader
 * @see DebMetadata
 */
public final class DebPackage implements Package {

    /**
     * Pattern to detect Debian version indicators like "deb10", "deb11", "deb12".
     */
    private static final Pattern DEBIAN_VERSION_PATTERN = Pattern.compile("\\+deb\\d+");

    private final @NotNull DebMetadata metadata;
    private final @Nullable Path sourcePath;
    private final @NotNull String debianBinaryVersion;

    /**
     * Creates a new DEB package representation.
     *
     * @param metadata the package metadata
     * @param sourcePath the source file path (for payload streaming)
     * @param debianBinaryVersion the debian-binary version string
     */
    public DebPackage(@NotNull DebMetadata metadata, @Nullable Path sourcePath, @NotNull String debianBinaryVersion) {
        this.metadata = metadata;
        this.sourcePath = sourcePath;
        this.debianBinaryVersion = debianBinaryVersion;
    }

    @Override
    public @NotNull PackageFormat format() {
        return PackageFormat.DEB;
    }

    @Override
    public @NotNull PackageMetadata metadata() {
        return metadata;
    }

    /**
     * Returns the DEB-specific metadata.
     *
     * @return the DEB metadata
     */
    public @NotNull DebMetadata debMetadata() {
        return metadata;
    }

    @Override
    public @NotNull Stream<PackageEntry> payload() throws IOException, PackageException {
        if (sourcePath == null) {
            throw new PackageException("Cannot stream payload without source path", PackageFormat.DEB);
        }
        return DebReader.streamPayload(sourcePath);
    }

    /**
     * Returns the debian-binary version string (usually "2.0").
     *
     * @return the version string
     */
    public @NotNull String debianBinaryVersion() {
        return debianBinaryVersion;
    }

    /**
     * Returns the source file path.
     *
     * @return the source path, or null if created from a stream
     */
    public @Nullable Path sourcePath() {
        return sourcePath;
    }

    @Override
    public @NotNull String toString() {
        return "DebPackage{" + name() + "_" + version() + "_" + arch() + "}";
    }

    /**
     * Infers the PURL namespace (distribution) from a filename or path.
     *
     * <p>This heuristic examines the filename for distribution indicators:
     * <ul>
     *   <li>Files containing "ubuntu" → "ubuntu"</li>
     *   <li>Files containing "debian" or version patterns like "+deb12" → "debian"</li>
     *   <li>Files containing "mint" → "linuxmint"</li>
     *   <li>Files containing "raspbian" → "raspbian"</li>
     *   <li>Otherwise → empty (no namespace)</li>
     * </ul>
     *
     * <p>Example usage with Goat Rodeo:
     * <pre>{@code
     * Purl purl = pkg.purl();
     * }</pre>
     *
     * @param filename the filename or path to examine
     * @return an Optional containing the inferred namespace, or empty if unknown
     */
    public static @NotNull Optional<String> inferNamespace(@NotNull String filename) {
        String lower = filename.toLowerCase();
        if (lower.contains("ubuntu")) {
            return Optional.of("ubuntu");
        }
        if (lower.contains("debian") || DEBIAN_VERSION_PATTERN.matcher(lower).find()) {
            return Optional.of("debian");
        }
        if (lower.contains("mint")) {
            return Optional.of("linuxmint");
        }
        if (lower.contains("raspbian")) {
            return Optional.of("raspbian");
        }
        return Optional.empty();
    }

    /**
     * Infers the PURL namespace from this package's source path.
     *
     * @return an Optional containing the inferred namespace, or empty if unknown
     */
    public @NotNull Optional<String> inferNamespace() {
        if (sourcePath != null) {
            return inferNamespace(sourcePath.toString());
        }
        return Optional.empty();
    }
}
