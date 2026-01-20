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
        this.metadataAdapter = new MetadataAdapter(this.metadata);
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

    @Override
    public @NotNull Stream<PackageEntry> payload() throws IOException, PackageException {
        if (sourcePath == null) {
            throw new PackageException("Cannot stream payload without source path", PackageFormat.RPM);
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

    @Override
    public @NotNull String toString() {
        return "RpmPackage{" + nevra() + "}";
    }
}
