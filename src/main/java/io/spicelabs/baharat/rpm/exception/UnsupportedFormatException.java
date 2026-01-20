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
package io.spicelabs.baharat.rpm.exception;

import org.jetbrains.annotations.NotNull;

/**
 * Exception thrown when an RPM file uses features not supported by this library.
 * This includes unsupported compression formats, unknown tag types, or
 * future format versions.
 */
public class UnsupportedFormatException extends FormatException {

    /**
     * Constructs a new unsupported RPM exception with the specified message.
     *
     * @param message the detail message
     */
    public UnsupportedFormatException(@NotNull String message) {
        super(message);
    }

    /**
     * Constructs a new unsupported RPM exception with the specified message and cause.
     *
     * @param message the detail message
     * @param cause the cause of this exception
     */
    public UnsupportedFormatException(@NotNull String message, @NotNull Throwable cause) {
        super(message, cause);
    }
}
