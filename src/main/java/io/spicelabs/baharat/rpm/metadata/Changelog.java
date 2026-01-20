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

/**
 * Represents a changelog entry in an RPM package.
 *
 * @param time the timestamp of the changelog entry
 * @param author the author of the changelog entry (name and email)
 * @param text the changelog text
 */
public record Changelog(
        @NotNull Instant time,
        @NotNull String author,
        @NotNull String text
) {
    /**
     * Creates a changelog entry from a Unix timestamp.
     *
     * @param unixTime the Unix timestamp in seconds
     * @param author the author
     * @param text the changelog text
     * @return the changelog entry
     */
    public static @NotNull Changelog fromUnixTime(long unixTime, @NotNull String author, @NotNull String text) {
        return new Changelog(Instant.ofEpochSecond(unixTime), author, text);
    }
}
