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

import com.google.gson.JsonObject;
import io.spicelabs.baharat.BaharatStreamException;
import io.spicelabs.baharat.PackageEntry;
import io.spicelabs.baharat.PackageException;
import io.spicelabs.baharat.PackageFormat;
import io.spicelabs.baharat.adapter.InputStreamSource;
import io.spicelabs.baharat.common.BudgetLimits;
import io.spicelabs.baharat.common.CountedLimitedInputStream;
import io.spicelabs.baharat.common.FileInfo;
import io.spicelabs.baharat.common.JsonSecurity;
import io.spicelabs.baharat.common.SecurityUtils;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream;
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
 * Reader for FreeBSD pkg (.pkg, .txz) package files.
 *
 * <p>FreeBSD packages are compressed tar archives containing:
 * <ul>
 *   <li>{@code +COMPACT_MANIFEST} - Compact JSON metadata</li>
 *   <li>{@code +MANIFEST} - Full JSON metadata with file checksums</li>
 *   <li>Payload files at their installation paths</li>
 * </ul>
 *
 * @see FreeBsdPackage
 * @see FreeBsdMetadata
 */
public final class FreeBsdReader {

    private static final Logger log = LoggerFactory.getLogger(FreeBsdReader.class);

    private FreeBsdReader() {
        // Utility class
    }

    /**
     * Reads a FreeBSD package from a file path.
     *
     * @param path the path to the package file
     * @return the parsed package
     * @throws PackageException if the package cannot be read
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull FreeBsdPackage read(@NotNull Path path) throws PackageException, IOException {
        log.debug("Reading FreeBSD package from: {}", path);

        try (InputStream in = decompressFile(path)) {
            FreeBsdPackage pkg = readFromStream(in, path.toString());
            return new FreeBsdPackage(pkg.freeBsdMetadata(), path);
        }
    }

    /**
     * Reads a FreeBSD package from an input stream.
     *
     * @param input the input stream containing the package
     * @param name the nominal name/path (for logging and compression detection)
     * @return the parsed package
     * @throws PackageException if the package cannot be read
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull FreeBsdPackage read(@NotNull InputStream input, @NotNull String name)
            throws PackageException, IOException {
        log.debug("Reading FreeBSD package from stream: {}", name);
        try (InputStream decompressed = decompressStream(input, name)) {
            return readFromStream(decompressed, name);
        }
    }

    /**
     * Reads a FreeBSD package from an InputStreamSource.
     *
     * @param source the input stream source
     * @return the parsed package
     * @throws PackageException if the package cannot be read
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull FreeBsdPackage read(@NotNull InputStreamSource source)
            throws PackageException, IOException {
        log.debug("Reading FreeBSD package from source: {}", source.path());
        try (InputStream in = source.openStream();
             InputStream decompressed = decompressStream(in, source.path())) {
            return readFromStream(decompressed, source.path());
        }
    }

    private static @NotNull FreeBsdPackage readFromStream(@NotNull InputStream input, @NotNull String name)
            throws PackageException, IOException {
        return readFromStream(input, name, BudgetLimits.DEFAULT);
    }

    /**
     * Package-private overload with explicit budgets.
     */
    static @NotNull FreeBsdPackage readFromStream(@NotNull InputStream input, @NotNull String name,
                                                  @NotNull BudgetLimits limits)
            throws PackageException, IOException {
        CountedLimitedInputStream counted = new CountedLimitedInputStream(
                input, limits.decompressedCap(), "freebsd package payload");
        TarArchiveInputStream tar = new TarArchiveInputStream(counted);

        JsonObject manifest = null;

        TarArchiveEntry entry;
        int entryCount = 0;
        while ((entry = tar.getNextEntry()) != null) {
            if (++entryCount > limits.maxEntries()) {
                throw new PackageException.InvalidPackageException(
                        "FreeBSD package exceeds maximum entry count: " + limits.maxEntries(),
                        PackageFormat.FREEBSD_PKG);
            }
            String entryName = entry.getName();

            if (entryName.equals("+MANIFEST") || entryName.equals("+COMPACT_MANIFEST")) {
                // Capped member read + JSON depth guard.
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                CountedLimitedInputStream member = new CountedLimitedInputStream(
                        tar, limits.memberCap(), entryName);
                member.transferTo(out);
                String content = out.toString(StandardCharsets.UTF_8);
                JsonSecurity.checkDepth(content, PackageFormat.FREEBSD_PKG);
                manifest = ManifestParser.parse(content);
                if (entryName.equals("+MANIFEST")) {
                    break;
                }
            }
        }

        if (manifest == null) {
            throw new PackageException.InvalidPackageException(
                    "Missing +MANIFEST in FreeBSD package", PackageFormat.FREEBSD_PKG);
        }

        FreeBsdMetadata metadata = new FreeBsdMetadata(manifest);
        log.info("Read FreeBSD package: {}-{}", metadata.name(), metadata.version());

        return new FreeBsdPackage(metadata, null);
    }

