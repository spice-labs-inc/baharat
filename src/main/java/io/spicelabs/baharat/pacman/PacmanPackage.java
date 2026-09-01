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
package io.spicelabs.baharat.pacman;

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
import java.util.stream.Stream;

/**
 * Represents a parsed Pacman/ALPM (.pkg.tar.*) package.
 *
 * <p>Pacman packages are compressed tar archives containing:
 * <ul>
 *   <li>{@code .PKGINFO} - Package metadata in key=value format</li>
 *   <li>{@code .BUILDINFO} - Build information</li>
 *   <li>{@code .MTREE} - File manifest with checksums</li>
 *   <li>{@code .INSTALL} - Install scripts (optional)</li>
 *   <li>Payload files under their installation paths</li>
 * </ul>
 *
 * <h2>Compression Formats</h2>
 * <ul>
 *   <li>{@code .pkg.tar.zst} - Current default (Arch Linux 2019+)</li>
 *   <li>{@code .pkg.tar.xz} - Previous default (2010-2019)</li>
 *   <li>{@code .pkg.tar.gz} - Original default (pre-2010)</li>
 * </ul>
 *
 * @see PacmanReader
 * @see PacmanMetadata
 */
public final class PacmanPackage implements Package {

    private final @NotNull PacmanMetadata metadata;
    private final @Nullable Path sourcePath;

    /**
     * Creates a new Pacman package representation.
     *
     * @param metadata the package metadata
     * @param sourcePath the source file path (for payload streaming)
     */
    public PacmanPackage(@NotNull PacmanMetadata metadata, @Nullable Path sourcePath) {
        this.metadata = metadata;
        this.sourcePath = sourcePath;
    }

    @Override
    public @NotNull PackageFormat format() {
        return PackageFormat.PACMAN;
    }

    @Override
    public @NotNull PackageMetadata metadata() {
        return metadata;
    }

    /**
     * Returns the Pacman-specific metadata.
     *
     * @return the Pacman metadata
     */
    public @NotNull PacmanMetadata pacmanMetadata() {
        return metadata;
    }

    @Override
    /**
     * Streams the payload entries of this package.
     *
     * <p>The stream must be closed after use to release resources.
     *
     * <p>Requires this package to have been read from a file {@link Path} (e.g.,
     * {@code PacmanReader.read(Path)} or {@code PackageReader.readPacman(Path)}),
     * which retains the source path for lazy re-reading. Packages read from an
     * {@link java.io.InputStream} have no re-readable source and throw
     * {@link PackageException} with guidance; stream such packages directly via
     * {@link PacmanReader#streamPayload(java.io.InputStream, String)} or
     * {@link PackageReader#streamPayload(Path)} instead.
     *
     * @return a stream of payload entries
     * @throws IOException if an I/O error occurs
     * @throws PackageException if the payload cannot be read (e.g., no source path)
     */
    public @NotNull Stream<PackageEntry> payload() throws IOException, PackageException {
        if (sourcePath == null) {
            throw new PackageException(
                    "Cannot stream payload without source path: packages read from an "
                    + "InputStream cannot stream payload lazily. Read from a Path instead, "
                    + "or stream the payload directly via streamPayload(Path) or "
                    + "streamPayload(InputStream).",
                    PackageFormat.PACMAN);
        }
        return PacmanReader.streamPayload(sourcePath);
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
        return "PacmanPackage{" + name() + "-" + version() + "}";
    }

    /**
     * Infers the PURL namespace (distribution) from a filename or path.
     *
     * <p>Pacman packages are used by several distributions:
     * <ul>
     *   <li>Files containing "arch" → "arch"</li>
     *   <li>Files containing "manjaro" → "manjaro"</li>
     *   <li>Files containing "endeavour" → "endeavouros"</li>
     *   <li>Files containing "artix" → "artix"</li>
     *   <li>Otherwise → "arch" (default for Pacman format)</li>
     * </ul>
     *
     * @param filename the filename or path to examine
     * @return an Optional containing the inferred namespace
     */
    public static @NotNull Optional<String> inferNamespace(@NotNull String filename) {
        String lower = filename.toLowerCase();
        if (lower.contains("manjaro")) {
            return Optional.of("manjaro");
        }
        if (lower.contains("endeavour")) {
            return Optional.of("endeavouros");
        }
        if (lower.contains("artix")) {
            return Optional.of("artix");
        }
        // Default to arch for Pacman packages
        return Optional.of("arch");
    }

    /**
     * Infers the PURL namespace from this package's source path.
     *
     * @return an Optional containing the inferred namespace
     */
    public @NotNull Optional<String> inferNamespace() {
        if (sourcePath != null) {
            return inferNamespace(sourcePath.toString());
        }
        return Optional.of("arch");
    }
}
