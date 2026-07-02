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

import io.spicelabs.baharat.PackageMetadata;
import io.spicelabs.baharat.common.Dependency;
import io.spicelabs.baharat.common.FileInfo;
import io.spicelabs.baharat.common.PurlHelper;
import io.spicelabs.coordinates.Purl;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Metadata for OpenBSD packages, parsed from +CONTENTS and +DESC.
 */
public final class OpenBsdMetadata implements PackageMetadata {

    // Pattern for OpenBSD package names: name-version
    private static final Pattern NAME_VERSION_PATTERN = Pattern.compile("^(.+?)-([0-9].*)$");

    private final @NotNull Map<String, String> metadata;
    private final @NotNull List<Dependency> dependencies;
    private final @NotNull List<FileInfo> files;
    private final @NotNull String description;

    /**
     * Creates metadata from parsed +CONTENTS.
     *
     * @param parseResult the +CONTENTS parse result
     * @param description the +DESC content
     */
    public OpenBsdMetadata(@NotNull ContentsParser.ParseResult parseResult, @NotNull String description) {
        this.metadata = parseResult.metadata();
        this.dependencies = parseDependencies(parseResult.dependencies());
        this.files = parseResult.files();
        this.description = description;
    }

    @Override
    public @NotNull String name() {
        String fullName = metadata.getOrDefault("name", "");
        Matcher m = NAME_VERSION_PATTERN.matcher(fullName);
        if (m.matches()) {
            return m.group(1);
        }
        return fullName;
    }

    @Override
    public @NotNull String version() {
        String fullName = metadata.getOrDefault("name", "");
        Matcher m = NAME_VERSION_PATTERN.matcher(fullName);
        if (m.matches()) {
            return m.group(2);
        }
        return "";
    }

    @Override
    public @NotNull String arch() {
        return metadata.getOrDefault("arch", "");
    }

    @Override
    public @NotNull Optional<String> description() {
        return description.isEmpty() ? Optional.empty() : Optional.of(description);
    }

    @Override
    public @NotNull Optional<String> summary() {
        return Optional.ofNullable(metadata.get("comment"));
    }

    @Override
    public @NotNull Optional<String> maintainer() {
        return Optional.ofNullable(metadata.get("maintainer"));
    }

    @Override
    public @NotNull Optional<String> url() {
        return Optional.ofNullable(metadata.get("homepage"));
    }

    @Override
    public long installedSize() {
        return files.stream().mapToLong(FileInfo::size).sum();
    }

    @Override
    public @NotNull List<Dependency> dependencies() {
        return Collections.unmodifiableList(dependencies);
    }

    @Override
    public @NotNull List<FileInfo> files() {
        return Collections.unmodifiableList(files);
    }

    @Override
    public @NotNull Purl purl() {
        var qualifiers = PurlHelper.newQualifiers();
        if (!PurlHelper.isArchitectureIndependent(arch())) {
            qualifiers.put("arch", arch());
        }

        return PurlHelper.build("openbsd", null, name(), version().isEmpty() ? null : version(), qualifiers);
    }

    // OpenBSD-specific fields

    /**
     * Returns the pkgpath (e.g., "www/nginx").
     *
     * @return an Optional containing the pkgpath
     */
    public @NotNull Optional<String> pkgpath() {
        return Optional.ofNullable(metadata.get("pkgpath"));
    }

    /**
     * Returns the full package name (name-version).
     *
     * @return the full name
     */
    public @NotNull String fullName() {
        return metadata.getOrDefault("name", "");
    }

    /**
     * Returns the raw metadata map.
     *
     * @return the metadata
     */
    public @NotNull Map<String, String> getMetadata() {
        return Collections.unmodifiableMap(metadata);
    }

    private @NotNull List<Dependency> parseDependencies(@NotNull List<String> depStrings) {
        List<Dependency> deps = new ArrayList<>();

        for (String dep : depStrings) {
            if (dep.startsWith("lib:")) {
                // Library dependency (wantlib)
                deps.add(Dependency.of(Dependency.Type.REQUIRES, dep));
            } else {
                // Package dependency
                // Format: pkgpath:pattern:actual
                // Example: www/pcre2:pcre2-*:pcre2-10.42
                String[] parts = dep.split(":");
                if (parts.length >= 1) {
                    String name = parts[parts.length - 1]; // Use actual name if available
                    deps.add(Dependency.of(Dependency.Type.REQUIRES, name));
                }
            }
        }

        return deps;
    }
}
