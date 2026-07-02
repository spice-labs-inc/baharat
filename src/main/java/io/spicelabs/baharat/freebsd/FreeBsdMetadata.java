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

import com.google.gson.JsonElement;
import io.spicelabs.baharat.common.PurlHelper;
import io.spicelabs.coordinates.Purl;
import com.google.gson.JsonObject;
import io.spicelabs.baharat.PackageMetadata;
import io.spicelabs.baharat.common.Dependency;
import io.spicelabs.baharat.common.FileInfo;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Metadata for FreeBSD packages, parsed from +MANIFEST JSON.
 */
public final class FreeBsdMetadata implements PackageMetadata {

    private final @NotNull JsonObject manifest;
    private final @NotNull List<Dependency> dependencies;
    private final @NotNull List<Dependency> provides;
    private final @NotNull List<FileInfo> files;

    /**
     * Creates metadata from parsed manifest.
     *
     * @param manifest the manifest JSON
     */
    public FreeBsdMetadata(@NotNull JsonObject manifest) {
        this.manifest = manifest;
        this.dependencies = parseDependencies();
        this.provides = parseProvides();
        this.files = parseFiles();
    }

    @Override
    public @NotNull String name() {
        return getString("name").orElse("");
    }

    @Override
    public @NotNull String version() {
        return getString("version").orElse("");
    }

    @Override
    public @NotNull String arch() {
        return getString("arch").orElse("");
    }

    @Override
    public @NotNull Optional<String> description() {
        return getString("desc").or(() -> getString("comment"));
    }

    @Override
    public @NotNull Optional<String> summary() {
        return getString("comment");
    }

    @Override
    public @NotNull Optional<String> maintainer() {
        return getString("maintainer");
    }

    @Override
    public @NotNull Optional<String> url() {
        return getString("www");
    }

    @Override
    public @NotNull Optional<String> license() {
        // Licenses can be an array
        if (manifest.has("licenses")) {
            JsonElement licenses = manifest.get("licenses");
            if (licenses.isJsonArray()) {
                List<String> licList = new ArrayList<>();
                for (JsonElement lic : licenses.getAsJsonArray()) {
                    licList.add(lic.getAsString());
                }
                return Optional.of(String.join(", ", licList));
            }
        }
        return Optional.empty();
    }

    @Override
    public long installedSize() {
        return getLong("flatsize").orElse(0L);
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
    public @NotNull Purl purl() {
        var qualifiers = PurlHelper.newQualifiers();
        if (!PurlHelper.isArchitectureIndependent(arch())) {
            qualifiers.put("arch", arch());
        }

        return PurlHelper.build("freebsd", null, name(), version().isEmpty() ? null : version(), qualifiers);
    }

    // FreeBSD-specific fields

    /**
     * Returns the package origin (e.g., "www/nginx").
     *
     * @return an Optional containing the origin
     */
    public @NotNull Optional<String> origin() {
        return getString("origin");
    }

    /**
     * Returns the package prefix (installation directory).
     *
     * @return an Optional containing the prefix
     */
    public @NotNull Optional<String> prefix() {
        return getString("prefix");
    }

    /**
     * Returns the ABI string.
     *
     * @return an Optional containing the ABI
     */
    public @NotNull Optional<String> abi() {
        return getString("abi");
    }

    /**
     * Returns the package checksum.
     *
     * @return an Optional containing the checksum
     */
    public @NotNull Optional<String> checksum() {
        return getString("sum");
    }

    /**
     * Returns the conflicts list.
     *
     * @return list of conflicts
     */
    public @NotNull List<Dependency> conflicts() {
        if (!manifest.has("conflicts")) {
            return Collections.emptyList();
        }

        List<Dependency> deps = new ArrayList<>();
        JsonObject conflicts = manifest.getAsJsonObject("conflicts");
        for (String name : conflicts.keySet()) {
            deps.add(Dependency.of(Dependency.Type.CONFLICTS, name));
        }
        return deps;
    }

    /**
     * Returns the raw manifest JSON.
     *
     * @return the manifest
     */
    public @NotNull JsonObject getManifest() {
        return manifest;
    }

    private @NotNull Optional<String> getString(@NotNull String key) {
        if (manifest.has(key) && !manifest.get(key).isJsonNull()) {
            return Optional.of(manifest.get(key).getAsString());
        }
        return Optional.empty();
    }

    private @NotNull Optional<Long> getLong(@NotNull String key) {
        if (manifest.has(key) && !manifest.get(key).isJsonNull()) {
            try {
                return Optional.of(manifest.get(key).getAsLong());
            } catch (Exception e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private @NotNull List<Dependency> parseDependencies() {
        if (!manifest.has("deps")) {
            return Collections.emptyList();
        }

        List<Dependency> deps = new ArrayList<>();
        JsonObject depsObj = manifest.getAsJsonObject("deps");
        for (Map.Entry<String, JsonElement> entry : depsObj.entrySet()) {
            String name = entry.getKey();
            JsonElement value = entry.getValue();
            if (value.isJsonObject()) {
                JsonObject depObj = value.getAsJsonObject();
                if (depObj.has("version")) {
                    String version = depObj.get("version").getAsString();
                    deps.add(Dependency.of(Dependency.Type.REQUIRES, name,
                            Dependency.Operator.GREATER_THAN_OR_EQUAL, version));
                } else {
                    deps.add(Dependency.of(Dependency.Type.REQUIRES, name));
                }
            } else {
                deps.add(Dependency.of(Dependency.Type.REQUIRES, name));
            }
        }
        return deps;
    }

    private @NotNull List<Dependency> parseProvides() {
        if (!manifest.has("provides")) {
            return Collections.emptyList();
        }

        List<Dependency> deps = new ArrayList<>();
        JsonElement providesElem = manifest.get("provides");
        if (providesElem.isJsonArray()) {
            for (JsonElement elem : providesElem.getAsJsonArray()) {
                deps.add(Dependency.of(Dependency.Type.PROVIDES, elem.getAsString()));
            }
        }
        return deps;
    }

    private @NotNull List<FileInfo> parseFiles() {
        if (!manifest.has("files")) {
            return Collections.emptyList();
        }

        List<FileInfo> fileList = new ArrayList<>();
        JsonObject filesObj = manifest.getAsJsonObject("files");
        for (Map.Entry<String, JsonElement> entry : filesObj.entrySet()) {
            String path = entry.getKey();
            String digest = entry.getValue().isJsonPrimitive() ? entry.getValue().getAsString() : null;

            fileList.add(new FileInfo(
                    path,
                    0, // Size not in manifest
                    FileInfo.S_IFREG | 0644, // Default mode
                    java.time.Instant.EPOCH,
                    "root",
                    "wheel",
                    digest != null ? Optional.of(digest) : Optional.empty(),
                    Optional.empty(),
                    0
            ));
        }
        return fileList;
    }
}
