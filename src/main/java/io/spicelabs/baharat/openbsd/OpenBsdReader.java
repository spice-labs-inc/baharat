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

import io.spicelabs.baharat.BaharatStreamException;
import io.spicelabs.baharat.PackageEntry;
import io.spicelabs.baharat.PackageException;
import io.spicelabs.baharat.PackageFormat;
import io.spicelabs.baharat.adapter.InputStreamSource;
import io.spicelabs.baharat.common.BudgetLimits;
import io.spicelabs.baharat.common.CountedLimitedInputStream;
import io.spicelabs.baharat.common.FileInfo;
import io.spicelabs.baharat.common.SecurityUtils;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.GZIPInputStream;

/**
 * Reader for OpenBSD pkg (.tgz) package files.
 *
 * <p>OpenBSD packages are gzip-compressed tar archives containing:
 * <ul>
 *   <li>{@code +CONTENTS} - Packing list with checksums and dependencies</li>
 *   <li>{@code +DESC} - Package description</li>
 *   <li>{@code +COMMENT} - One-line comment (optional)</li>
 *   <li>Payload files at their installation paths</li>
 * </ul>
 *
 * @see OpenBsdPackage
 * @see OpenBsdMetadata
 */
public final class OpenBsdReader {

    private static final Logger log = LoggerFactory.getLogger(OpenBsdReader.class);

    private OpenBsdReader() {
        // Utility class
    }

    /**
     * Reads an OpenBSD package from a file path.
     *
     * @param path the path to the package file
     * @return the parsed package
     * @throws PackageException if the package cannot be read
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull OpenBsdPackage read(@NotNull Path path) throws PackageException, IOException {
        log.debug("Reading OpenBSD package from: {}", path);

        try (InputStream in = new BufferedInputStream(Files.newInputStream(path))) {
            OpenBsdPackage pkg = readFromStream(in, path.toString());
            return new OpenBsdPackage(pkg.openBsdMetadata(), path);
        }
    }

    /**
     * Reads an OpenBSD package from an input stream.
     *
     * @param input the input stream containing the package
     * @param name the nominal name/path (for logging)
     * @return the parsed package
     * @throws PackageException if the package cannot be read
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull OpenBsdPackage read(@NotNull InputStream input, @NotNull String name)
            throws PackageException, IOException {
        log.debug("Reading OpenBSD package from stream: {}", name);
        return readFromStream(input, name);
    }

    /**
     * Reads an OpenBSD package from an InputStreamSource.
     *
     * @param source the input stream source
     * @return the parsed package
     * @throws PackageException if the package cannot be read
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull OpenBsdPackage read(@NotNull InputStreamSource source)
            throws PackageException, IOException {
        log.debug("Reading OpenBSD package from source: {}", source.path());
        try (InputStream in = source.openStream()) {
            return readFromStream(in, source.path());
        }
    }

    private static @NotNull OpenBsdPackage readFromStream(@NotNull InputStream input, @NotNull String name)
            throws PackageException, IOException {
        return readFromStream(input, name, BudgetLimits.DEFAULT);
    }

    /**
     * Package-private overload with explicit budgets (Fresh Scent Phase 5, finding B2).
     */
    static @NotNull OpenBsdPackage readFromStream(@NotNull InputStream input, @NotNull String name,
                                                  @NotNull BudgetLimits limits)
            throws PackageException, IOException {
        try (InputStream gzip = new GZIPInputStream(input)) {
            CountedLimitedInputStream counted = new CountedLimitedInputStream(
                    gzip, limits.decompressedCap(), "openbsd package payload");
            TarArchiveInputStream tar = new TarArchiveInputStream(counted);

            ContentsParser.ParseResult contents = null;
            String description = "";

            TarArchiveEntry entry;
            int entryCount = 0;
            while ((entry = tar.getNextEntry()) != null) {
                if (++entryCount > limits.maxEntries()) {
                    throw new PackageException.InvalidPackageException(
                            "OpenBSD package exceeds maximum entry count: " + limits.maxEntries(),
                            PackageFormat.OPENBSD_PKG);
                }
                String entryName = entry.getName();

                if (entryName.equals("+CONTENTS")) {
                    contents = ContentsParser.parse(new CountedLimitedInputStream(
                            tar, limits.memberCap(), "+CONTENTS"));
                } else if (entryName.equals("+DESC")) {
                    // Capped member read (catalog §2).
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    CountedLimitedInputStream member = new CountedLimitedInputStream(
                            tar, limits.memberCap(), "+DESC");
                    member.transferTo(out);
                    description = out.toString(StandardCharsets.UTF_8).trim();
                }
            }

            if (contents == null) {
                throw new PackageException.InvalidPackageException(
                        "Missing +CONTENTS in OpenBSD package", PackageFormat.OPENBSD_PKG);
            }

            OpenBsdMetadata metadata = new OpenBsdMetadata(contents, description);
            log.info("Read OpenBSD package: {}-{}", metadata.name(), metadata.version());

            return new OpenBsdPackage(metadata, null);
        }
    }

