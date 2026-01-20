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
package io.spicelabs.baharat.openbsd;

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
 * Represents a parsed OpenBSD pkg (.tgz) package.
 *
 * <p>OpenBSD packages are gzip-compressed tar archives containing:
 * <ul>
 *   <li>{@code +CONTENTS} - Packing list with checksums and dependencies</li>
 *   <li>{@code +DESC} - Package description</li>
 *   <li>{@code +COMMENT} - One-line comment (optional)</li>
 *   <li>Payload files under their installation paths</li>
 * </ul>
 *
 * <h2>Contents Format</h2>
 * <pre>
 * &#64;name nginx-1.24.0
 * &#64;depend www/pcre2:pcre2-*:pcre2-10.42
 * &#64;pkgpath www/nginx
 * &#64;sha /usr/local/sbin/nginx=abc123...
 * &#64;size /usr/local/sbin/nginx=1048576
 * </pre>
 *
 * @see OpenBsdReader
 * @see OpenBsdMetadata
 */
public final class OpenBsdPackage implements Package {

    private final @NotNull OpenBsdMetadata metadata;
    private final @Nullable Path sourcePath;

    /**
     * Creates a new OpenBSD package representation.
     *
     * @param metadata the package metadata
     * @param sourcePath the source file path (for payload streaming)
     */
    public OpenBsdPackage(@NotNull OpenBsdMetadata metadata, @Nullable Path sourcePath) {
        this.metadata = metadata;
        this.sourcePath = sourcePath;
    }

    @Override
    public @NotNull PackageFormat format() {
        return PackageFormat.OPENBSD_PKG;
    }

    @Override
    public @NotNull PackageMetadata metadata() {
        return metadata;
    }

    /**
     * Returns the OpenBSD-specific metadata.
     *
     * @return the OpenBSD metadata
     */
    public @NotNull OpenBsdMetadata openBsdMetadata() {
        return metadata;
    }

    @Override
    public @NotNull Stream<PackageEntry> payload() throws IOException, PackageException {
        if (sourcePath == null) {
            throw new PackageException("Cannot stream payload without source path", PackageFormat.OPENBSD_PKG);
        }
        return OpenBsdReader.streamPayload(sourcePath);
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
        return "OpenBsdPackage{" + name() + "-" + version() + "}";
    }

    /**
     * Infers the PURL namespace for OpenBSD packages.
     *
     * <p>OpenBSD packages always have "openbsd" as the namespace since
     * they are specific to OpenBSD.
     *
     * @param filename the filename or path (not used, always returns openbsd)
     * @return an Optional containing "openbsd"
     */
    public static @NotNull Optional<String> inferNamespace(@NotNull String filename) {
        return Optional.of("openbsd");
    }

    /**
     * Infers the PURL namespace for this package.
     *
     * @return an Optional containing "openbsd"
     */
    public @NotNull Optional<String> inferNamespace() {
        return Optional.of("openbsd");
    }
}
