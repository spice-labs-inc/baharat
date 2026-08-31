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
package io.spicelabs.baharat.rpm.extract;

import io.spicelabs.baharat.rpm.RpmReader;
import io.spicelabs.baharat.rpm.exception.FormatException;
import io.spicelabs.baharat.rpm.payload.PayloadEntry;
import io.spicelabs.baharat.rpm.payload.PayloadReader;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Extracts files from RPM packages to the filesystem.
 *
 * <p>This utility class provides methods to extract all or selected files
 * from an RPM package's payload to a target directory.
 *
 * <p>Example usage:
 * <pre>{@code
 * // Extract all files
 * Extractor.extract(Path.of("package.rpm"), Path.of("/tmp/extracted"));
 *
 * // Extract with options
 * Extractor extractor = Extractor.builder()
 *     .preservePermissions(true)
 *     .overwrite(true)
 *     .filter(entry -> entry.path().startsWith("/usr/bin"))
 *     .build();
 * ExtractionResult result = extractor.extract(Path.of("package.rpm"), Path.of("/tmp/bin"));
 *
 * System.out.println("Extracted " + result.fileCount() + " files");
 * }</pre>
 *
 * @see PayloadEntry
 */
public final class Extractor {

    private static final Logger log = LoggerFactory.getLogger(Extractor.class);

    private final boolean preservePermissions;
    private final boolean preserveOwnership;
    private final boolean overwrite;
    private final boolean createSymlinks;
    private final Predicate<PayloadEntry> filter;

    private Extractor(@NotNull Builder builder) {
        this.preservePermissions = builder.preservePermissions;
        this.preserveOwnership = builder.preserveOwnership;
        this.overwrite = builder.overwrite;
        this.createSymlinks = builder.createSymlinks;
        this.filter = builder.filter;
    }

    /**
     * Extracts all files from an RPM to the target directory using default settings.
     *
     * @param rpmPath path to the RPM file
     * @param targetDir target directory for extraction
     * @return the extraction result
     * @throws IOException if an I/O error occurs
     * @throws FormatException if the RPM file is invalid
     */
    public static @NotNull ExtractionResult extract(@NotNull Path rpmPath, @NotNull Path targetDir)
            throws IOException, FormatException {
        return builder().build().extractTo(rpmPath, targetDir);
    }

    /**
     * Creates a new builder for configuring extraction options.
     *
     * @return a new builder
     */
    public static @NotNull Builder builder() {
        return new Builder();
    }

    /**
     * Extracts files from the RPM to the target directory.
     *
     * @param rpmPath path to the RPM file
     * @param targetDir target directory for extraction
     * @return the extraction result
     * @throws IOException if an I/O error occurs
     * @throws FormatException if the RPM file is invalid
     */
    public @NotNull ExtractionResult extractTo(@NotNull Path rpmPath, @NotNull Path targetDir)
            throws IOException, FormatException {
        // Ensure target directory exists
        Files.createDirectories(targetDir);

        // Normalize target to absolute path for security
        Path normalizedTarget = targetDir.toAbsolutePath().normalize();

        List<String> extractedFiles = new ArrayList<>();
        List<String> skippedFiles = new ArrayList<>();
        List<ExtractionError> errors = new ArrayList<>();

        try (PayloadReader reader = RpmReader.openPayload(rpmPath)) {
            PayloadEntry entry;
            while ((entry = reader.nextEntry()) != null) {
                // Apply filter
                if (filter != null && !filter.test(entry)) {
                    skippedFiles.add(entry.path());
                    continue;
                }

                try {
                    extractEntry(entry, normalizedTarget, extractedFiles, skippedFiles);
                } catch (IOException e) {
                    errors.add(new ExtractionError(entry.path(), e.getMessage()));
                    log.warn("Failed to extract {}: {}", entry.path(), e.getMessage());
                }
            }
        }

        log.info("Extracted {} files to {}, {} skipped, {} errors",
                extractedFiles.size(), targetDir, skippedFiles.size(), errors.size());

        return new ExtractionResult(extractedFiles, skippedFiles, errors);
    }

