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
package io.spicelabs.baharat.rpm;

import io.spicelabs.baharat.PackageEntry;
import io.spicelabs.baharat.PackageException;
import io.spicelabs.baharat.PackageFormat;
import io.spicelabs.baharat.rpm.header.Header;
import io.spicelabs.baharat.rpm.lead.Lead;
import io.spicelabs.baharat.rpm.metadata.PackageMetadata;
import io.spicelabs.baharat.rpm.payload.PayloadEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Immutable representation of a parsed RPM package.
 *
 * <p>This class provides access to all parsed components of an RPM file:
 * <ul>
 *   <li>{@link #lead()} - The legacy lead section (96 bytes, mostly obsolete)</li>
 *   <li>{@link #signatureHeader()} - Checksums and cryptographic signatures</li>
 *   <li>{@link #header()} - The main header with all package metadata</li>
 *   <li>{@link #metadata()} - Convenient accessor for common metadata fields</li>
 * </ul>
 *
 * <p>For most use cases, the {@link #metadata()} accessor provides the easiest
 * way to access package information like name, version, dependencies, and file lists.
 *
 * <p>This class also provides shortcut methods for the most commonly accessed fields:
 * <ul>
 *   <li>{@link #name()}, {@link #version()}, {@link #release()}, {@link #arch()}</li>
 *   <li>{@link #nevra()} - Full Name-Epoch:Version-Release.Arch string</li>
 *   <li>{@link #isSource()}, {@link #isBinary()} - RpmPackage type checks</li>
 * </ul>
 *
 * <p>Instances are created by {@link RpmReader#read(java.nio.file.Path)}.
 *
 * @see RpmReader
 * @see io.spicelabs.baharat.rpm.metadata.PackageMetadata
 */
public final class RpmPackage implements io.spicelabs.baharat.Package {

    private final @NotNull Lead lead;
    private final @NotNull Header signatureHeader;
    private final @NotNull Header header;
    private final @NotNull PackageMetadata metadata;
    private final @NotNull MetadataAdapter metadataAdapter;
    private final long payloadOffset;
    private final @Nullable Path sourcePath;

    /**
     * Creates a new RPM package representation.
     *
     * @param lead the lead section
     * @param signatureHeader the signature header
     * @param header the main header
     * @param payloadOffset the byte offset where the payload begins
     */
    public RpmPackage(@NotNull Lead lead, @NotNull Header signatureHeader, @NotNull Header header, long payloadOffset) {
        this(lead, signatureHeader, header, payloadOffset, null);
    }

    /**
     * Creates a new RPM package representation with source path.
     *
     * @param lead the lead section
     * @param signatureHeader the signature header
     * @param header the main header
     * @param payloadOffset the byte offset where the payload begins
     * @param sourcePath the source file path (for payload streaming)
     */
    public RpmPackage(@NotNull Lead lead, @NotNull Header signatureHeader, @NotNull Header header,
                      long payloadOffset, @Nullable Path sourcePath) {
        this.lead = lead;
        this.signatureHeader = signatureHeader;
        this.header = header;
        this.metadata = new PackageMetadata(header);
        this.metadataAdapter = new MetadataAdapter(this.metadata, sourcePath);
        this.payloadOffset = payloadOffset;
        this.sourcePath = sourcePath;
    }

    @Override
    public @NotNull PackageFormat format() {
        return PackageFormat.RPM;
    }

    @Override
    public @NotNull io.spicelabs.baharat.PackageMetadata metadata() {
        return metadataAdapter;
    }

    /**
     * Streams the payload entries of this package.
     *
     * <p>The stream must be closed after use to release resources.
     *
     * <p>Requires this package to have been read from a file {@link Path}
     * (e.g., {@code RpmReader.read(Path)} or {@code PackageReader.readRpm(Path)}),
     * which retains the source path for lazy re-reading. Packages read from an
     * {@link java.io.InputStream} have no re-readable source and throw
     * {@link PackageException} with guidance; stream such packages directly via
     * {@link RpmReader#streamPayload(java.io.InputStream)} or
     * {@link PackageReader#streamPayload(Path)} instead.
     *
     * @return a stream of payload entries
     * @throws IOException if an I/O error occurs
     * @throws PackageException if the payload cannot be read (e.g., no source path)
     */
    @Override
    public @NotNull Stream<PackageEntry> payload() throws IOException, PackageException {
        if (sourcePath == null) {
            throw new PackageException(
                    "Cannot stream payload without source path: packages read from an "
                    + "InputStream cannot stream payload lazily. Read from a Path instead, "
                    + "or stream the payload directly via streamPayload(Path) or "
                    + "streamPayload(InputStream).",
                    PackageFormat.RPM);
        }
        // Convert RPM PayloadEntry stream to common PackageEntry stream
        try {
            return RpmReader.streamPayload(sourcePath).map(this::convertPayloadEntry);
        } catch (io.spicelabs.baharat.rpm.exception.FormatException e) {
            throw new PackageException("Failed to stream RPM payload: " + e.getMessage(), PackageFormat.RPM, e);
        }
    }

    private @NotNull PackageEntry convertPayloadEntry(@NotNull PayloadEntry entry) {
        return switch (entry) {
            case PayloadEntry.FileEntry f -> new PackageEntry.FileEntry(
                    f.path(), f.mode(), f.mtime(), f.userName(), f.groupName(), f.size(), f.content());
            case PayloadEntry.DirectoryEntry d -> new PackageEntry.DirectoryEntry(
                    d.path(), d.mode(), d.mtime(), d.userName(), d.groupName());
            case PayloadEntry.SymlinkEntry s -> new PackageEntry.SymlinkEntry(
                    s.path(), s.mode(), s.mtime(), s.userName(), s.groupName(), s.target());
        };
    }

    /**
     * Returns the RPM-specific metadata.
     * Use this for RPM-specific features not available through the common metadata interface.
     *
     * @return the RPM metadata
     */
    public @NotNull PackageMetadata rpmMetadata() {
        return metadata;
    }

    /**
     * Returns the source file path.
     *
     * @return the source path, or null if created from a stream
     */
    public @Nullable Path sourcePath() {
        return sourcePath;
    }

    /**
     * Returns the lead section.
     *
     * @return the lead
     */
    public @NotNull Lead lead() {
        return lead;
    }

    /**
     * Returns the signature header.
     *
     * @return the signature header
     */
    public @NotNull Header signatureHeader() {
        return signatureHeader;
    }

    /**
     * Returns the main header.
     *
     * @return the header
     */
    public @NotNull Header header() {
        return header;
    }

    /**
     * Returns the byte offset where the payload begins in the file.
     *
     * @return the payload offset
     */
    public long payloadOffset() {
        return payloadOffset;
    }

    /**
     * Returns the RPM format version (e.g., "3.0" or "4.0").
     *
     * @return the version string
     */
    public @NotNull String formatVersion() {
        return lead.version();
    }

    /**
     * Returns true if this is a source RPM.
     *
     * @return true if source package
     */
    public boolean isSource() {
        return lead.isSource();
    }

    /**
     * Returns true if this is a binary RPM.
     *
     * @return true if binary package
     */
    public boolean isBinary() {
        return lead.isBinary();
    }

    /**
     * Returns the package name.
     *
     * @return the name
     */
    public @NotNull String name() {
        return metadata.name();
    }

    /**
     * Returns the package version.
     *
     * @return the version
     */
    public @NotNull String version() {
        return metadata.version();
    }

    /**
     * Returns the package release.
     *
     * @return the release
     */
    public @NotNull String release() {
        return metadata.release();
    }

    /**
     * Returns the package architecture.
     *
     * @return the architecture
     */
    public @NotNull String arch() {
        return metadata.arch();
    }

    /**
     * Returns the full NEVRA string (Name-Epoch:Version-Release.Arch).
     *
     * @return the NEVRA string
     */
    public @NotNull String nevra() {
        return metadata.nevra();
    }

    // ── PURL namespace inference ──────────────────────────────────────────

    private static final Pattern FC_DISTTAG = Pattern.compile("\\.fc\\d+");
    private static final Pattern EL_DISTTAG = Pattern.compile("\\.el\\d+");
    private static final Pattern SUSE_DISTTAG = Pattern.compile("\\.(?:suse|opensuse|sles)");
    private static final Pattern AMZN_DISTTAG = Pattern.compile("\\.amzn\\d*");
    private static final Pattern OL_DISTTAG = Pattern.compile("\\.ol\\d+");
    private static final Pattern AL_DISTTAG = Pattern.compile("\\.al\\d+");
    private static final Pattern ROCKY_DISTTAG = Pattern.compile("\\.rocky");
    private static final Pattern MAGEIA_DISTTAG = Pattern.compile("\\.mageia");

    /**
     * Infers the PURL namespace (distribution) from RPM metadata.
     *
     * <p>Per the <a href="https://github.com/package-url/purl-spec">pURL spec</a>,
     * the namespace for {@code rpm} packages is the distribution name
     * (e.g., {@code fedora}, {@code opensuse}, {@code centos}), <em>not</em> the
     * vendor. The vendor field often contains a company name with contact info
     * (e.g., {@code "SUSE LLC <https://www.suse.com/>"}) which is not a valid
     * namespace.
     *
     * <p>This method consults three sources, in order of reliability:
     * <ol>
     *   <li><b>Release string</b> — RPM releases carry standardized distro tags
     *       such as {@code fc40} (Fedora), {@code el8} (Enterprise Linux),
     *       {@code suse}, {@code amzn}, {@code ol9}, {@code al9},
     *       {@code rocky}, {@code mageia}.</li>
     *   <li><b>Distribution field</b> — the RPM {@code DISTRIBUTION} header tag.
     *       This disambiguates {@code el<N>} packages (e.g., "CentOS" vs
     *       "Red Hat") and may be the only source for packages whose release
     *       lacks a recognizable tag.</li>
     *   <li><b>Source path / filename</b> — a last-resort heuristic matching the
     *       pattern used by {@code DebPackage} and other formats.</li>
     * </ol>
     *
     * @param release      the RPM release string (e.g., {@code "1.fc40"})
     * @param distribution the RPM distribution field, or empty string if unset
     * @param sourcePath   the source file path, or empty string if unknown
     * @return an Optional containing the inferred namespace, or empty if unknown
     */
    public static @NotNull Optional<String> inferNamespace(
            @NotNull String release, @NotNull String distribution, @NotNull String sourcePath) {
        String rel = release.toLowerCase(Locale.ROOT);

        // 1. Unambiguous release-string distro tags
        if (FC_DISTTAG.matcher(rel).find()) {
            return Optional.of("fedora");
        }
        if (SUSE_DISTTAG.matcher(rel).find()) {
            return Optional.of("opensuse");
        }
        if (AMZN_DISTTAG.matcher(rel).find()) {
            return Optional.of("amazon");
        }
        if (OL_DISTTAG.matcher(rel).find()) {
            return Optional.of("oraclelinux");
        }
        if (AL_DISTTAG.matcher(rel).find()) {
            return Optional.of("alma");
        }
        if (ROCKY_DISTTAG.matcher(rel).find()) {
            return Optional.of("rocky");
        }
        if (MAGEIA_DISTTAG.matcher(rel).find()) {
            return Optional.of("mageia");
        }

        // 2. el<N> is ambiguous — use the distribution field to disambiguate
        if (EL_DISTTAG.matcher(rel).find()) {
            Optional<String> fromDist = inferFromDistribution(distribution);
            if (fromDist.isPresent()) {
                return fromDist;
            }
            return Optional.of("rhel");
        }

        // 3. Distribution field (may be the only source for some packages)
        Optional<String> fromDist = inferFromDistribution(distribution);
        if (fromDist.isPresent()) {
            return fromDist;
        }

        // 4. Source path / filename
        return inferFromPath(sourcePath);
    }

    /**
     * Infers the PURL namespace from a filename or path.
     *
     * <p>This is a convenience overload that examines the filename for
     * distribution indicators. It is less reliable than
     * {@link #inferNamespace(String, String, String)} because it lacks the
     * release string and distribution field.
     *
     * @param filename the filename or path to examine
     * @return an Optional containing the inferred namespace, or empty if unknown
     */
    public static @NotNull Optional<String> inferNamespace(@NotNull String filename) {
        return inferFromPath(filename);
    }

    /**
     * Infers the PURL namespace from this package's metadata and source path.
     *
     * @return an Optional containing the inferred namespace, or empty if unknown
     */
    public @NotNull Optional<String> inferNamespace() {
        return inferNamespace(
                metadata.release(),
                metadata.distribution().orElse(""),
                sourcePath == null ? "" : sourcePath.toString());
    }

    private static @NotNull Optional<String> inferFromDistribution(@NotNull String distribution) {
        if (distribution.isEmpty()) {
            return Optional.empty();
        }
        String lower = distribution.toLowerCase(Locale.ROOT);
        if (lower.contains("fedora")) return Optional.of("fedora");
        if (lower.contains("centos")) return Optional.of("centos");
        if (lower.contains("red hat") || lower.contains("rhel")) return Optional.of("rhel");
        if (lower.contains("opensuse")) return Optional.of("opensuse");
        if (lower.contains("suse")) return Optional.of("opensuse");
        if (lower.contains("amazon")) return Optional.of("amazon");
        if (lower.contains("oracle")) return Optional.of("oraclelinux");
        if (lower.contains("alma")) return Optional.of("alma");
        if (lower.contains("rocky")) return Optional.of("rocky");
        if (lower.contains("mageia")) return Optional.of("mageia");
        return Optional.empty();
    }

    private static @NotNull Optional<String> inferFromPath(@NotNull String path) {
        if (path.isEmpty()) {
            return Optional.empty();
        }
        String lower = path.toLowerCase(Locale.ROOT);
        if (FC_DISTTAG.matcher(lower).find() || lower.contains("fedora")) return Optional.of("fedora");
        if (lower.contains("centos")) return Optional.of("centos");
        if (EL_DISTTAG.matcher(lower).find() || lower.contains("redhat") || lower.contains("rhel")) {
            return Optional.of("rhel");
        }
        if (SUSE_DISTTAG.matcher(lower).find() || lower.contains("opensuse") || lower.contains("suse")) {
            return Optional.of("opensuse");
        }
        if (AMZN_DISTTAG.matcher(lower).find() || lower.contains("amazon")) return Optional.of("amazon");
        if (OL_DISTTAG.matcher(lower).find() || lower.contains("oracle")) return Optional.of("oraclelinux");
        if (AL_DISTTAG.matcher(lower).find() || lower.contains("alma")) return Optional.of("alma");
        if (ROCKY_DISTTAG.matcher(lower).find() || lower.contains("rocky")) return Optional.of("rocky");
        if (MAGEIA_DISTTAG.matcher(lower).find() || lower.contains("mageia")) return Optional.of("mageia");
        return Optional.empty();
    }

    @Override
    public @NotNull String toString() {
        return "RpmPackage{" + nevra() + "}";
    }
}
