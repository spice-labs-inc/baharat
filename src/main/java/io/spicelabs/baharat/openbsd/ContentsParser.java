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
package io.spicelabs.baharat.openbsd;

import io.spicelabs.baharat.PackageException;
import io.spicelabs.baharat.common.FileInfo;
import io.spicelabs.baharat.common.SecurityUtils;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Parser for OpenBSD +CONTENTS packing list files.
 *
 * <p>The +CONTENTS file uses a line-oriented format with @ directives:
 * <pre>
 * &#64;name nginx-1.24.0
 * &#64;depend www/pcre2:pcre2-*:pcre2-10.42
 * &#64;pkgpath www/nginx
 * &#64;comment Description of the package
 * &#64;sha /usr/local/sbin/nginx=abc123...
 * &#64;size /usr/local/sbin/nginx=1048576
 * /usr/local/sbin/nginx
 * /usr/local/etc/nginx/nginx.conf
 * </pre>
 */
public final class ContentsParser {

    private ContentsParser() {
        // Utility class
    }

    /**
     * Result of parsing +CONTENTS file.
     */
    public record ParseResult(
            @NotNull Map<String, String> metadata,
            @NotNull List<String> dependencies,
            @NotNull List<FileInfo> files
    ) {
    }

    /**
     * Parses a +CONTENTS file from an input stream.
     *
     * @param input the input stream
     * @return the parse result
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull ParseResult parse(@NotNull InputStream input) throws IOException {
        return parse(new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8)));
    }

    /**
     * Parses a +CONTENTS file from a reader.
     *
     * @param reader the reader
     * @return the parse result
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull ParseResult parse(@NotNull BufferedReader reader) throws IOException {
        Map<String, String> metadata = new LinkedHashMap<>();
        List<String> dependencies = new ArrayList<>();
        List<FileInfo> files = new ArrayList<>();
        Map<String, String> sha = new LinkedHashMap<>();
        Map<String, Long> sizes = new LinkedHashMap<>();

        String line;
        while ((line = reader.readLine()) != null) {
            // Security: Check line length to prevent DoS
            if (!SecurityUtils.isLineLengthValid(line)) {
                continue; // Skip excessively long lines
            }

            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            if (line.startsWith("@")) {
                // Parse directive
                int spacePos = line.indexOf(' ');
                String directive;
                String value;
                if (spacePos > 0) {
                    directive = line.substring(1, spacePos);
                    value = line.substring(spacePos + 1).trim();
                } else {
                    directive = line.substring(1);
                    value = "";
                }

                switch (directive) {
                    case "name" -> metadata.put("name", value);
                    case "pkgpath" -> metadata.put("pkgpath", value);
                    case "comment" -> metadata.put("comment", value);
                    case "arch" -> metadata.put("arch", value);
                    case "homepage" -> metadata.put("homepage", value);
                    case "maintainer" -> metadata.put("maintainer", value);
                    case "depend" -> dependencies.add(value);
                    case "wantlib" -> dependencies.add("lib:" + value);
                    case "sha" -> {
                        // Format: path=hash
                        int eqPos = value.indexOf('=');
                        if (eqPos > 0) {
                            sha.put(value.substring(0, eqPos), value.substring(eqPos + 1));
                        }
                    }
                    case "size" -> {
                        // Format: path=size
                        int eqPos = value.indexOf('=');
                        if (eqPos > 0) {
                            try {
                                sizes.put(value.substring(0, eqPos), Long.parseLong(value.substring(eqPos + 1)));
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                    case "ts" -> {
                        // Timestamp - metadata
                        metadata.put("timestamp", value);
                    }
                    // Ignore other directives like @cwd, @exec, @unexec, etc.
                }
            } else {
                // Regular file path
                String path = line;
                long size = sizes.getOrDefault(path, 0L);
                String digest = sha.get(path);

                files.add(new FileInfo(
                        path,
                        size,
                        FileInfo.S_IFREG | 0644,
                        java.time.Instant.EPOCH,
                        "root",
                        "wheel",
                        digest != null ? Optional.of(digest) : Optional.empty(),
                        Optional.empty(),
                        0
                ));
            }
        }

        return new ParseResult(metadata, dependencies, files);
    }

    /**
     * Parses a +CONTENTS file from a string.
     *
     * @param content the +CONTENTS content
     * @return the parse result
     */
    public static @NotNull ParseResult parse(@NotNull String content) {
        try {
            return parse(new BufferedReader(new java.io.StringReader(content)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