    private void extractEntry(@NotNull PayloadEntry entry, @NotNull Path targetDir,
                              @NotNull List<String> extracted, @NotNull List<String> skipped)
            throws IOException {
        // Compute target path
        String entryPath = entry.path();
        if (entryPath.startsWith("/")) {
            entryPath = entryPath.substring(1);
        }

        Path targetPath = targetDir.resolve(entryPath).normalize();

        // Security check: ensure path doesn't escape target directory
        if (!targetPath.startsWith(targetDir)) {
            log.warn("Path traversal attempt blocked: {}", entry.path());
            skipped.add(entry.path());
            return;
        }

        // Security (Fresh Scent Phase 6, finding B13, decision D7): a PREVIOUS archive
        // entry may have created a symlink anywhere in the parent chain of this path.
        // Writing or deleting through it would operate OUTSIDE the target directory
        // (e.g. [symlink "etc" -> /etc, file "etc/passwd"] would clobber /etc/passwd).
        // Verify every component is a real directory before operating.
        verifySafeParents(targetDir, targetPath);

        if (entry instanceof PayloadEntry.DirectoryEntry dir) {
            extractDirectory(dir, targetPath, extracted);
        } else if (entry instanceof PayloadEntry.SymlinkEntry symlink) {
            extractSymlink(symlink, targetPath, extracted, skipped);
        } else if (entry instanceof PayloadEntry.FileEntry file) {
            extractFile(file, targetPath, extracted);
        }
    }

