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
package io.spicelabs.baharat.rpm.delta;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Represents a parsed Delta RPM (DRPM) file.
 *
 * <p>Delta RPMs contain the differences between two versions of an RPM package,
 * allowing for smaller downloads when updating packages. They are commonly used
 * by package managers like DNF/YUM to reduce bandwidth usage.
 *
 * <p>A delta RPM contains:
 * <ul>
 *   <li>Source package information (the package being upgraded from)</li>
 *   <li>Target package information (the package being upgraded to)</li>
 *   <li>The delta payload (compressed differences)</li>
 *   <li>Sequence information for validation</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * Delta drpm = DeltaReader.read(Path.of("package-1.0-2.0.drpm"));
 * System.out.println("From: " + drpm.sourceNevra());
 * System.out.println("To: " + drpm.targetNevra());
 * System.out.println("Delta size: " + drpm.deltaSize() + " bytes");
 * }</pre>
 *
 * @see DeltaReader
 */
public record Delta(
        @NotNull String version,
        @NotNull String sourceNevra,
        @NotNull String targetNevra,
        @NotNull String sequence,
        @NotNull Optional<String> sourceRpmDigest,
        @NotNull Optional<String> targetRpmDigest,
        long deltaSize,
        long targetSize,
        @NotNull DeltaType deltaType,
        @NotNull CompressionMethod compressionMethod
) {

    /**
     * Returns the source package name (without version/release/arch).
     *
     * @return the source package name
     */
    public @NotNull String sourceName() {
        return extractName(sourceNevra);
    }

    /**
     * Returns the target package name (without version/release/arch).
     *
     * @return the target package name
     */
    public @NotNull String targetName() {
        return extractName(targetNevra);
    }

    /**
     * Returns true if this is a valid delta (source and target have same name).
     *
     * @return true if valid
     */
    public boolean isValid() {
        return sourceName().equals(targetName());
    }

    private static String extractName(String nevra) {
        // NEVRA format: name-[epoch:]version-release.arch
        // Find the last two dashes to extract name
        int lastDash = nevra.lastIndexOf('-');
        if (lastDash > 0) {
            int secondLastDash = nevra.lastIndexOf('-', lastDash - 1);
            if (secondLastDash > 0) {
                return nevra.substring(0, secondLastDash);
            }
        }
        return nevra;
    }

    /**
     * The type of delta algorithm used.
     */
    public enum DeltaType {
        /** Standard delta format */
        STANDARD,
        /** RPM-only delta (header changes only) */
        RPM_ONLY,
        /** Unknown delta type */
        UNKNOWN
    }

    /**
     * The compression method used for the delta payload.
     */
    public enum CompressionMethod {
        /** No compression */
        NONE,
        /** gzip compression */
        GZIP,
        /** bzip2 compression */
        BZIP2,
        /** xz/lzma compression */
        XZ,
        /** zstd compression */
        ZSTD,
        /** Unknown compression */
        UNKNOWN
    }
}
