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
package io.spicelabs.baharat.common;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Security utilities for validating paths and symlink targets.
 * Used to prevent path traversal attacks in package readers.
 */
public final class SecurityUtils {

    private static final Logger log = LoggerFactory.getLogger(SecurityUtils.class);

    // Security limits
    public static final int MAX_PATH_LENGTH = 4096;
    public static final int MAX_SYMLINK_TARGET_LENGTH = 4096;
    public static final int MAX_LINE_LENGTH = 65536; // 64 KB

    private SecurityUtils() {
        // Utility class
    }

    /**
     * Validates a file path for security issues like path traversal.
     *
     * @param path the path to validate
     * @return the validated path, or null if the path is invalid
     */
    public static @Nullable String validatePath(@NotNull String path) {
        if (path.isEmpty()) {
            log.warn("Empty path in package");
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
                continue;
            }

            if (component.equals("..")) {
                depth--;
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
     * Symlink targets are validated to ensure they don't escape the package root.
     *
     * @param target the symlink target to validate
     * @param symlinkPath the path of the symlink itself (for context in relative resolution)
     * @return the validated target, or null if the target is dangerous
     */
    public static @Nullable String validateSymlinkTarget(@NotNull String target, @NotNull String symlinkPath) {
        if (target.isEmpty()) {
            log.warn("Empty symlink target for {}", symlinkPath);
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

        // For absolute symlink targets, validate they don't point to dangerous locations
        if (normalizedTarget.startsWith("/")) {
            String validated = validatePath(normalizedTarget);
            if (validated == null) {
                log.warn("Absolute symlink target failed validation for {}: {}", symlinkPath, target);
                return null;
            }
            return target;
        }

        // For relative symlinks, calculate the effective path and ensure it doesn't escape
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
     * Validates that a line length is within acceptable limits.
     *
     * @param line the line to check
     * @return true if the line is within limits, false otherwise
     */
    public static boolean isLineLengthValid(@NotNull String line) {
        if (line.length() > MAX_LINE_LENGTH) {
            log.warn("Line exceeds maximum length ({}): {}", MAX_LINE_LENGTH, line.length());
            return false;
        }
        return true;
    }
}