    /**
     * Verifies that the parent chain of {@code targetPath} contains no symlink components
     * and resolves (realpath) inside {@code targetDir}. Fails loud on any violation —
     * never write or delete through an archive-created symlink (catalog §6/§7).
     */
    private void verifySafeParents(@NotNull Path targetDir, @NotNull Path targetPath)
            throws IOException {
        Path parent = targetPath.getParent();
        if (parent == null) {
            return;
        }
        Path current = targetDir;
        for (Path component : targetDir.relativize(parent)) {
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) {
                throw new IOException(
                        "Refusing to write through symlink component: " + current);
            }
        }
        // Final containment check via realpath (also catches symlinks created AFTER a
        // component was verified, and races with external actors).
        if (Files.exists(parent, LinkOption.NOFOLLOW_LINKS)) {
            Path realParent = parent.toRealPath();
            Path realTarget = targetDir.toRealPath();
            if (!realParent.startsWith(realTarget)) {
                throw new IOException(
                        "Refusing to write outside target directory via symlink: " + targetPath);
            }
        }
    }

    private void extractDirectory(@NotNull PayloadEntry.DirectoryEntry dir, @NotNull Path targetPath,
                                  @NotNull List<String> extracted) throws IOException {
        if (Files.exists(targetPath)) {
            if (!Files.isDirectory(targetPath)) {
                if (overwrite) {
                    Files.delete(targetPath);
                } else {
                    throw new FileAlreadyExistsException(targetPath.toString());
                }
            }
        }

        if (preservePermissions && isPosixSupported()) {
            Set<PosixFilePermission> perms = modeToPermissions(dir.mode());
            FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(perms);
            Files.createDirectories(targetPath, attr);
        } else {
            Files.createDirectories(targetPath);
        }

        extracted.add(dir.path());
        log.trace("Created directory: {}", targetPath);
    }

    private void extractSymlink(@NotNull PayloadEntry.SymlinkEntry symlink, @NotNull Path targetPath,
                                @NotNull List<String> extracted, @NotNull List<String> skipped)
            throws IOException {
        if (!createSymlinks) {
            skipped.add(symlink.path());
            return;
        }

        // Ensure parent directory exists
        Path parent = targetPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        // Handle existing file
        if (Files.exists(targetPath, LinkOption.NOFOLLOW_LINKS)) {
            if (overwrite) {
                Files.delete(targetPath);
            } else {
                throw new FileAlreadyExistsException(targetPath.toString());
            }
        }

        Path linkTarget = Path.of(symlink.target());
        Files.createSymbolicLink(targetPath, linkTarget);

        extracted.add(symlink.path());
        log.trace("Created symlink: {} -> {}", targetPath, symlink.target());
    }

    private void extractFile(@NotNull PayloadEntry.FileEntry file, @NotNull Path targetPath,
                             @NotNull List<String> extracted) throws IOException {
        // Ensure parent directory exists
        Path parent = targetPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        // Handle existing file
        if (Files.exists(targetPath)) {
            if (overwrite) {
                Files.delete(targetPath);
            } else {
                throw new FileAlreadyExistsException(targetPath.toString());
            }
        }

        // Copy content
        try (InputStream content = file.content()) {
            if (preservePermissions && isPosixSupported()) {
                Set<PosixFilePermission> perms = modeToPermissions(file.mode());
                FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(perms);
                try (OutputStream out = Files.newOutputStream(targetPath,
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                    content.transferTo(out);
                }
                Files.setPosixFilePermissions(targetPath, perms);
            } else {
                Files.copy(content, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        extracted.add(file.path());
        log.trace("Extracted file: {} ({} bytes)", targetPath, file.size());
    }

    private static @NotNull Set<PosixFilePermission> modeToPermissions(int mode) {
        Set<PosixFilePermission> perms = EnumSet.noneOf(PosixFilePermission.class);

        // Owner permissions
        if ((mode & 0400) != 0) perms.add(PosixFilePermission.OWNER_READ);
        if ((mode & 0200) != 0) perms.add(PosixFilePermission.OWNER_WRITE);
        if ((mode & 0100) != 0) perms.add(PosixFilePermission.OWNER_EXECUTE);

        // Group permissions
        if ((mode & 0040) != 0) perms.add(PosixFilePermission.GROUP_READ);
        if ((mode & 0020) != 0) perms.add(PosixFilePermission.GROUP_WRITE);
        if ((mode & 0010) != 0) perms.add(PosixFilePermission.GROUP_EXECUTE);

        // Others permissions
        if ((mode & 0004) != 0) perms.add(PosixFilePermission.OTHERS_READ);
        if ((mode & 0002) != 0) perms.add(PosixFilePermission.OTHERS_WRITE);
        if ((mode & 0001) != 0) perms.add(PosixFilePermission.OTHERS_EXECUTE);

        return perms;
    }

    private static boolean isPosixSupported() {
        return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    }

    /**
     * Builder for configuring Extractor instances.
     */
    public static final class Builder {
        private boolean preservePermissions = false;
        private boolean preserveOwnership = false;
        private boolean overwrite = false;
        private boolean createSymlinks = true;
        private Predicate<PayloadEntry> filter = null;

        private Builder() {}

        /**
         * Sets whether to preserve Unix file permissions.
         * Only works on POSIX-compatible systems (Linux, macOS).
         *
         * @param preserve true to preserve permissions
         * @return this builder
         */
        public @NotNull Builder preservePermissions(boolean preserve) {
            this.preservePermissions = preserve;
            return this;
        }

        /**
         * Sets whether to preserve file ownership (requires root).
         * Note: This is not currently implemented.
         *
         * @param preserve true to preserve ownership
         * @return this builder
         */
        public @NotNull Builder preserveOwnership(boolean preserve) {
            this.preserveOwnership = preserve;
            return this;
        }

        /**
         * Sets whether to overwrite existing files.
         *
         * @param overwrite true to overwrite existing files
         * @return this builder
         */
        public @NotNull Builder overwrite(boolean overwrite) {
            this.overwrite = overwrite;
            return this;
        }

        /**
         * Sets whether to create symbolic links.
         * If false, symlinks are skipped.
         *
         * @param create true to create symlinks
         * @return this builder
         */
        public @NotNull Builder createSymlinks(boolean create) {
            this.createSymlinks = create;
            return this;
        }

        /**
         * Sets a filter to select which entries to extract.
         *
         * @param filter predicate that returns true for entries to extract
         * @return this builder
         */
        public @NotNull Builder filter(@NotNull Predicate<PayloadEntry> filter) {
            this.filter = filter;
            return this;
        }

        /**
         * Builds the extractor.
         *
         * @return the configured extractor
         */
        public @NotNull Extractor build() {
            return new Extractor(this);
        }
    }

    /**
     * Result of an extraction operation.
     */
    public static final class ExtractionResult {
        private final List<String> extractedFiles;
        private final List<String> skippedFiles;
        private final List<ExtractionError> errors;

        ExtractionResult(@NotNull List<String> extractedFiles,
                         @NotNull List<String> skippedFiles,
                         @NotNull List<ExtractionError> errors) {
            this.extractedFiles = List.copyOf(extractedFiles);
            this.skippedFiles = List.copyOf(skippedFiles);
            this.errors = List.copyOf(errors);
        }

        /**
         * Returns true if extraction completed without errors.
         *
         * @return true if successful
         */
        public boolean isSuccessful() {
            return errors.isEmpty();
        }

        /**
         * Returns the number of files extracted.
         *
         * @return the file count
         */
        public int fileCount() {
            return extractedFiles.size();
        }

        /**
         * Returns the list of extracted file paths.
         *
         * @return unmodifiable list of paths
         */
        public @NotNull List<String> extractedFiles() {
            return extractedFiles;
        }

        /**
         * Returns the list of skipped file paths.
         *
         * @return unmodifiable list of paths
         */
        public @NotNull List<String> skippedFiles() {
            return skippedFiles;
        }

        /**
         * Returns the list of extraction errors.
         *
         * @return unmodifiable list of errors
         */
        public @NotNull List<ExtractionError> errors() {
            return errors;
        }

        @Override
        public String toString() {
            return String.format("ExtractionResult{extracted=%d, skipped=%d, errors=%d}",
                    extractedFiles.size(), skippedFiles.size(), errors.size());
        }
    }

    /**
     * Represents an error during extraction.
     */
    public record ExtractionError(@NotNull String path, @NotNull String message) {
        @Override
        public String toString() {
            return path + ": " + message;
        }
    }
}
