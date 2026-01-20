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
package io.spicelabs.baharat.apk;

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
 * Represents a parsed Alpine Linux (.apk) package.
 *
 * <p>APK files are gzip-compressed tar archives containing:
 * <ul>
 *   <li>{@code .SIGN.RSA.*.pub} - Package signature (optional)</li>
 *   <li>{@code .PKGINFO} - Package metadata in key=value format</li>
 *   <li>{@code .trigger} - Trigger scripts (optional)</li>
 *   <li>Payload files under their installation paths</li>
 * </ul>
 *
 * <h2>Version Format</h2>
 * <p>APK versions follow the pattern: {@code version-rrelease}
 * <p>Example: {@code 1.24.0-r1}
 *
 * @see ApkReader
 * @see ApkMetadata
 */
public final class ApkPackage implements Package {

    private final @NotNull ApkMetadata metadata;
    private final @Nullable Path sourcePath;

    /**
     * Creates a new APK package representation.
     *
     * @param metadata the package metadata
     * @param sourcePath the source file path (for payload streaming)
     */
    public ApkPackage(@NotNull ApkMetadata metadata, @Nullable Path sourcePath) {
        this.metadata = metadata;
        this.sourcePath = sourcePath;
    }

    @Override
    public @NotNull PackageFormat format() {
        return PackageFormat.APK;
    }

    @Override
    public @NotNull PackageMetadata metadata() {
        return metadata;
    }

    /**
     * Returns the APK-specific metadata.
     *
     * @return the APK metadata
     */
    public @NotNull ApkMetadata apkMetadata() {
        return metadata;
    }

    @Override
    public @NotNull Stream<PackageEntry> payload() throws IOException, PackageException {
        if (sourcePath == null) {
            throw new PackageException("Cannot stream payload without source path", PackageFormat.APK);
        }
        return ApkReader.streamPayload(sourcePath);
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
        return "ApkPackage{" + name() + "-" + version() + "}";
    }

    /**
     * Infers the PURL namespace (distribution) from a filename or path.
     *
     * <p>APK packages are typically from Alpine Linux, but there are variants:
     * <ul>
     *   <li>Files containing "alpine" → "alpine"</li>
     *   <li>Files containing "postmarket" → "postmarketos"</li>
     *   <li>Otherwise → "alpine" (default for APK format)</li>
     * </ul>
     *
     * @param filename the filename or path to examine
     * @return an Optional containing the inferred namespace
     */
    public static @NotNull Optional<String> inferNamespace(@NotNull String filename) {
        String lower = filename.toLowerCase();
        if (lower.contains("postmarket")) {
            return Optional.of("postmarketos");
        }
        // Default to alpine for APK packages
        return Optional.of("alpine");
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
        return Optional.of("alpine");
    }
}
