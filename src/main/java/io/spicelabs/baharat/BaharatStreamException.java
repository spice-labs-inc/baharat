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
 * Unchecked exception thrown when a package format violation is detected MID-STREAM by a
 * {@code Stream<...>}-based API.
 *
 * <p>Java streams cannot propagate checked exceptions, so stream-lambda paths use this
 * dedicated, documented wrapper instead of bare {@link RuntimeException}. Every
 * {@code streamPayload(...)} method's javadoc names this type; callers that must treat
 * hostile input as recoverable catch it explicitly. Non-stream entry points throw the
 * checked {@link PackageException} hierarchy.
 *
 * <p>It carries the checked cause ({@link PackageException} or {@link java.io.IOException})
 * so callers can recover the precise failure.
 */
public class BaharatStreamException extends RuntimeException {

    private final @Nullable PackageFormat format;

    public BaharatStreamException(@NotNull String message) {
        super(message);
        this.format = null;
    }

    public BaharatStreamException(@NotNull String message, @NotNull Throwable cause) {
        super(message, cause);
        this.format = null;
    }

    public BaharatStreamException(@NotNull String message, @Nullable PackageFormat format,
                                  @NotNull Throwable cause) {
        super(message, cause);
        this.format = format;
    }

    /** The package format being processed, if known. */
    public @Nullable PackageFormat getFormat() {
        return format;
    }
}
