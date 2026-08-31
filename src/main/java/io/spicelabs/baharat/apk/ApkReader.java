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
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.GZIPInputStream;

/**
 * Reader for Alpine Linux APK (.apk) package files.
 *
 * <p>APK files are gzip-compressed tar archives containing:
 * <ul>
 *   <li>{@code .SIGN.RSA.*} - Package signature (optional)</li>
 *   <li>{@code .PKGINFO} - Package metadata</li>
 *   <li>{@code .trigger} - Trigger scripts (optional)</li>
 *   <li>Payload files at their installation paths</li>
 * </ul>
 *
 * @see ApkPackage
 * @see ApkMetadata
 */
public final class ApkReader {

    private static final Logger log = LoggerFactory.getLogger(ApkReader.class);

    private ApkReader() {
        // Utility class
    }

    /**
     * Reads an APK package from a file path.
     *
     * @param path the path to the APK file
     * @return the parsed package
     * @throws PackageException if the package cannot be read
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull ApkPackage read(@NotNull Path path) throws PackageException, IOException {
        log.debug("Reading APK package from: {}", path);

        try (InputStream in = new BufferedInputStream(Files.newInputStream(path))) {
            ApkPackage pkg = readFromStream(in, path.toString());
            return new ApkPackage(pkg.apkMetadata(), path);
        }
    }

    /**
     * Reads an APK package from an input stream.
     *
     * @param input the input stream containing the APK package
     * @param name the nominal name/path (for logging)
     * @return the parsed package
     * @throws PackageException if the package cannot be read
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull ApkPackage read(@NotNull InputStream input, @NotNull String name)
            throws PackageException, IOException {
        log.debug("Reading APK package from stream: {}", name);
        return readFromStream(input, name);
    }

    /**
     * Reads an APK package from an InputStreamSource.
     *
     * @param source the input stream source
     * @return the parsed package
     * @throws PackageException if the package cannot be read
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull ApkPackage read(@NotNull InputStreamSource source)
            throws PackageException, IOException {
        log.debug("Reading APK package from source: {}", source.path());
        try (InputStream in = source.openStream()) {
            return readFromStream(in, source.path());
        }
    }

    private static @NotNull ApkPackage readFromStream(@NotNull InputStream input, @NotNull String name)
            throws PackageException, IOException {
        return readFromStream(input, name, BudgetLimits.DEFAULT);
    }

    /**
     * Package-private overload with explicit budgets (Fresh Scent Phase 5, finding B2).
     */
    static @NotNull ApkPackage readFromStream(@NotNull InputStream input, @NotNull String name,
                                              @NotNull BudgetLimits limits)
            throws PackageException, IOException {
        try (InputStream gzip = new GZIPInputStream(input)) {
            // Decompressed-byte budget: the whole tar is inflated during this metadata
            // pass (header reads + skips); cap it loudly (catalog §9).
            CountedLimitedInputStream counted = new CountedLimitedInputStream(
                    gzip, limits.decompressedCap(), "apk payload");
            TarArchiveInputStream tar = new TarArchiveInputStream(counted);

            Map<String, Object> pkgInfo = null;
            List<FileInfo> files = new ArrayList<>();

            TarArchiveEntry entry;
            int entryCount = 0;
            while ((entry = tar.getNextEntry()) != null) {
                if (++entryCount > limits.maxEntries()) {
                    throw new PackageException.InvalidPackageException(
                            "APK exceeds maximum entry count: " + limits.maxEntries(),
                            PackageFormat.APK);
                }
                String entryName = entry.getName();

                if (entryName.equals(".PKGINFO")) {
                    pkgInfo = ApkInfoParser.parse(new CountedLimitedInputStream(
                            tar, limits.memberCap(), ".PKGINFO"));
                } else if (!entryName.startsWith(".")) {
                    FileInfo info = createFileInfo(entry);
                    if (info != null) {
                        files.add(info);
                    }
                }
            }

            if (pkgInfo == null) {
                throw new PackageException.InvalidPackageException(
                        "Missing .PKGINFO in APK package", PackageFormat.APK);
            }

            ApkMetadata metadata = new ApkMetadata(pkgInfo, files);
            log.info("Read APK package: {}-{}", metadata.name(), metadata.version());

            return new ApkPackage(metadata, null);
        }
    }

