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
import org.jetbrains.annotations.Nullable;

/**
 * Base exception for all package reading errors.
 *
 * <p>This exception is thrown when a package file cannot be read, parsed,
 * or is in an invalid format. Subclasses provide more specific error types.
 */
public class PackageException extends Exception {

    private final @Nullable PackageFormat format;

    /**
     * Constructs a new package exception with the specified message.
     *
     * @param message the detail message
     */
    public PackageException(@NotNull String message) {
        super(message);
        this.format = null;
    }

    /**
     * Constructs a new package exception with the specified message and cause.
     *
     * @param message the detail message
     * @param cause the cause of this exception
     */
    public PackageException(@NotNull String message, @NotNull Throwable cause) {
        super(message, cause);
        this.format = null;
    }

    /**
     * Constructs a new package exception with format context.
     *
     * @param message the detail message
     * @param format the package format being processed
     */
    public PackageException(@NotNull String message, @Nullable PackageFormat format) {
        super(message);
        this.format = format;
    }

    /**
     * Constructs a new package exception with format context and cause.
     *
     * @param message the detail message
     * @param format the package format being processed
     * @param cause the cause of this exception
     */
    public PackageException(@NotNull String message, @Nullable PackageFormat format, @NotNull Throwable cause) {
        super(message, cause);
        this.format = format;
    }

    /**
     * Returns the package format being processed when the error occurred.
     *
     * @return the format, or null if not applicable
     */
    public @Nullable PackageFormat getFormat() {
        return format;
    }

    /**
     * Exception thrown when a package file is malformed or invalid.
     */
    public static class InvalidPackageException extends PackageException {
        public InvalidPackageException(@NotNull String message) {
            super(message);
        }

        public InvalidPackageException(@NotNull String message, @NotNull Throwable cause) {
            super(message, cause);
        }

        public InvalidPackageException(@NotNull String message, @Nullable PackageFormat format) {
            super(message, format);
        }

        public InvalidPackageException(@NotNull String message, @Nullable PackageFormat format, @NotNull Throwable cause) {
            super(message, format, cause);
        }
    }

    /**
     * Exception thrown when a package format or feature is not supported.
     */
    public static class UnsupportedPackageException extends PackageException {
        public UnsupportedPackageException(@NotNull String message) {
            super(message);
        }

        public UnsupportedPackageException(@NotNull String message, @NotNull Throwable cause) {
            super(message, cause);
        }

        public UnsupportedPackageException(@NotNull String message, @Nullable PackageFormat format) {
            super(message, format);
        }

        public UnsupportedPackageException(@NotNull String message, @Nullable PackageFormat format, @NotNull Throwable cause) {
            super(message, format, cause);
        }
    }
}
