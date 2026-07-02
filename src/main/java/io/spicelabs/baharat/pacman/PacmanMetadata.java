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
package io.spicelabs.baharat.pacman;

import io.spicelabs.baharat.PackageMetadata;
import io.spicelabs.baharat.common.Dependency;
import io.spicelabs.baharat.common.FileInfo;
import io.spicelabs.baharat.common.PurlHelper;
import io.spicelabs.coordinates.Purl;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Metadata for Pacman/ALPM packages, parsed from .PKGINFO.
 */
public final class PacmanMetadata implements PackageMetadata {

    private final @NotNull Map<String, Object> fields;
    private final @NotNull List<Dependency> dependencies;
    private final @NotNull List<Dependency> provides;
    private final @NotNull List<FileInfo> files;

    /**
     * Creates metadata from parsed .PKGINFO fields.
     *
     * @param fields the .PKGINFO fields
     */
    public PacmanMetadata(@NotNull Map<String, Object> fields) {
        this.fields = fields;
        this.dependencies = parseAllDependencies();
        this.provides = parseProvides();
        this.files = new ArrayList<>();
    }

    /**
     * Creates metadata from parsed .PKGINFO fields with file list.
     *
     * @param fields the .PKGINFO fields
     * @param files the package files
     */
    public PacmanMetadata(@NotNull Map<String, Object> fields, @NotNull List<FileInfo> files) {
        this.fields = fields;
        this.dependencies = parseAllDependencies();
        this.provides = parseProvides();
        this.files = new ArrayList<>(files);
    }

    @Override
    public @NotNull String name() {
        return getString("pkgname").orElse("");
    }

    @Override
    public @NotNull String version() {
        return getString("pkgver").orElse("");
    }

    @Override
    public @NotNull String arch() {
        return getString("arch").orElse("");
    }

    @Override
    public @NotNull Optional<String> description() {
        return getString("pkgdesc");
    }

    @Override
    public @NotNull Optional<String> summary() {
        return description();
    }

    @Override
    public @NotNull Optional<String> maintainer() {
        return getString("packager");
    }

    @Override
    public @NotNull Optional<String> url() {
        return getString("url");
    }

