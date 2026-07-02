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
package io.spicelabs.baharat.deb;

import io.spicelabs.baharat.PackageEntry;
import io.spicelabs.baharat.PackageException;
import io.spicelabs.baharat.PackageFormat;
import io.spicelabs.baharat.adapter.InputStreamSource;
import io.spicelabs.baharat.common.FileInfo;
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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.GZIPInputStream;

/**
 * Reader for Debian (.deb) package files.
 *
 * <p>DEB files are ar archives containing:
 * <ul>
 *   <li>{@code debian-binary} - Version string (usually "2.0")</li>
 *   <li>{@code control.tar.*} - Control information (metadata, scripts)</li>
 *   <li>{@code data.tar.*} - Package payload (files to install)</li>
 * </ul>
 *
 * <h2>Supported Compressions</h2>
 * <ul>
 *   <li>gzip (.gz) - Original format</li>
 *   <li>xz (.xz) - Default since Debian 7</li>
 *   <li>zstd (.zst) - Supported in recent dpkg</li>
 *   <li>bzip2 (.bz2) - Legacy support</li>
 *   <li>lzma (.lzma) - Legacy support</li>
 * </ul>
 *
 * @see DebPackage
 * @see DebMetadata
 */
public final class DebReader {

    private static final Logger log = LoggerFactory.getLogger(DebReader.class);

    private DebReader() {
        // Utility class
    }

    /**
     * Reads a DEB package from a file path.
     *
     * @param path the path to the DEB file
     * @return the parsed DEB package
     * @throws PackageException if the package cannot be read
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull DebPackage read(@NotNull Path path) throws PackageException, IOException {
        log.debug("Reading DEB package from: {}", path);

        try (InputStream in = new BufferedInputStream(Files.newInputStream(path))) {
            DebPackage pkg = readFromStream(in, path.toString());
            // Re-create with the actual path
            return new DebPackage(pkg.debMetadata(), path, pkg.debianBinaryVersion());
        }
    }

    /**
     * Reads a DEB package from an input stream.
     *
     * <p>This method is useful for reading packages from non-file sources,
     * such as archives or network streams.
     *
     * @param input the input stream containing the DEB package
     * @param name the nominal name/path of the package (for logging and error messages)
     * @return the parsed DEB package
     * @throws PackageException if the package cannot be read
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull DebPackage read(@NotNull InputStream input, @NotNull String name)
            throws PackageException, IOException {
        log.debug("Reading DEB package from stream: {}", name);
        return readFromStream(input, name);
    }

    /**
     * Reads a DEB package from an InputStreamSource.
     *
     * <p>This method enables integration with external artifact abstractions
     * like Goat Rodeo's ArtifactWrapper.
     *
     * @param source the input stream source
     * @return the parsed DEB package
     * @throws PackageException if the package cannot be read
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull DebPackage read(@NotNull InputStreamSource source)
            throws PackageException, IOException {
        log.debug("Reading DEB package from source: {}", source.path());
        try (InputStream in = source.openStream()) {
            return readFromStream(in, source.path());
        }
    }

    /**
     * Internal method to read a DEB package from an input stream.
     */
    private static @NotNull DebPackage readFromStream(@NotNull InputStream input, @NotNull String name)
            throws PackageException, IOException {
        ArArchiveReader ar = new ArArchiveReader(input);
        ar.readHeader();

        String debianBinaryVersion = null;
        DebControlParser.ParseResult controlResult = null;
        List<FileInfo> files = new ArrayList<>();

        ArArchiveReader.ArEntry entry;
        while ((entry = ar.nextEntry()) != null) {
            String entryName = entry.name();
            log.trace("Processing ar entry: {}", entryName);

            if (entryName.equals("debian-binary")) {
                debianBinaryVersion = readDebianBinary(ar.getEntryInputStream(entry));
            } else if (entryName.startsWith("control.tar")) {
                controlResult = readControlTarWithRaw(ar.getEntryInputStream(entry), entryName);
            } else if (entryName.startsWith("data.tar")) {
                files = readDataTarFileList(ar.getEntryInputStream(entry), entryName);
            }
        }

        if (debianBinaryVersion == null) {
            throw new PackageException.InvalidPackageException(
                    "Missing debian-binary in DEB package", PackageFormat.DEB);
        }
        if (controlResult == null) {
            throw new PackageException.InvalidPackageException(
                    "Missing control.tar in DEB package", PackageFormat.DEB);
        }

        DebMetadata metadata = new DebMetadata(controlResult.fields(), files, controlResult.rawContent(), name);
        log.info("Read DEB package: {}_{}", metadata.name(), metadata.version());

        return new DebPackage(metadata, null, debianBinaryVersion);
    }

