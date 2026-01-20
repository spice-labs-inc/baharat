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
package io.spicelabs.baharat.rpm.metadata;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Optional;

/**
 * Represents metadata about a file contained in an RPM package.
 *
 * @param path the full file path
 * @param size the file size in bytes
 * @param mode the Unix file mode (permissions and type)
 * @param mtime the modification time
 * @param flags the RPM file flags
 * @param userName the owner user name
 * @param groupName the owner group name
 * @param digest the file digest/checksum
 * @param linkTo the symlink target (empty for non-symlinks)
 */
public record FileInfo(
        @NotNull String path,
        long size,
        int mode,
        @NotNull Instant mtime,
        int flags,
        @NotNull String userName,
        @NotNull String groupName,
        @NotNull Optional<String> digest,
        @NotNull Optional<String> linkTo
) {
    // File flag constants
    public static final int RPMFILE_CONFIG = (1 << 0);
    public static final int RPMFILE_DOC = (1 << 1);
    public static final int RPMFILE_ICON = (1 << 2);
    public static final int RPMFILE_MISSINGOK = (1 << 3);
    public static final int RPMFILE_NOREPLACE = (1 << 4);
    public static final int RPMFILE_SPECFILE = (1 << 5);
    public static final int RPMFILE_GHOST = (1 << 6);
    public static final int RPMFILE_LICENSE = (1 << 7);
    public static final int RPMFILE_README = (1 << 8);
    public static final int RPMFILE_EXCLUDE = (1 << 9);
    public static final int RPMFILE_PUBKEY = (1 << 11);
    public static final int RPMFILE_ARTIFACT = (1 << 12);

    // File mode type masks (from POSIX)
    public static final int S_IFMT = 0170000;   // file type mask
    public static final int S_IFSOCK = 0140000; // socket
    public static final int S_IFLNK = 0120000;  // symbolic link
    public static final int S_IFREG = 0100000;  // regular file
    public static final int S_IFBLK = 0060000;  // block device
    public static final int S_IFDIR = 0040000;  // directory
    public static final int S_IFCHR = 0020000;  // character device
    public static final int S_IFIFO = 0010000;  // FIFO

    /**
     * Returns true if this is a regular file.
     *
     * @return true if regular file
     */
    public boolean isRegularFile() {
        return (mode & S_IFMT) == S_IFREG;
    }

    /**
     * Returns true if this is a directory.
     *
     * @return true if directory
     */
    public boolean isDirectory() {
        return (mode & S_IFMT) == S_IFDIR;
    }

    /**
     * Returns true if this is a symbolic link.
     *
     * @return true if symbolic link
     */
    public boolean isSymbolicLink() {
        return (mode & S_IFMT) == S_IFLNK;
    }

    /**
     * Returns true if this is a configuration file.
     *
     * @return true if configuration file
     */
    public boolean isConfig() {
        return (flags & RPMFILE_CONFIG) != 0;
    }

    /**
     * Returns true if this is a documentation file.
     *
     * @return true if documentation file
     */
    public boolean isDoc() {
        return (flags & RPMFILE_DOC) != 0;
    }

    /**
     * Returns true if this is a ghost file (not in payload).
     *
     * @return true if ghost file
     */
    public boolean isGhost() {
        return (flags & RPMFILE_GHOST) != 0;
    }

    /**
     * Returns true if this is a license file.
     *
     * @return true if license file
     */
    public boolean isLicense() {
        return (flags & RPMFILE_LICENSE) != 0;
    }

    /**
     * Returns true if this file should not be replaced on upgrade.
     *
     * @return true if noreplace
     */
    public boolean isNoReplace() {
        return (flags & RPMFILE_NOREPLACE) != 0;
    }

    /**
     * Returns the Unix permission bits (lower 12 bits of mode).
     *
     * @return the permission bits (e.g., 0755, 0644)
     */
    public int permissions() {
        return mode & 07777;
    }

    /**
     * Returns a string representation of the file type.
     *
     * @return the file type string
     */
    public @NotNull String fileType() {
        int type = mode & S_IFMT;
        return switch (type) {
            case S_IFREG -> "file";
            case S_IFDIR -> "directory";
            case S_IFLNK -> "symlink";
            case S_IFBLK -> "block device";
            case S_IFCHR -> "character device";
            case S_IFIFO -> "fifo";
            case S_IFSOCK -> "socket";
            default -> "unknown";
        };
    }
}
