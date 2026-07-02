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
package io.spicelabs.baharat.deb;

import io.spicelabs.baharat.PackageMetadata;
import io.spicelabs.baharat.common.Dependency;
import io.spicelabs.baharat.common.FileInfo;
import io.spicelabs.baharat.common.PurlHelper;
import io.spicelabs.coordinates.Purl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Metadata for Debian packages, parsed from the control file.
 *
 * <p>The control file uses RFC 822-like format with fields separated by colons.
 * Multi-line values have continuation lines starting with whitespace.
 */
public final class DebMetadata implements PackageMetadata {

    private final @NotNull Map<String, String> fields;
    private final @NotNull List<Dependency> dependencies;
    private final @NotNull List<Dependency> provides;
    private final @NotNull List<FileInfo> files;
    private final String rawControlContent;
    private final @Nullable String sourcePath;

    /**
     * Creates metadata from parsed control file fields.
     *
     * @param fields the control file fields
     */
    public DebMetadata(@NotNull Map<String, String> fields) {
        this(fields, new ArrayList<>(), null, null);
    }

    /**
     * Creates metadata from parsed control file fields with file list.
     *
     * @param fields the control file fields
     * @param files the package files
     */
    public DebMetadata(@NotNull Map<String, String> fields, @NotNull List<FileInfo> files) {
        this(fields, files, null, null);
    }

    /**
     * Creates metadata from parsed control file fields with file list and raw content.
     *
     * @param fields the control file fields
     * @param files the package files
     * @param rawControlContent the raw control file content
     */
    public DebMetadata(@NotNull Map<String, String> fields, @NotNull List<FileInfo> files, String rawControlContent) {
        this(fields, files, rawControlContent, null);
    }

    /**
     * Creates metadata from parsed control file fields with raw content and source path.
     *
     * @param fields the control file fields
     * @param files the package files
     * @param rawControlContent the raw control file content
     * @param sourcePath the original path or name of the package
     */
    public DebMetadata(@NotNull Map<String, String> fields, @NotNull List<FileInfo> files, String rawControlContent, @Nullable String sourcePath) {
        this.fields = new LinkedHashMap<>(fields);
        this.dependencies = parseAllDependencies();
        this.provides = parseProvides();
        this.files = new ArrayList<>(files);
        this.rawControlContent = rawControlContent;
        this.sourcePath = sourcePath;
    }

    @Override
    public @NotNull String name() {
        return fields.getOrDefault("Package", "");
    }

    @Override
    public @NotNull String version() {
        return fields.getOrDefault("Version", "");
    }

    @Override
    public @NotNull String arch() {
        return fields.getOrDefault("Architecture", "");
    }

    @Override
    public @NotNull Optional<String> summary() {
        String desc = fields.get("Description");
        if (desc != null) {
            int newline = desc.indexOf('\n');
            return Optional.of(newline > 0 ? desc.substring(0, newline) : desc);
        }
        return Optional.empty();
    }

    @Override
    public @NotNull Optional<String> description() {
        return Optional.ofNullable(fields.get("Description"));
    }

    @Override
    public @NotNull Optional<String> maintainer() {
        return Optional.ofNullable(fields.get("Maintainer"));
    }

    @Override
    public @NotNull Optional<String> url() {
        return Optional.ofNullable(fields.get("Homepage"));
    }

    @Override
    public long installedSize() {
        String size = fields.get("Installed-Size");
        if (size != null) {
            try {
                // Installed-Size is in kilobytes
                // Use multiplyExact to detect overflow
                return Math.multiplyExact(Long.parseLong(size.trim()), 1024L);
            } catch (NumberFormatException | ArithmeticException e) {
                // Return 0 for invalid or overflowing values
                return 0;
            }
        }
        return 0;
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
        return Optional.ofNullable(fields.get("Section"));
    }

    @Override
    public @NotNull Purl purl() {
        String namespace = DebPackage.inferNamespace(sourcePath == null ? "" : sourcePath)
                .orElse("debian");

        var qualifiers = PurlHelper.newQualifiers();
        if (!PurlHelper.isArchitectureIndependent(arch())) {
            qualifiers.put("arch", arch());
        }

        return PurlHelper.build("deb", namespace, name(), version().isEmpty() ? null : version(), qualifiers);
    }

    // DEB-specific fields

    /**
     * Returns the package priority (required, important, standard, optional, extra).
     *
     * @return an Optional containing the priority
     */
    public @NotNull Optional<String> priority() {
        return Optional.ofNullable(fields.get("Priority"));
    }

    /**
     * Returns the source package name.
     *
     * @return an Optional containing the source package
     */
    public @NotNull Optional<String> source() {
        return Optional.ofNullable(fields.get("Source"));
    }

    /**
     * Returns the essential flag.
     *
     * @return true if the package is marked essential
     */
    public boolean isEssential() {
        return "yes".equalsIgnoreCase(fields.get("Essential"));
    }