    /**
     * Streams the payload entries from a DEB file.
     *
     * @param path the path to the DEB file
     * @return a stream of payload entries
     * @throws PackageException if the package cannot be read
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull Stream<PackageEntry> streamPayload(@NotNull Path path)
            throws PackageException, IOException {
        log.debug("Streaming DEB payload from: {}", path);
        InputStream fileStream = new BufferedInputStream(Files.newInputStream(path));
        return streamPayloadFromStream(fileStream, path.toString());
    }

    /**
     * Streams the payload entries from a DEB input stream.
     *
     * <p>The caller is responsible for closing the returned stream when done,
     * which will also close the input stream.
     *
     * @param input the input stream containing the DEB package
     * @param name the nominal name/path of the package (for logging)
     * @return a stream of payload entries
     * @throws PackageException if the package cannot be read
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull Stream<PackageEntry> streamPayload(@NotNull InputStream input, @NotNull String name)
            throws PackageException, IOException {
        log.debug("Streaming DEB payload from stream: {}", name);
        return streamPayloadFromStream(input, name);
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
        log.debug("Streaming DEB payload from source: {}", source.path());
        return streamPayloadFromStream(source.openStream(), source.path());
    }

    /**
     * Internal method to stream payload from an input stream.
     */
    private static @NotNull Stream<PackageEntry> streamPayloadFromStream(
            @NotNull InputStream input, @NotNull String name) throws PackageException, IOException {
        try {
            ArArchiveReader ar = new ArArchiveReader(input);
            ar.readHeader();

            // Find data.tar entry
            ArArchiveReader.ArEntry entry;
            while ((entry = ar.nextEntry()) != null) {
                if (entry.name().startsWith("data.tar")) {
                    InputStream decompressed = decompressStream(ar.getEntryInputStream(entry), entry.name());
                    TarArchiveInputStream tar = new TarArchiveInputStream(decompressed);

                    Iterator<PackageEntry> iterator = new TarEntryIterator(tar);
                    Spliterator<PackageEntry> spliterator = Spliterators.spliteratorUnknownSize(
                            iterator, Spliterator.ORDERED | Spliterator.NONNULL);

                    return StreamSupport.stream(spliterator, false)
                            .onClose(() -> {
                                try {
                                    tar.close();
                                    input.close();
                                } catch (IOException e) {
                                    log.debug("Error closing DEB payload stream", e);
                                }
                            });
                }
            }

            input.close();
            throw new PackageException.InvalidPackageException(
                    "Missing data.tar in DEB package", PackageFormat.DEB);

        } catch (Exception e) {
            input.close();
            throw e;
        }
    }

    private static @NotNull String readDebianBinary(@NotNull InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        in.transferTo(out);
        return out.toString(StandardCharsets.UTF_8).trim();
    }

    private static @NotNull DebControlParser.ParseResult readControlTarWithRaw(
            @NotNull InputStream in, @NotNull String name) throws IOException, PackageException {
        InputStream decompressed = decompressStream(in, name);
        TarArchiveInputStream tar = new TarArchiveInputStream(decompressed);

        TarArchiveEntry entry;
        while ((entry = tar.getNextEntry()) != null) {
            String entryName = entry.getName();
            // Look for the control file
            if (entryName.equals("./control") || entryName.equals("control")) {
                return DebControlParser.parseWithRaw(tar);
            }
        }

        throw new PackageException.InvalidPackageException(
                "Missing control file in control.tar", PackageFormat.DEB);
    }

    private static @NotNull List<FileInfo> readDataTarFileList(@NotNull InputStream in, @NotNull String name)
            throws IOException {
        InputStream decompressed = decompressStream(in, name);
        TarArchiveInputStream tar = new TarArchiveInputStream(decompressed);

        List<FileInfo> files = new ArrayList<>();
        TarArchiveEntry entry;
        while ((entry = tar.getNextEntry()) != null) {
            String path = entry.getName();
            // Normalize path - remove leading ./
            if (path.startsWith("./")) {
                path = path.substring(1);
            }
            if (path.isEmpty() || path.equals("/")) {
                continue;
            }

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
                if (validatedTarget == null) {
                    log.warn("Skipping entry with dangerous symlink target: {} -> {}", path, target);
                    continue;
                }
                linkTarget = java.util.Optional.of(validatedTarget);
            }

            files.add(new FileInfo(
                    path,
                    entry.getSize(),
                    mode,
                    entry.getLastModifiedDate().toInstant(),
                    entry.getUserName() != null ? entry.getUserName() : "root",
                    entry.getGroupName() != null ? entry.getGroupName() : "root",
                    java.util.Optional.empty(),
                    linkTarget,
                    0
            ));
        }

        return files;
    }

    private static @NotNull InputStream decompressStream(@NotNull InputStream in, @NotNull String name)
            throws IOException {
        if (name.endsWith(".gz")) {
            return new GZIPInputStream(in);
        } else if (name.endsWith(".xz")) {
            return new XZCompressorInputStream(in);
        } else if (name.endsWith(".zst")) {
            return new ZstdCompressorInputStream(in);
        } else if (name.endsWith(".bz2")) {
            return new BZip2CompressorInputStream(in);
        } else if (name.endsWith(".lzma")) {
            return new org.tukaani.xz.LZMAInputStream(in);
        }
        // Uncompressed
        return in;
    }

    /**
     * Iterator over tar archive entries as PackageEntry objects.
     */
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
            if (path.startsWith("./")) {
                path = path.substring(1);
            }
            if (path.isEmpty()) {
                path = "/";
            }

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
                            PackageFormat.DEB));
                }
                return new PackageEntry.SymlinkEntry(path, mode | FileInfo.S_IFLNK, mtime, user, group,
                        validatedTarget);
            } else {
                // Regular file - wrap the tar stream in a bounded stream
                return new PackageEntry.FileEntry(path, mode | FileInfo.S_IFREG, mtime, user, group,
                        current.getSize(), new BoundedTarInputStream(tar, current.getSize()));
            }
        }
    }

    /**
     * Bounded input stream that reads from tar without closing it.
     */
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
            // Don't close the tar stream - it's managed by the stream pipeline
        }
    }
}