    /**
     * Streams the payload entries from an APK package.
     *
     * @param path the path to the APK file
     * @return a stream of payload entries
     * @throws PackageException if the package cannot be read
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull Stream<PackageEntry> streamPayload(@NotNull Path path)
            throws PackageException, IOException {
        log.debug("Streaming APK payload from: {}", path);
        InputStream fileStream = new BufferedInputStream(Files.newInputStream(path));
        return streamPayloadFromStream(fileStream);
    }

    /**
     * Streams the payload entries from an APK input stream.
     *
     * @param input the input stream containing the APK package
     * @param name the nominal name/path (for logging)
     * @return a stream of payload entries
     * @throws PackageException if the package cannot be read
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull Stream<PackageEntry> streamPayload(@NotNull InputStream input, @NotNull String name)
            throws PackageException, IOException {
        log.debug("Streaming APK payload from stream: {}", name);
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
        log.debug("Streaming APK payload from source: {}", source.path());
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
                .filter(e -> !e.path().startsWith(".")) // Skip metadata files
                .onClose(() -> {
                    try {
                        tar.close();
                        input.close();
                    } catch (IOException e) {
                        log.debug("Error closing APK payload stream", e);
                    }
                });
    }

    /**
     * Builds FileInfo for a tar entry, or returns null when the entry must be skipped
     * (dangerous symlink target — uniform policy across the tar readers, Fresh Scent
     * Phase 5, finding B14; the previous code stored an EMPTY target silently, catalog §6).
     */
    private static FileInfo createFileInfo(@NotNull TarArchiveEntry entry) {
        String path = entry.getName();
        int mode = entry.getMode();

        if (entry.isDirectory()) {
            mode |= FileInfo.S_IFDIR;
        } else if (entry.isSymbolicLink()) {
            mode |= FileInfo.S_IFLNK;
        } else if (entry.isFile()) {
            mode |= FileInfo.S_IFREG;
        }

        java.util.Optional<String> linkTarget = java.util.Optional.empty();
        if (entry.isSymbolicLink()) {
            String target = entry.getLinkName();
            String validatedTarget = SecurityUtils.validateSymlinkTarget(target, path);
            if (validatedTarget == null) {
                log.warn("Skipping entry with dangerous symlink target: {} -> {}", path, target);
                return null;
            }
            linkTarget = java.util.Optional.of(validatedTarget);
        }

        long entrySize = entry.isSparse() ? entry.getRealSize() : entry.getSize();
        return new FileInfo(
                path,
                entrySize,
                mode,
                entry.getLastModifiedDate().toInstant(),
                entry.getUserName() != null ? entry.getUserName() : "root",
                entry.getGroupName() != null ? entry.getGroupName() : "root",
                java.util.Optional.empty(),
                linkTarget,
                0
        );
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
                        PackageFormat.APK, pendingFailure);
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
            String group = current.getGroupName() != null ? current.getGroupName() : "root";

            if (current.isDirectory()) {
                return new PackageEntry.DirectoryEntry(path, mode | FileInfo.S_IFDIR, mtime, user, group);
            } else if (current.isSymbolicLink()) {
                String linkTarget = current.getLinkName();
                // Security: Validate symlink target for path traversal
                String validatedTarget = SecurityUtils.validateSymlinkTarget(linkTarget, path);
                if (validatedTarget == null) {
                    throw new BaharatStreamException(
                            "Dangerous symlink target detected for " + path + ": " + linkTarget,
                            PackageFormat.APK, new PackageException.InvalidPackageException(
                                    "Dangerous symlink target detected for " + path + ": " + linkTarget,
                                    PackageFormat.APK));
                }
                return new PackageEntry.SymlinkEntry(path, mode | FileInfo.S_IFLNK, mtime, user, group,
                        validatedTarget);
            } else {
                long entrySize = current.isSparse() ? current.getRealSize() : current.getSize();
                return new PackageEntry.FileEntry(path, mode | FileInfo.S_IFREG, mtime, user, group,
                        entrySize, new BoundedTarInputStream(tar, entrySize, !current.isSparse()));
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
