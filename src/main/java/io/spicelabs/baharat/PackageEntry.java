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
package io.spicelabs.baharat;

import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.time.Instant;

/**
 * Represents an entry (file, directory, or symlink) in a package payload.
 *
 * <p>This is a sealed interface with three implementations for each entry type:
 * <ul>
 *   <li>{@link FileEntry} - Regular files with content</li>
 *   <li>{@link DirectoryEntry} - Directories</li>
 *   <li>{@link SymlinkEntry} - Symbolic links</li>
 * </ul>
 *
 * <p>The sealed nature allows for exhaustive pattern matching in switch expressions:
 * <pre>{@code
 * String info = switch (entry) {
 *     case FileEntry f -> "File: " + f.path() + " (" + f.size() + " bytes)";
 *     case DirectoryEntry d -> "Dir: " + d.path();
 *     case SymlinkEntry s -> "Link: " + s.path() + " -> " + s.target();
 * };
 * }</pre>
 */
public sealed interface PackageEntry permits PackageEntry.FileEntry, PackageEntry.DirectoryEntry, PackageEntry.SymlinkEntry {

    /**
     * Returns the full path of this entry.
     *
     * @return the path
     */
    @NotNull String path();

    /**
     * Returns the Unix file mode (permissions and type bits).
     *
     * @return the mode
     */
    int mode();

    /**
     * Returns the modification time.
     *
     * @return the modification time
     */
    @NotNull Instant mtime();

    /**
     * Returns the owner user name.
     *
     * @return the user name
     */
    @NotNull String userName();

    /**
     * Returns the owner group name.
     *
     * @return the group name
     */
    @NotNull String groupName();

    /**
     * Returns true if this is a regular file.
     *
     * @return true if file
     */
    default boolean isFile() {
        return this instanceof FileEntry;
    }

    /**
     * Returns true if this is a directory.
     *
     * @return true if directory
     */
    default boolean isDirectory() {
        return this instanceof DirectoryEntry;
    }

    /**
     * Returns true if this is a symbolic link.
     *
     * @return true if symlink
     */
    default boolean isSymlink() {
        return this instanceof SymlinkEntry;
    }

    /**
     * Returns the Unix permission bits (lower 12 bits of mode).
     *
     * @return the permissions
     */
    default int permissions() {
        return mode() & 07777;
    }

    /**
     * A regular file entry with content.
     *
     * @param path the file path
     * @param mode the Unix mode
     * @param mtime the modification time
     * @param userName the owner user name
     * @param groupName the owner group name
     * @param size the file size in bytes
     * @param content the input stream for reading file content
     */
    record FileEntry(
            @NotNull String path,
            int mode,
            @NotNull Instant mtime,
            @NotNull String userName,
            @NotNull String groupName,
            long size,
            @NotNull InputStream content
    ) implements PackageEntry {
    }

    /**
     * A directory entry.
     *
     * @param path the directory path
     * @param mode the Unix mode
     * @param mtime the modification time
     * @param userName the owner user name
     * @param groupName the owner group name
     */
    record DirectoryEntry(
            @NotNull String path,
            int mode,
            @NotNull Instant mtime,
            @NotNull String userName,
            @NotNull String groupName
    ) implements PackageEntry {
    }

    /**
     * A symbolic link entry.
     *
     * @param path the symlink path
     * @param mode the Unix mode
     * @param mtime the modification time
     * @param userName the owner user name
     * @param groupName the owner group name
     * @param target the symlink target path
     */
    record SymlinkEntry(
            @NotNull String path,
            int mode,
            @NotNull Instant mtime,
            @NotNull String userName,
            @NotNull String groupName,
            @NotNull String target
    ) implements PackageEntry {
    }
}
