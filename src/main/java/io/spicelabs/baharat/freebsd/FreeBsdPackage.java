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
package io.spicelabs.baharat.freebsd;

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
 * Represents a parsed FreeBSD pkg (.pkg, .txz) package.
 *
 * <p>FreeBSD packages are compressed tar archives containing:
 * <ul>
 *   <li>{@code +COMPACT_MANIFEST} - Compact JSON metadata</li>
 *   <li>{@code +MANIFEST} - Full JSON metadata with file checksums</li>
 *   <li>Payload files under their installation paths</li>
 * </ul>
 *
 * <h2>Compression Formats</h2>
 * <ul>
 *   <li>{@code .pkg} or {@code .tzst} - Zstandard compressed (modern default)</li>
 *   <li>{@code .txz} - XZ compressed (previous default)</li>
 *   <li>{@code .tbz} - Bzip2 compressed</li>
 *   <li>{@code .tgz} - Gzip compressed</li>
 * </ul>
 *
 * @see FreeBsdReader
 * @see FreeBsdMetadata
 */
public final class FreeBsdPackage implements Package {

    private final @NotNull FreeBsdMetadata metadata;
    private final @Nullable Path sourcePath;

    /**
     * Creates a new FreeBSD package representation.
     *
     * @param metadata the package metadata
     * @param sourcePath the source file path (for payload streaming)
     */
    public FreeBsdPackage(@NotNull FreeBsdMetadata metadata, @Nullable Path sourcePath) {
        this.metadata = metadata;
        this.sourcePath = sourcePath;
    }

    @Override
    public @NotNull PackageFormat format() {
        return PackageFormat.FREEBSD_PKG;
    }

    @Override
    public @NotNull PackageMetadata metadata() {
        return metadata;
    }

    /**
     * Returns the FreeBSD-specific metadata.
     *
     * @return the FreeBSD metadata
     */
    public @NotNull FreeBsdMetadata freeBsdMetadata() {
        return metadata;
    }

    @Override
    public @NotNull Stream<PackageEntry> payload() throws IOException, PackageException {
        if (sourcePath == null) {
            throw new PackageException("Cannot stream payload without source path", PackageFormat.FREEBSD_PKG);
        }
        return FreeBsdReader.streamPayload(sourcePath);
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
        return "FreeBsdPackage{" + name() + "-" + version() + "}";
    }

    /**
     * Infers the PURL namespace for FreeBSD packages.
     *
     * <p>FreeBSD packages may also be used by DragonFlyBSD or other BSD variants,
     * but typically returns "freebsd".
     *
     * @param filename the filename or path to examine
     * @return an Optional containing the inferred namespace
     */
    public static @NotNull Optional<String> inferNamespace(@NotNull String filename) {
        String lower = filename.toLowerCase();
        if (lower.contains("dragonfly")) {
            return Optional.of("dragonflybsd");
        }
        return Optional.of("freebsd");
    }

    /**
     * Infers the PURL namespace for this package.
     *
     * @return an Optional containing the inferred namespace
     */
    public @NotNull Optional<String> inferNamespace() {
        if (sourcePath != null) {
            return inferNamespace(sourcePath.toString());
        }
        return Optional.of("freebsd");
    }
}