    /**
     * Streams the payload entries from a FreeBSD package.
     *
     * @param path the path to the package file
     * @return a stream of payload entries
     * @throws PackageException if the package cannot be read
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull Stream<PackageEntry> streamPayload(@NotNull Path path)
            throws PackageException, IOException {
        log.debug("Streaming FreeBSD payload from: {}", path);
        InputStream decompressed = decompressFile(path);
        return streamPayloadFromStream(decompressed);
    }

    /**
     * Streams the payload entries from a FreeBSD input stream.
     *
     * @param input the input stream containing the package
     * @param name the nominal name/path (for compression detection)
     * @return a stream of payload entries
     * @throws PackageException if the package cannot be read
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull Stream<PackageEntry> streamPayload(@NotNull InputStream input, @NotNull String name)
            throws PackageException, IOException {
        log.debug("Streaming FreeBSD payload from stream: {}", name);
        InputStream decompressed = decompressStream(input, name);
        return streamPayloadFromStream(decompressed);
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
        log.debug("Streaming FreeBSD payload from source: {}", source.path());
        InputStream decompressed = decompressStream(source.openStream(), source.path());
        return streamPayloadFromStream(decompressed);
    }

    private static @NotNull Stream<PackageEntry> streamPayloadFromStream(@NotNull InputStream input) {
        TarArchiveInputStream tar = new TarArchiveInputStream(input);

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
                        log.debug("Error closing FreeBSD payload stream", e);
                    }
                });
    }

    private static @NotNull InputStream decompressFile(@NotNull Path path) throws IOException {
        String fileName = path.getFileName().toString().toLowerCase();
        InputStream in = new BufferedInputStream(Files.newInputStream(path));

        if (fileName.endsWith(".txz") || fileName.endsWith(".xz")) {
            return new XZCompressorInputStream(in);
        } else if (fileName.endsWith(".tzst") || fileName.endsWith(".zst")) {
            return new ZstdCompressorInputStream(in);
        } else if (fileName.endsWith(".tgz") || fileName.endsWith(".gz")) {
            return new GZIPInputStream(in);
        } else if (fileName.endsWith(".tbz") || fileName.endsWith(".bz2")) {
            return new BZip2CompressorInputStream(in);
        }

        // Try to detect from magic bytes
        byte[] magic = new byte[6];
        in.mark(6);
        int read = in.read(magic);
        in.reset();

        if (read >= 4) {
            // Zstd
            if (magic[0] == 0x28 && magic[1] == (byte) 0xB5 && magic[2] == 0x2F && magic[3] == (byte) 0xFD) {
                return new ZstdCompressorInputStream(in);
            }
            // XZ
            if (magic[0] == (byte) 0xFD && magic[1] == '7' && magic[2] == 'z' && magic[3] == 'X') {
                return new XZCompressorInputStream(in);
            }
            // Gzip
            if (magic[0] == 0x1F && magic[1] == (byte) 0x8B) {
                return new GZIPInputStream(in);
            }
            // Bzip2
            if (magic[0] == 0x42 && magic[1] == 0x5A && magic[2] == 0x68) {
                return new BZip2CompressorInputStream(in);
            }
        }

        // Assume XZ as default for .pkg files
        return new XZCompressorInputStream(in);
    }

    /**
     * Decompresses a stream based on filename hint or magic bytes.
     */
    private static @NotNull InputStream decompressStream(@NotNull InputStream input, @NotNull String name)
            throws IOException {
        String lower = name.toLowerCase();

        // First try filename-based detection
        if (lower.endsWith(".txz") || lower.endsWith(".xz")) {
            return new XZCompressorInputStream(input);
        } else if (lower.endsWith(".tzst") || lower.endsWith(".zst")) {
            return new ZstdCompressorInputStream(input);
        } else if (lower.endsWith(".tgz") || lower.endsWith(".gz")) {
            return new GZIPInputStream(input);
        } else if (lower.endsWith(".tbz") || lower.endsWith(".bz2")) {
            return new BZip2CompressorInputStream(input);
        }

        // Try magic byte detection
        BufferedInputStream buffered = new BufferedInputStream(input);
        byte[] magic = new byte[6];
        buffered.mark(6);
        int read = buffered.read(magic);
        buffered.reset();

        if (read >= 4) {
            // Zstd
            if (magic[0] == 0x28 && magic[1] == (byte) 0xB5 && magic[2] == 0x2F && magic[3] == (byte) 0xFD) {
                return new ZstdCompressorInputStream(buffered);
            }
            // XZ
            if (magic[0] == (byte) 0xFD && magic[1] == '7' && magic[2] == 'z' && magic[3] == 'X') {
                return new XZCompressorInputStream(buffered);
            }
            // Gzip
            if (magic[0] == 0x1F && magic[1] == (byte) 0x8B) {
                return new GZIPInputStream(buffered);
            }
            // Bzip2
            if (magic[0] == 0x42 && magic[1] == 0x5A && magic[2] == 0x68) {
                return new BZip2CompressorInputStream(buffered);
            }
        }

        // Assume XZ as default for .pkg files
        return new XZCompressorInputStream(buffered);
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
                // Strict truncation: a corrupt or truncated tar must
                // surface loudly instead of silently ending the stream with partial data.
                done = true;
                pendingFailure = e;
            }
        }

        private void failIfPending() {
            if (pendingFailure != null) {
                throw new BaharatStreamException(
                        "Corrupt or truncated tar archive: " + pendingFailure.getMessage(),
                        PackageFormat.FREEBSD_PKG, pendingFailure);
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
                            PackageFormat.FREEBSD_PKG, new PackageException.InvalidPackageException(
                                    "Dangerous symlink target detected for " + path + ": " + linkTarget,
                                    PackageFormat.FREEBSD_PKG));
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