    /**
     * Streams the payload entries from an OpenBSD package.
     *
     * @param path the path to the package file
     * @return a stream of payload entries
     * @throws PackageException if the package cannot be read
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull Stream<PackageEntry> streamPayload(@NotNull Path path)
            throws PackageException, IOException {
        log.debug("Streaming OpenBSD payload from: {}", path);
        InputStream fileStream = new BufferedInputStream(Files.newInputStream(path));
        return streamPayloadFromStream(fileStream);
    }

    /**
     * Streams the payload entries from an OpenBSD input stream.
     *
     * @param input the input stream containing the package
     * @param name the nominal name/path (for logging)
     * @return a stream of payload entries
     * @throws PackageException if the package cannot be read
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull Stream<PackageEntry> streamPayload(@NotNull InputStream input, @NotNull String name)
            throws PackageException, IOException {
        log.debug("Streaming OpenBSD payload from stream: {}", name);
        return streamPayloadFromStream(input);
    }

    /**
     * Streams the payload entries from an InputStreamSource.
     *
     * @param source the input stream source
     * @return a stream of payload entries
     * @throws PackageException if the package cannot be read
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull Stream<PackageEntry> streamPayload(@NotNull InputStreamSource source)
            throws PackageException, IOException {
        log.debug("Streaming OpenBSD payload from source: {}", source.path());
        return streamPayloadFromStream(source.openStream());
    }

    private static @NotNull Stream<PackageEntry> streamPayloadFromStream(@NotNull InputStream input)
            throws IOException {
        InputStream gzipStream = new GZIPInputStream(input);
        TarArchiveInputStream tar = new TarArchiveInputStream(gzipStream);

        Iterator<PackageEntry> iterator = new TarEntryIterator(tar);
        Spliterator<PackageEntry> spliterator = Spliterators.spliteratorUnknownSize(
                iterator, Spliterator.ORDERED | Spliterator.NONNULL);

        return StreamSupport.stream(spliterator, false)
                .filter(e -> !e.path().startsWith("+")) // Skip metadata files
                .onClose(() -> {
                    try {
                        tar.close();
                        input.close();
                    } catch (IOException e) {
                        log.debug("Error closing OpenBSD payload stream", e);
                    }
                });
    }

    private static class TarEntryIterator implements Iterator<PackageEntry> {
        private final TarArchiveInputStream tar;
        private TarArchiveEntry nextEntry;
        private boolean done = false;
        private IOException pendingFailure = null;

        TarEntryIterator(TarArchiveInputStream tar) {
            this.tar = tar;
            advance();
        }

        private void advance() {
            try {
                nextEntry = tar.getNextEntry();
                if (nextEntry == null) {
                    done = true;
                }
            } catch (IOException e) {
                // Strict truncation (catalog §5/§6): a corrupt or truncated tar must
                // surface loudly instead of silently ending the stream with partial data.
                done = true;
                pendingFailure = e;
            }
        }

        private void failIfPending() {
            if (pendingFailure != null) {
                throw new BaharatStreamException(
                        "Corrupt or truncated tar archive: " + pendingFailure.getMessage(),
                        PackageFormat.OPENBSD_PKG, pendingFailure);
            }
        }

        @Override
        public boolean hasNext() {
            failIfPending();
            return !done && nextEntry != null;
        }

        @Override
        public PackageEntry next() {
            failIfPending();
            TarArchiveEntry current = nextEntry;
            advance();

            String path = current.getName();
            int mode = current.getMode();
            Instant mtime = current.getLastModifiedDate().toInstant();
            String user = current.getUserName() != null ? current.getUserName() : "root";
            String group = current.getGroupName() != null ? current.getGroupName() : "wheel";

            if (current.isDirectory()) {
                return new PackageEntry.DirectoryEntry(path, mode | FileInfo.S_IFDIR, mtime, user, group);
            } else if (current.isSymbolicLink()) {
                String linkTarget = current.getLinkName();
                // Security: Validate symlink target for path traversal
                String validatedTarget = SecurityUtils.validateSymlinkTarget(linkTarget, path);
                if (validatedTarget == null) {
                    throw new BaharatStreamException(
                            "Dangerous symlink target detected for " + path + ": " + linkTarget,
                            PackageFormat.OPENBSD_PKG, new PackageException.InvalidPackageException(
                                    "Dangerous symlink target detected for " + path + ": " + linkTarget,
                                    PackageFormat.OPENBSD_PKG));
                }
                return new PackageEntry.SymlinkEntry(path, mode | FileInfo.S_IFLNK, mtime, user, group,
                        validatedTarget);
            } else {
                return new PackageEntry.FileEntry(path, mode | FileInfo.S_IFREG, mtime, user, group,
                        current.getSize(), new BoundedTarInputStream(tar, current.getSize(), !current.isSparse()));
            }
        }
    }

    private static class BoundedTarInputStream extends InputStream {
        private final TarArchiveInputStream tar;
        private long remaining;
        private final boolean enforceTruncation;

        BoundedTarInputStream(TarArchiveInputStream tar, long size, boolean enforceTruncation) {
            this.tar = tar;
            this.remaining = size;
            this.enforceTruncation = enforceTruncation;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) return -1;
            int b = tar.read();
            if (b >= 0) {
                remaining--;
            } else if (remaining > 0 && enforceTruncation) {
                throw new IOException("Truncated tar entry: " + remaining + " bytes missing");
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) return -1;
            int toRead = (int) Math.min(len, remaining);
            int read = tar.read(b, off, toRead);
            if (read > 0) {
                remaining -= read;
            } else if (read < 0 && remaining > 0 && enforceTruncation) {
                throw new IOException("Truncated tar entry: " + remaining + " bytes missing");
            }
            return read;
        }

        @Override
        public void close() {
            // Don't close the tar stream
        }
    }
}
