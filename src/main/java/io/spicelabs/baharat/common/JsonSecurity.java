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

import io.spicelabs.baharat.PackageException;
import io.spicelabs.baharat.PackageFormat;
import org.jetbrains.annotations.NotNull;

/**
 * Depth guard for GSON-based JSON parsing.
 *
 * <p>GSON's {@code JsonParser.parseString} is recursive descent with NO depth limit; a
 * hostile {@code [[[[...]]]]} manifest overflows the stack with a
 * {@code StackOverflowError} — an {@code Error} that no {@code catch (Exception)} can
 * contain. Call {@link #checkDepth(String, PackageFormat)} before handing
 * attacker-controlled JSON to GSON.
 */
public final class JsonSecurity {

    /** Maximum nesting depth of {@code [} / {@code {} accepted before GSON sees the text. */
    public static final int MAX_JSON_DEPTH = 512;

    private JsonSecurity() {
    }

    /**
     * Verifies that bracket nesting in {@code json} does not exceed {@link #MAX_JSON_DEPTH}.
     * Strings (double-quoted, with escape sequences) are skipped so brackets inside string
     * values do not count.
     *
     * @param json the attacker-controlled JSON text
     * @param format the package format (for the error)
     * @throws PackageException.InvalidPackageException if nesting exceeds the cap
     */
    public static void checkDepth(@NotNull String json, @NotNull PackageFormat format)
            throws PackageException {
        int depth = 0;
        int n = json.length();
        boolean inString = false;
        for (int i = 0; i < n; i++) {
            char c = json.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++; // skip escaped char
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            switch (c) {
                case '"' -> inString = true;
                case '[', '{' -> {
                    depth++;
                    if (depth > MAX_JSON_DEPTH) {
                        throw new PackageException.InvalidPackageException(
                                "JSON nesting depth exceeds maximum of " + MAX_JSON_DEPTH,
                                format);
                    }
                }
                case ']', '}' -> depth = Math.max(0, depth - 1);
                default -> { }
            }
        }
    }
}
