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
package io.spicelabs.baharat.freebsd;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.spicelabs.baharat.PackageException;
import io.spicelabs.baharat.PackageFormat;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * Parser for FreeBSD pkg manifest files (+MANIFEST and +COMPACT_MANIFEST).
 *
 * <p>FreeBSD manifests are JSON files with structure:
 * <pre>
 * {
 *   "name": "nginx",
 *   "version": "1.24.0",
 *   "origin": "www/nginx",
 *   "comment": "Robust and small HTTP server",
 *   "arch": "freebsd:14:x86:64",
 *   "maintainer": "maintainer@freebsd.org",
 *   "www": "https://nginx.org",
 *   "deps": {
 *     "pcre2": {"origin": "devel/pcre2", "version": "10.42"}
 *   },
 *   "files": {
 *     "/usr/local/sbin/nginx": "sha256hash..."
 *   }
 * }
 * </pre>
 */
public final class ManifestParser {

    private static final Gson gson = new Gson();

    private ManifestParser() {
        // Utility class
    }

    /**
     * Parses a manifest from an input stream.
     *
     * @param input the input stream
     * @return the parsed JSON object
     * @throws IOException if an I/O error occurs
     * @throws PackageException if the manifest is invalid
     */
    public static @NotNull JsonObject parse(@NotNull InputStream input)
            throws IOException, PackageException {
        return parse(new InputStreamReader(input, StandardCharsets.UTF_8));
    }

    /**
     * Parses a manifest from a reader.
     *
     * @param reader the reader
     * @return the parsed JSON object
     * @throws PackageException if the manifest is invalid
     */
    public static @NotNull JsonObject parse(@NotNull Reader reader) throws PackageException {
        try {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            throw new PackageException.InvalidPackageException(
                    "Failed to parse FreeBSD manifest: " + e.getMessage(),
                    PackageFormat.FREEBSD_PKG,
                    e);
        }
    }

    /**
     * Parses a manifest from a string.
     *
     * @param content the manifest content
     * @return the parsed JSON object
     * @throws PackageException if the manifest is invalid
     */
    public static @NotNull JsonObject parse(@NotNull String content) throws PackageException {
        try {
            return JsonParser.parseString(content).getAsJsonObject();
        } catch (Exception e) {
            throw new PackageException.InvalidPackageException(
                    "Failed to parse FreeBSD manifest: " + e.getMessage(),
                    PackageFormat.FREEBSD_PKG,
                    e);
        }
    }
}
