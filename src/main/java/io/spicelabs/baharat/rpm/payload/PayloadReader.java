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
package io.spicelabs.baharat.rpm.payload;

import io.spicelabs.baharat.BaharatStreamException;
import io.spicelabs.baharat.PackageFormat;
import io.spicelabs.baharat.rpm.exception.InvalidFormatException;
import io.spicelabs.baharat.rpm.exception.UnsupportedFormatException;
import io.spicelabs.baharat.rpm.metadata.FileInfo;
import io.spicelabs.baharat.rpm.metadata.PackageMetadata;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Reads the payload section of an RPM file.
 * The payload contains the actual files packaged in the RPM,
 * stored as a compressed CPIO archive.
 */
public final class PayloadReader implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PayloadReader.class);

    // Security limits
    private static final long DEFAULT_MAX_DECOMPRESSED_SIZE = 2L * 1024 * 1024 * 1024; // 2 GB default (reduced for safety)
    private static final int MAX_PATH_LENGTH = 4096;
    private static final int MAX_SYMLINK_TARGET_LENGTH = 4096;

    private final @NotNull InputStream payloadStream;
    private final @NotNull CompressionType compressionType;
    private final @NotNull PackageMetadata metadata;
    private final long maxDecompressedSize;
    private InputStream decompressedStream;
    private CpioArchiveReader cpioReader;

    /**
     * Creates a new payload reader.
     *
     * @param payloadStream the raw payload input stream
     * @param metadata the package metadata (for file info lookup)
     * @throws UnsupportedFormatException if the compression format is not supported
     * @throws IOException if an I/O error occurs
     */
    public PayloadReader(@NotNull InputStream payloadStream, @NotNull PackageMetadata metadata)
            throws UnsupportedFormatException, IOException {
        this(payloadStream, metadata, DEFAULT_MAX_DECOMPRESSED_SIZE);
    }

    /**
     * Creates a new payload reader with a custom max decompressed size limit.
     *
     * @param payloadStream the raw payload input stream
     * @param metadata the package metadata (for file info lookup)
     * @param maxDecompressedSize maximum allowed decompressed size in bytes (for decompression bomb protection)
     * @throws UnsupportedFormatException if the compression format is not supported
     * @throws IOException if an I/O error occurs
     */
    public PayloadReader(@NotNull InputStream payloadStream, @NotNull PackageMetadata metadata, long maxDecompressedSize)
            throws UnsupportedFormatException, IOException {
        this.payloadStream = payloadStream;
        this.metadata = metadata;
        this.maxDecompressedSize = maxDecompressedSize;

        // Detect compression from header or magic bytes
        String compressor = metadata.payloadCompressor();
        this.compressionType = CompressionType.fromName(compressor)
                .orElseThrow(() -> new UnsupportedFormatException(
                        "Unsupported payload compression: " + compressor));
    }

    /**
     * Creates a new payload reader with explicit compression type.
     *
     * @param payloadStream the raw payload input stream
     * @param compressionType the compression type
     * @param metadata the package metadata (for file info lookup)
     */
    public PayloadReader(@NotNull InputStream payloadStream, @NotNull CompressionType compressionType,
                         @NotNull PackageMetadata metadata) {
        this(payloadStream, compressionType, metadata, DEFAULT_MAX_DECOMPRESSED_SIZE);
    }

    /**
     * Creates a new payload reader with explicit compression type and custom size limit.
     *
     * @param payloadStream the raw payload input stream
     * @param compressionType the compression type
     * @param metadata the package metadata (for file info lookup)
     * @param maxDecompressedSize maximum allowed decompressed size in bytes
     */
    public PayloadReader(@NotNull InputStream payloadStream, @NotNull CompressionType compressionType,
                         @NotNull PackageMetadata metadata, long maxDecompressedSize) {
        this.payloadStream = payloadStream;
        this.compressionType = compressionType;
        this.metadata = metadata;
        this.maxDecompressedSize = maxDecompressedSize;
    }

    /**
     * Returns a stream of payload entries.
     * Each entry represents a file, directory, or symlink in the package.
     *
     * @return a stream of payload entries
     * @throws IOException if an I/O error occurs
     */
    public @NotNull Stream<PayloadEntry> entries() throws IOException {
        initializeReader();

        // Build lookup map for file metadata
        List<FileInfo> fileInfos = metadata.files();
        Map<String, FileInfo> fileInfoMap = new HashMap<>();
        for (FileInfo info : fileInfos) {
            fileInfoMap.put(info.path(), info);
        }

        return cpioReader.stream().map(cpioEntry -> {
            // Normalize path (remove leading ./)
            String path = cpioEntry.name();
            if (path.startsWith("./")) {
                path = path.substring(1);
            } else if (!path.startsWith("/")) {
                path = "/" + path;
            }

            // Security: Validate path for traversal attacks
            String validatedPath = validatePath(path);
            if (validatedPath == null) {
                throw new BaharatStreamException("Path traversal detected in payload entry: " + path,
                        PackageFormat.RPM, new InvalidFormatException(
                                "Path traversal detected in payload entry: " + path));
            }
            path = validatedPath;

            // Look up file info from header
            FileInfo info = fileInfoMap.get(path);
            String userName = info != null ? info.userName() : "root";
            String groupName = info != null ? info.groupName() : "root";
            Instant mtime = info != null ? info.mtime() : cpioEntry.mtime();

            if (cpioEntry.isDirectory()) {
                return new PayloadEntry.DirectoryEntry(
                        path,
                        cpioEntry.mode(),
                        mtime,
                        userName,
                        groupName
                );
            } else if (cpioEntry.isSymlink()) {
                String target;
                try {
                    target = cpioEntry.readLinkTarget();
                } catch (IOException e) {
                    throw new BaharatStreamException("Failed to read symlink target", e);
                }
                // Security: Validate symlink target for path traversal
                String validatedTarget = validateSymlinkTarget(target, path);
                if (validatedTarget == null) {
                    throw new BaharatStreamException(
                            "Dangerous symlink target detected for " + path + ": " + target,
                            PackageFormat.RPM, new InvalidFormatException(
                                    "Dangerous symlink target detected for " + path + ": " + target));
                }
                return new PayloadEntry.SymlinkEntry(
                        path,
                        cpioEntry.mode(),
                        mtime,
                        userName,
                        groupName,
                        validatedTarget
                );
            } else {
                return new PayloadEntry.FileEntry(
                        path,
                        cpioEntry.mode(),
                        mtime,
                        userName,
                        groupName,
                        cpioEntry.size(),
                        cpioEntry.dataStream()
                );
            }
        });
    }

    /**
     * Reads the next entry from the payload.
     *
     * @return the next entry, or null if there are no more entries
     * @throws InvalidFormatException if the payload format is invalid
     * @throws IOException if an I/O error occurs
     */
    public @Nullable PayloadEntry nextEntry() throws InvalidFormatException, IOException {
        initializeReader();

        CpioArchiveReader.CpioEntry cpioEntry = cpioReader.nextEntry();
        if (cpioEntry == null) {
            return null;
        }

        // Normalize path
        String path = cpioEntry.name();
        if (path.startsWith("./")) {
            path = path.substring(1);
        } else if (!path.startsWith("/")) {
            path = "/" + path;
        }

        // Security: Validate path for traversal attacks
        String validatedPath = validatePath(path);
        if (validatedPath == null) {
            throw new InvalidFormatException("Path traversal detected in payload entry: " + path);
        }
        path = validatedPath;

        // Look up file info
        List<FileInfo> fileInfos = metadata.files();
        FileInfo info = null;
        for (FileInfo fi : fileInfos) {
            if (fi.path().equals(path)) {
                info = fi;
                break;
            }
        }

        String userName = info != null ? info.userName() : "root";
        String groupName = info != null ? info.groupName() : "root";
        Instant mtime = info != null ? info.mtime() : cpioEntry.mtime();

        if (cpioEntry.isDirectory()) {
            return new PayloadEntry.DirectoryEntry(
                    path,
                    cpioEntry.mode(),
                    mtime,
                    userName,
                    groupName
            );
        } else if (cpioEntry.isSymlink()) {
            String target = cpioEntry.readLinkTarget();
            // Security: Validate symlink target for path traversal
            String validatedTarget = validateSymlinkTarget(target, path);
            if (validatedTarget == null) {
                throw new InvalidFormatException("Dangerous symlink target detected for " + path + ": " + target);
            }
            return new PayloadEntry.SymlinkEntry(
                    path,
                    cpioEntry.mode(),
                    mtime,
                    userName,
                    groupName,
                    validatedTarget
            );
        } else {
            return new PayloadEntry.FileEntry(
                    path,
                    cpioEntry.mode(),
                    mtime,
                    userName,
                    groupName,
                    cpioEntry.size(),
                    cpioEntry.dataStream()
            );
        }
    }

    /**
     * Returns the compression type used by this payload.
     *
     * @return the compression type
     */
    public @NotNull CompressionType compressionType() {
        return compressionType;
    }

    @Override
    public void close() throws IOException {
        if (cpioReader != null) {
            cpioReader.close();
        }
        if (decompressedStream != null) {
            decompressedStream.close();
        }
        payloadStream.close();
    }

    private void initializeReader() throws IOException {
        if (cpioReader == null) {
            log.debug("Initializing payload reader with {} compression", compressionType);
            BufferedInputStream buffered = new BufferedInputStream(payloadStream);
            InputStream rawDecompressed = compressionType.decompress(buffered);
            // Wrap with size-limiting stream for decompression bomb protection
            decompressedStream = new SizeLimitedInputStream(rawDecompressed, maxDecompressedSize);
            cpioReader = new CpioArchiveReader(decompressedStream);
            log.trace("CPIO archive reader initialized");
        }
    }

    /**
     * Validates a path for security issues like path traversal.
     *
     * @param path the path to validate
     * @return the validated path, or null if the path is invalid
     */
    private String validatePath(String path) {
        if (path == null || path.isEmpty()) {
            log.warn("Empty or null path in payload");
            return null;
        }

        // Check path length
        if (path.length() > MAX_PATH_LENGTH) {
            log.warn("Path exceeds maximum length ({}): {}", MAX_PATH_LENGTH, path.length());
            return null;
        }

        // Normalize path separators
        String normalized = path.replace('\\', '/');

        // Check for null bytes (can be used to bypass filters)
        if (normalized.contains("\0")) {
            log.warn("Path contains null bytes (possible path traversal): {}", path);
            return null;
        }

        // Split path and check each component
        String[] components = normalized.split("/");
        int depth = 0;

        for (String component : components) {
            if (component.isEmpty() || component.equals(".")) {
                // Empty components and "." are OK
                continue;
            }

            if (component.equals("..")) {
                depth--;
                // If depth goes negative, we're escaping the root
                if (depth < 0) {
                    log.warn("Path traversal detected (escapes root): {}", path);
                    return null;
                }
            } else {
                depth++;
            }
        }

        return path;
    }

    /**
     * Validates a symlink target for security issues like path traversal.
     * Symlink targets are validated differently from paths:
     * - Relative symlinks are allowed as long as they don't escape the package root
     * - Absolute symlinks to sensitive system paths are flagged
     *
     * @param target the symlink target to validate
     * @param symlinkPath the path of the symlink itself (for context in relative resolution)
     * @return the validated target, or null if the target is dangerous
     */
    private String validateSymlinkTarget(String target, String symlinkPath) {
        if (target == null || target.isEmpty()) {
            log.warn("Empty or null symlink target for {}", symlinkPath);
            return null;
        }

        // Check target length
        if (target.length() > MAX_SYMLINK_TARGET_LENGTH) {
            log.warn("Symlink target exceeds maximum length ({}) for {}: {}",
                    MAX_SYMLINK_TARGET_LENGTH, symlinkPath, target.length());
            return null;
        }

        // Check for null bytes (can be used to bypass filters)
        if (target.contains("\0")) {
            log.warn("Symlink target contains null bytes (possible path traversal) for {}: {}",
                    symlinkPath, target);
            return null;
        }

        // Normalize path separators
        String normalizedTarget = target.replace('\\', '/');

        // For absolute symlink targets, validate they don't point to sensitive locations
        if (normalizedTarget.startsWith("/")) {
            // Absolute symlinks are validated similarly to regular paths
            String validated = validatePath(normalizedTarget);
            if (validated == null) {
                log.warn("Absolute symlink target failed validation for {}: {}", symlinkPath, target);
                return null;
            }
            return target;
        }

        // For relative symlinks, calculate the effective path and ensure it doesn't escape
        // the directory structure implied by the symlink location
        String symlinkDir = symlinkPath.contains("/")
                ? symlinkPath.substring(0, symlinkPath.lastIndexOf('/'))
                : "";

        // Count how deep the symlink is from root
        int symlinkDepth = 0;
        for (String component : symlinkDir.split("/")) {
            if (!component.isEmpty() && !component.equals(".")) {
                symlinkDepth++;
            }
        }

        // Now check if the relative target escapes
        String[] components = normalizedTarget.split("/");
        int depth = symlinkDepth;

        for (String component : components) {
            if (component.isEmpty() || component.equals(".")) {
                continue;
            }

            if (component.equals("..")) {
                depth--;
                if (depth < 0) {
                    log.warn("Symlink target escapes package root for {}: {} (resolves outside root)",
                            symlinkPath, target);
                    return null;
                }
            } else {
                depth++;
            }
        }

        return target;
    }

    /**
     * An input stream that limits the number of bytes that can be read,
     * protecting against decompression bombs.
     */
    private static class SizeLimitedInputStream extends InputStream {
        private final InputStream delegate;
        private final long maxSize;
        private long bytesRead = 0;

        SizeLimitedInputStream(InputStream delegate, long maxSize) {
            this.delegate = delegate;
            this.maxSize = maxSize;
        }

        @Override
        public int read() throws IOException {
            if (bytesRead >= maxSize) {
                log.error("Decompression bomb detected: data exceeds {} bytes limit", maxSize);
                throw new IOException("Decompressed data exceeds maximum allowed size of " + maxSize + " bytes");
            }
            int b = delegate.read();
            if (b >= 0) {
                bytesRead++;
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (bytesRead >= maxSize) {
                log.error("Decompression bomb detected: data exceeds {} bytes limit", maxSize);
                throw new IOException("Decompressed data exceeds maximum allowed size of " + maxSize + " bytes");
            }
            // Limit the read to not exceed maxSize
            long remaining = maxSize - bytesRead;
            int toRead = (int) Math.min(len, remaining);
            int read = delegate.read(b, off, toRead);
            if (read > 0) {
                bytesRead += read;
            }
            return read;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        @Override
        public int available() throws IOException {
            return delegate.available();
        }

        @Override
        public long skip(long n) throws IOException {
            if (bytesRead >= maxSize) {
                log.error("Decompression bomb detected: data exceeds {} bytes limit", maxSize);
                throw new IOException("Decompressed data exceeds maximum allowed size of " + maxSize + " bytes");
            }
            long remaining = maxSize - bytesRead;
            long toSkip = Math.min(n, remaining);
            long skipped = delegate.skip(toSkip);
            bytesRead += skipped;
            return skipped;
        }
    }
}
