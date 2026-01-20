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

import io.spicelabs.baharat.PackageEntry;
import io.spicelabs.baharat.PackageException;
import io.spicelabs.baharat.PackageFormat;
import io.spicelabs.baharat.adapter.InputStreamSource;
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
        try (InputStream gzip = new GZIPInputStream(input)) {
            TarArchiveInputStream tar = new TarArchiveInputStream(gzip);

            Map<String, Object> pkgInfo = null;
            List<FileInfo> files = new ArrayList<>();

            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                String entryName = entry.getName();

                if (entryName.equals(".PKGINFO")) {
                    pkgInfo = ApkInfoParser.parse(tar);
                } else if (!entryName.startsWith(".")) {
                    files.add(createFileInfo(entry));
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

    private static @NotNull FileInfo createFileInfo(@NotNull TarArchiveEntry entry) {
        String path = entry.getName();
        int mode = entry.getMode();

        if (entry.isDirectory()) {
            mode |= FileInfo.S_IFDIR;
        } else if (entry.isSymbolicLink()) {
            mode |= FileInfo.S_IFLNK;
        } else if (entry.isFile()) {
            mode |= FileInfo.S_IFREG;
        }

        // Security: Validate symlink target if this is a symlink
        java.util.Optional<String> linkTarget = java.util.Optional.empty();
        if (entry.isSymbolicLink()) {
            String target = entry.getLinkName();
            String validatedTarget = SecurityUtils.validateSymlinkTarget(target, path);
            if (validatedTarget != null) {
                linkTarget = java.util.Optional.of(validatedTarget);
            }
            // If validation fails, we store empty target - caller should handle
        }

        return new FileInfo(
                path,
                entry.getSize(),
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
                done = true;
            }
        }

        @Override
        public boolean hasNext() {
            return !done && nextEntry != null;
        }

        @Override
        public PackageEntry next() {
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
                    throw new RuntimeException(new PackageException.InvalidPackageException(
                            "Dangerous symlink target detected for " + path + ": " + linkTarget,
                            PackageFormat.APK));
                }
                return new PackageEntry.SymlinkEntry(path, mode | FileInfo.S_IFLNK, mtime, user, group,
                        validatedTarget);
            } else {
                return new PackageEntry.FileEntry(path, mode | FileInfo.S_IFREG, mtime, user, group,
                        current.getSize(), new BoundedTarInputStream(tar, current.getSize()));
            }
        }
    }

    private static class BoundedTarInputStream extends InputStream {
        private final TarArchiveInputStream tar;
        private long remaining;

        BoundedTarInputStream(TarArchiveInputStream tar, long size) {
            this.tar = tar;
            this.remaining = size;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) return -1;
            int b = tar.read();
            if (b >= 0) remaining--;
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) return -1;
            int toRead = (int) Math.min(len, remaining);
            int read = tar.read(b, off, toRead);
            if (read > 0) remaining -= read;
            return read;
        }

        @Override
        public void close() {
            // Don't close the tar stream
        }
    }
}