    /**
     * Returns pre-depends (dependencies that must be configured before unpacking).
     *
     * @return list of pre-dependencies
     */
    public @NotNull List<Dependency> preDepends() {
        return parseDependencyField("Pre-Depends", Dependency.Type.PRE_DEPENDS);
    }

    /**
     * Returns recommended packages.
     *
     * @return list of recommendations
     */
    public @NotNull List<Dependency> recommends() {
        return parseDependencyField("Recommends", Dependency.Type.RECOMMENDS);
    }

    /**
     * Returns suggested packages.
     *
     * @return list of suggestions
     */
    public @NotNull List<Dependency> suggests() {
        return parseDependencyField("Suggests", Dependency.Type.SUGGESTS);
    }

    /**
     * Returns conflicting packages.
     *
     * @return list of conflicts
     */
    public @NotNull List<Dependency> conflicts() {
        return parseDependencyField("Conflicts", Dependency.Type.CONFLICTS);
    }

    /**
     * Returns packages this one replaces.
     *
     * @return list of replaced packages
     */
    public @NotNull List<Dependency> replaces() {
        return parseDependencyField("Replaces", Dependency.Type.OBSOLETES);
    }

    /**
     * Returns packages this one breaks.
     *
     * @return list of broken packages
     */
    public @NotNull List<Dependency> breaks() {
        return parseDependencyField("Breaks", Dependency.Type.CONFLICTS);
    }

    /**
     * Returns a raw field value.
     *
     * @param name the field name
     * @return an Optional containing the field value
     */
    public @NotNull Optional<String> getField(@NotNull String name) {
        return Optional.ofNullable(fields.get(name));
    }

    /**
     * Returns all control file fields.
     *
     * @return unmodifiable map of fields
     */
    public @NotNull Map<String, String> getAllFields() {
        return Collections.unmodifiableMap(fields);
    }

    /**
     * Returns the raw control file content as originally parsed.
     *
     * <p>This is useful for systems that need to store or process
     * the original control file text, such as OmniBOR artifact graphs.
     *
     * @return an Optional containing the raw content, or empty if not available
     */
    public @NotNull Optional<String> rawControlContent() {
        return Optional.ofNullable(rawControlContent);
    }

    private @NotNull List<Dependency> parseAllDependencies() {
        List<Dependency> allDeps = new ArrayList<>();
        allDeps.addAll(parseDependencyField("Depends", Dependency.Type.REQUIRES));
        allDeps.addAll(parseDependencyField("Pre-Depends", Dependency.Type.PRE_DEPENDS));
        return allDeps;
    }

    private @NotNull List<Dependency> parseProvides() {
        return parseDependencyField("Provides", Dependency.Type.PROVIDES);
    }

    private @NotNull List<Dependency> parseDependencyField(@NotNull String fieldName, @NotNull Dependency.Type type) {
        String value = fields.get(fieldName);
        if (value == null || value.isEmpty()) {
            return Collections.emptyList();
        }

        List<Dependency> deps = new ArrayList<>();
        // Split by comma, handling alternatives (|)
        for (String part : value.split(",")) {
            part = part.trim();
            if (part.isEmpty()) continue;

            // For alternatives, take the first one
            if (part.contains("|")) {
                part = part.split("\\|")[0].trim();
            }

            // Parse name and version constraint
            // Format: name (op version) or just name
            int parenStart = part.indexOf('(');
            if (parenStart > 0) {
                String name = part.substring(0, parenStart).trim();
                int parenEnd = part.indexOf(')', parenStart);
                if (parenEnd > parenStart) {
                    String constraint = part.substring(parenStart + 1, parenEnd).trim();
                    Dependency.Operator op = Dependency.Operator.ANY;
                    String version = constraint;

                    if (constraint.startsWith(">=")) {
                        op = Dependency.Operator.GREATER_THAN_OR_EQUAL;
                        version = constraint.substring(2).trim();
                    } else if (constraint.startsWith("<=")) {
                        op = Dependency.Operator.LESS_THAN_OR_EQUAL;
                        version = constraint.substring(2).trim();
                    } else if (constraint.startsWith(">>")) {
                        op = Dependency.Operator.GREATER_THAN;
                        version = constraint.substring(2).trim();
                    } else if (constraint.startsWith("<<")) {
                        op = Dependency.Operator.LESS_THAN;
                        version = constraint.substring(2).trim();
                    } else if (constraint.startsWith("=")) {
                        op = Dependency.Operator.EQUAL;
                        version = constraint.substring(1).trim();
                    }

                    deps.add(Dependency.of(type, name, op, version));
                } else {
                    deps.add(Dependency.of(type, part.substring(0, parenStart).trim()));
                }
            } else {
                deps.add(Dependency.of(type, part));
            }
        }
        return deps;
    }
}