    @Override
    public @NotNull Optional<String> license() {
        List<String> licenses = getStringList("license");
        if (licenses.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(String.join(", ", licenses));
    }

    @Override
    public long installedSize() {
        return getString("size")
                .map(s -> {
                    try {
                        return Long.parseLong(s);
                    } catch (NumberFormatException e) {
                        return 0L;
                    }
                })
                .orElse(0L);
    }

    @Override
    public @NotNull Optional<Instant> buildTime() {
        return getString("builddate")
                .map(s -> {
                    try {
                        return Instant.ofEpochSecond(Long.parseLong(s));
                    } catch (NumberFormatException e) {
                        return null;
                    }
                });
    }

    @Override
    public @NotNull List<Dependency> dependencies() {
        return Collections.unmodifiableList(dependencies);
    }

    @Override
    public @NotNull List<Dependency> provides() {
        return Collections.unmodifiableList(provides);
    }

    @Override
    public @NotNull List<FileInfo> files() {
        return Collections.unmodifiableList(files);
    }

    @Override
    public @NotNull Optional<String> group() {
        List<String> groups = getStringList("group");
        if (groups.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(groups.get(0));
    }

    @Override
    public @NotNull Purl purl() {
        var qualifiers = PurlHelper.newQualifiers();
        if (!PurlHelper.isArchitectureIndependent(arch())) {
            qualifiers.put("arch", arch());
        }

        return PurlHelper.build("alpm", "arch", name(), version().isEmpty() ? null : version(), qualifiers);
    }

    // Pacman-specific fields

    /**
     * Returns the package base name.
     *
     * @return an Optional containing the pkgbase
     */
    public @NotNull Optional<String> pkgbase() {
        return getString("pkgbase");
    }

    /**
     * Returns the build host.
     *
     * @return an Optional containing the build host
     */
    public @NotNull Optional<String> buildHost() {
        return getString("buildhost");
    }

    /**
     * Returns the make dependencies.
     *
     * @return list of make dependencies
     */
    public @NotNull List<Dependency> makedepends() {
        return parseDependencyList("makedepend", Dependency.Type.BUILD_DEPENDS);
    }

    /**
     * Returns the check dependencies.
     *
     * @return list of check dependencies
     */
    public @NotNull List<Dependency> checkdepends() {
        return parseDependencyList("checkdepend", Dependency.Type.BUILD_DEPENDS);
    }

    /**
     * Returns the optional dependencies.
     *
     * @return list of optional dependencies with descriptions
     */
    public @NotNull List<String> optdepends() {
        return getStringList("optdepend");
    }

    /**
     * Returns the conflicts.
     *
     * @return list of conflicts
     */
    public @NotNull List<Dependency> conflicts() {
        return parseDependencyList("conflict", Dependency.Type.CONFLICTS);
    }

    /**
     * Returns the packages this one replaces.
     *
     * @return list of replaced packages
     */
    public @NotNull List<Dependency> replaces() {
        return parseDependencyList("replaces", Dependency.Type.OBSOLETES);
    }

    /**
     * Returns backup files (config files to preserve on upgrade).
     *
     * @return list of backup file paths
     */
    public @NotNull List<String> backup() {
        return getStringList("backup");
    }

    /**
     * Returns a raw field value as string.
     *
     * @param name the field name
     * @return an Optional containing the field value
     */
    public @NotNull Optional<String> getString(@NotNull String name) {
        Object value = fields.get(name);
        if (value instanceof String s) {
            return Optional.of(s);
        }
        return Optional.empty();
    }

    /**
     * Returns a field value as a list.
     *
     * @param name the field name
     * @return the list of values (empty if field not present)
     */
    @SuppressWarnings("unchecked")
    public @NotNull List<String> getStringList(@NotNull String name) {
        Object value = fields.get(name);
        if (value instanceof List<?> list) {
            return (List<String>) list;
        }
        return Collections.emptyList();
    }

    private @NotNull List<Dependency> parseAllDependencies() {
        return parseDependencyList("depend", Dependency.Type.REQUIRES);
    }

    private @NotNull List<Dependency> parseProvides() {
        return parseDependencyList("provides", Dependency.Type.PROVIDES);
    }

    private @NotNull List<Dependency> parseDependencyList(@NotNull String fieldName, @NotNull Dependency.Type type) {
        List<String> values = getStringList(fieldName);
        if (values.isEmpty()) {
            return Collections.emptyList();
        }

        List<Dependency> deps = new ArrayList<>(values.size());
        for (String value : values) {
            deps.add(parseDependency(value, type));
        }
        return deps;
    }

    private @NotNull Dependency parseDependency(@NotNull String spec, @NotNull Dependency.Type type) {
        // Pacman dependency format: name[<>=]version
        // Examples: openssl, openssl>=1.1, openssl=1.1.1
        int opStart = -1;
        Dependency.Operator op = Dependency.Operator.ANY;

        if ((opStart = spec.indexOf(">=")) > 0) {
            op = Dependency.Operator.GREATER_THAN_OR_EQUAL;
        } else if ((opStart = spec.indexOf("<=")) > 0) {
            op = Dependency.Operator.LESS_THAN_OR_EQUAL;
        } else if ((opStart = spec.indexOf(">")) > 0) {
            op = Dependency.Operator.GREATER_THAN;
        } else if ((opStart = spec.indexOf("<")) > 0) {
            op = Dependency.Operator.LESS_THAN;
        } else if ((opStart = spec.indexOf("=")) > 0) {
            op = Dependency.Operator.EQUAL;
        }

        if (opStart > 0) {
            String name = spec.substring(0, opStart);
            String version = spec.substring(opStart + (op == Dependency.Operator.GREATER_THAN_OR_EQUAL ||
                    op == Dependency.Operator.LESS_THAN_OR_EQUAL ? 2 : 1));
            return Dependency.of(type, name, op, version);
        }

        return Dependency.of(type, spec);
    }
}
