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

import io.spicelabs.baharat.rpm.header.Header;
import io.spicelabs.baharat.rpm.header.HeaderTag;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Provides convenient access to package metadata extracted from the RPM header.
 *
 * <p>This class wraps the raw {@link Header} and provides typed accessors for commonly
 * used package information including:
 * <ul>
 *   <li>Basic info: name, version, release, epoch, architecture (NEVRA)</li>
 *   <li>Description: summary, description, license, URL, vendor</li>
 *   <li>Build info: build time, build host, source RPM</li>
 *   <li>Dependencies: requires, provides, conflicts, obsoletes, and weak dependencies</li>
 *   <li>File list: paths, sizes, modes, ownership, digests</li>
 *   <li>Scripts: pre/post install/uninstall/transaction scripts</li>
 *   <li>Changelog: timestamped change entries</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * RpmPackage rpm = RpmReader.read(path);
 * PackageMetadata meta = rpm.metadata();
 *
 * System.out.println("Package: " + meta.nevra());
 * System.out.println("License: " + meta.license());
 *
 * for (Dependency dep : meta.requires()) {
 *     System.out.println("Requires: " + dep.toVersionedString());
 * }
 * }</pre>
 *
 * @see Header
 * @see Dependency
 * @see FileInfo
 */
public final class PackageMetadata {

    private final @NotNull Header header;

    /**
     * Creates a new PackageMetadata wrapper for the given header.
     *
     * @param header the RPM header
     */
    public PackageMetadata(@NotNull Header header) {
        this.header = header;
    }

    /**
     * Returns the underlying header.
     *
     * @return the header
     */
    public @NotNull Header header() {
        return header;
    }

    // Basic package information

    /**
     * Returns the package name.
     *
     * @return the name
     */
    public @NotNull String name() {
        return header.getString(HeaderTag.NAME.tag()).orElse("");
    }

    /**
     * Returns the package version.
     *
     * @return the version
     */
    public @NotNull String version() {
        return header.getString(HeaderTag.VERSION.tag()).orElse("");
    }

    /**
     * Returns the package release.
     *
     * @return the release
     */
    public @NotNull String release() {
        return header.getString(HeaderTag.RELEASE.tag()).orElse("");
    }

    /**
     * Returns the package epoch.
     *
     * @return an Optional containing the epoch, or empty if not set
     */
    public @NotNull Optional<Integer> epoch() {
        return header.getInt(HeaderTag.EPOCH.tag());
    }

    /**
     * Returns the package architecture.
     *
     * @return the architecture (e.g., "x86_64", "noarch")
     */
    public @NotNull String arch() {
        return header.getString(HeaderTag.ARCH.tag()).orElse("");
    }

    /**
     * Returns the full NEVRA string (Name-Epoch:Version-Release.Arch).
     *
     * @return the NEVRA string
     */
    public @NotNull String nevra() {
        StringBuilder sb = new StringBuilder();
        sb.append(name());
        epoch().ifPresent(e -> sb.append("-").append(e).append(":"));
        if (epoch().isEmpty()) {
            sb.append("-");
        }
        sb.append(version());
        sb.append("-");
        sb.append(release());
        sb.append(".");
        sb.append(arch());
        return sb.toString();
    }

    /**
     * Returns the package summary.
     *
     * @return the summary
     */
    public @NotNull String summary() {
        return header.getString(HeaderTag.SUMMARY.tag()).orElse("");
    }

    /**
     * Returns the package description.
     *
     * @return the description
     */
    public @NotNull String description() {
        return header.getString(HeaderTag.DESCRIPTION.tag()).orElse("");
    }

    /**
     * Returns the package license.
     *
     * @return the license
     */
    public @NotNull String license() {
        return header.getString(HeaderTag.LICENSE.tag()).orElse("");
    }

    /**
     * Returns the package group.
     *
     * @return the group
     */
    public @NotNull String group() {
        return header.getString(HeaderTag.GROUP.tag()).orElse("");
    }

    /**
     * Returns the package URL.
     *
     * @return an Optional containing the URL, or empty if not set
     */
    public @NotNull Optional<String> url() {
        return header.getString(HeaderTag.URL.tag());
    }

    /**
     * Returns the package vendor.
     *
     * @return an Optional containing the vendor, or empty if not set
     */
    public @NotNull Optional<String> vendor() {
        return header.getString(HeaderTag.VENDOR.tag());
    }

    /**
     * Returns the package packager.
     *
     * @return an Optional containing the packager, or empty if not set
     */
    public @NotNull Optional<String> packager() {
        return header.getString(HeaderTag.PACKAGER.tag());
    }

    /**
     * Returns the distribution name.
     *
     * @return an Optional containing the distribution, or empty if not set
     */
    public @NotNull Optional<String> distribution() {
        return header.getString(HeaderTag.DISTRIBUTION.tag());
    }

    // Build information

    /**
     * Returns the build timestamp.
     *
     * @return an Optional containing the build time, or empty if not set
     */
    public @NotNull Optional<Instant> buildTime() {
        return header.getInt(HeaderTag.BUILDTIME.tag())
                .map(t -> Instant.ofEpochSecond(Integer.toUnsignedLong(t)));
    }

    /**
     * Returns the build host.
     *
     * @return an Optional containing the build host, or empty if not set
     */
    public @NotNull Optional<String> buildHost() {
        return header.getString(HeaderTag.BUILDHOST.tag());
    }

    /**
     * Returns the source RPM filename.
     *
     * @return an Optional containing the source RPM name, or empty if not set
     */
    public @NotNull Optional<String> sourceRpm() {
        return header.getString(HeaderTag.SOURCERPM.tag());
    }

    // Size information

    /**
     * Returns the installed size in bytes.
     *
     * @return the installed size
     */
    public long size() {
        // Try LONGSIZE first (for packages > 4GB), then SIZE
        return header.getLong(HeaderTag.LONGSIZE.tag())
                .orElseGet(() -> header.getLong(HeaderTag.SIZE.tag()).orElse(0L));
    }

    /**
     * Returns the archive (payload) size in bytes.
     *
     * @return the archive size
     */
    public long archiveSize() {
        return header.getLong(HeaderTag.ARCHIVESIZE.tag()).orElse(0L);
    }

    // Payload information

    /**
     * Returns the payload format (e.g., "cpio").
     *
     * @return the payload format
     */
    public @NotNull String payloadFormat() {
        return header.getString(HeaderTag.PAYLOADFORMAT.tag()).orElse("cpio");
    }

    /**
     * Returns the payload compressor (e.g., "gzip", "xz", "zstd").
     *
     * @return the payload compressor
     */
    public @NotNull String payloadCompressor() {
        return header.getString(HeaderTag.PAYLOADCOMPRESSOR.tag()).orElse("gzip");
    }

    /**
     * Returns the payload flags (compression level, etc.).
     *
     * @return an Optional containing the payload flags, or empty if not set
     */
    public @NotNull Optional<String> payloadFlags() {
        return header.getString(HeaderTag.PAYLOADFLAGS.tag());
    }

    // Dependencies

    /**
     * Returns the list of requirements.
     *
     * @return an unmodifiable list of requires dependencies
     */
    public @NotNull List<Dependency> requires() {
        return extractDependencies(
                DependencyType.REQUIRES,
                HeaderTag.REQUIRENAME.tag(),
                HeaderTag.REQUIREVERSION.tag(),
                HeaderTag.REQUIREFLAGS.tag()
        );
    }

    /**
     * Returns the list of provides.
     *
     * @return an unmodifiable list of provides dependencies
     */
    public @NotNull List<Dependency> provides() {
        return extractDependencies(
                DependencyType.PROVIDES,
                HeaderTag.PROVIDENAME.tag(),
                HeaderTag.PROVIDEVERSION.tag(),
                HeaderTag.PROVIDEFLAGS.tag()
        );
    }

    /**
     * Returns the list of conflicts.
     *
     * @return an unmodifiable list of conflicts dependencies
     */
    public @NotNull List<Dependency> conflicts() {
        return extractDependencies(
                DependencyType.CONFLICTS,
                HeaderTag.CONFLICTNAME.tag(),
                HeaderTag.CONFLICTVERSION.tag(),
                HeaderTag.CONFLICTFLAGS.tag()
        );
    }

    /**
     * Returns the list of obsoletes.
     *
     * @return an unmodifiable list of obsoletes dependencies
     */
    public @NotNull List<Dependency> obsoletes() {
        return extractDependencies(
                DependencyType.OBSOLETES,
                HeaderTag.OBSOLETENAME.tag(),
                HeaderTag.OBSOLETEVERSION.tag(),
                HeaderTag.OBSOLETEFLAGS.tag()
        );
    }

    /**
     * Returns the list of recommends (weak dependency).
     *
     * @return an unmodifiable list of recommends dependencies
     */
    public @NotNull List<Dependency> recommends() {
        return extractDependencies(
                DependencyType.RECOMMENDS,
                HeaderTag.RECOMMENDNAME.tag(),
                HeaderTag.RECOMMENDVERSION.tag(),
                HeaderTag.RECOMMENDFLAGS.tag()
        );
    }

    /**
     * Returns the list of suggests (weak dependency).
     *
     * @return an unmodifiable list of suggests dependencies
     */
    public @NotNull List<Dependency> suggests() {
        return extractDependencies(
                DependencyType.SUGGESTS,
                HeaderTag.SUGGESTNAME.tag(),
                HeaderTag.SUGGESTVERSION.tag(),
                HeaderTag.SUGGESTFLAGS.tag()
        );
    }

    /**
     * Returns the list of supplements (weak dependency).
     *
     * @return an unmodifiable list of supplements dependencies
     */
    public @NotNull List<Dependency> supplements() {
        return extractDependencies(
                DependencyType.SUPPLEMENTS,
                HeaderTag.SUPPLEMENTNAME.tag(),
                HeaderTag.SUPPLEMENTVERSION.tag(),
                HeaderTag.SUPPLEMENTFLAGS.tag()
        );
    }

    /**
     * Returns the list of enhances (weak dependency).
     *
     * @return an unmodifiable list of enhances dependencies
     */
    public @NotNull List<Dependency> enhances() {
        return extractDependencies(
                DependencyType.ENHANCES,
                HeaderTag.ENHANCENAME.tag(),
                HeaderTag.ENHANCEVERSION.tag(),
                HeaderTag.ENHANCEFLAGS.tag()
        );
    }

    private @NotNull List<Dependency> extractDependencies(@NotNull DependencyType type,
                                                   int nameTag, int versionTag, int flagsTag) {
        Optional<List<String>> names = header.getStringArray(nameTag);
        if (names.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> nameList = names.get();
        Optional<List<String>> versions = header.getStringArray(versionTag);
        Optional<int[]> flags = header.getIntArray(flagsTag);

        List<Dependency> deps = new ArrayList<>(nameList.size());
        for (int i = 0; i < nameList.size(); i++) {
            String name = nameList.get(i);
            Optional<String> version = Optional.empty();
            if (versions.isPresent() && i < versions.get().size()) {
                String v = versions.get().get(i);
                if (!v.isEmpty()) {
                    version = Optional.of(v);
                }
            }
            int flag = 0;
            if (flags.isPresent() && i < flags.get().length) {
                flag = flags.get()[i];
            }
            deps.add(new Dependency(type, name, version, flag));
        }
        return Collections.unmodifiableList(deps);
    }

    // File list

    /**
     * Returns the list of files in this package.
     *
     * @return an unmodifiable list of file information
     */
    public @NotNull List<FileInfo> files() {
        // Get file paths - try new format first, then old format
        Optional<List<String>> paths = header.getStringArray(HeaderTag.FILENAMES.tag());
        if (paths.isEmpty()) {
            paths = buildFilePathsFromComponents();
        }
        if (paths.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> pathList = paths.get();

        // Get file attributes
        Optional<long[]> sizes = header.getLongArray(HeaderTag.LONGFILESIZES.tag());
        if (sizes.isEmpty()) {
            int[] intSizes = header.getIntArray(HeaderTag.FILESIZES.tag()).orElse(new int[0]);
            long[] longSizes = new long[intSizes.length];
            for (int i = 0; i < intSizes.length; i++) {
                longSizes[i] = Integer.toUnsignedLong(intSizes[i]);
            }
            sizes = Optional.of(longSizes);
        }

        int[] modes = header.getIntArray(HeaderTag.FILEMODES.tag()).orElse(new int[0]);
        int[] mtimes = header.getIntArray(HeaderTag.FILEMTIMES.tag()).orElse(new int[0]);
        int[] flags = header.getIntArray(HeaderTag.FILEFLAGS.tag()).orElse(new int[0]);
        List<String> users = header.getStringArray(HeaderTag.FILEUSERNAME.tag()).orElse(Collections.emptyList());
        List<String> groups = header.getStringArray(HeaderTag.FILEGROUPNAME.tag()).orElse(Collections.emptyList());
        List<String> digests = header.getStringArray(HeaderTag.FILEDIGESTS.tag()).orElse(Collections.emptyList());
        List<String> linkTos = header.getStringArray(HeaderTag.FILELINKTOS.tag()).orElse(Collections.emptyList());

        List<FileInfo> files = new ArrayList<>(pathList.size());
        long[] sizeArray = sizes.get();

        for (int i = 0; i < pathList.size(); i++) {
            String path = pathList.get(i);
            long size = i < sizeArray.length ? sizeArray[i] : 0;
            int mode = i < modes.length ? modes[i] : 0;
            Instant mtime = i < mtimes.length ? Instant.ofEpochSecond(Integer.toUnsignedLong(mtimes[i])) : Instant.EPOCH;
            int flag = i < flags.length ? flags[i] : 0;
            String user = i < users.size() ? users.get(i) : "root";
            String group = i < groups.size() ? groups.get(i) : "root";
            Optional<String> digest = i < digests.size() && !digests.get(i).isEmpty()
                    ? Optional.of(digests.get(i)) : Optional.empty();
            Optional<String> linkTo = i < linkTos.size() && !linkTos.get(i).isEmpty()
                    ? Optional.of(linkTos.get(i)) : Optional.empty();

            files.add(new FileInfo(path, size, mode, mtime, flag, user, group, digest, linkTo));
        }

        return Collections.unmodifiableList(files);
    }

    /**
     * Builds full file paths from the split storage format used by newer RPMs.
     *
     * <p>Modern RPMs store file paths in a compressed format to reduce header size:
     * <ul>
     *   <li>BASENAMES: Array of file names (e.g., "bash", "profile")</li>
     *   <li>DIRNAMES: Array of unique directory paths (e.g., "/usr/bin/", "/etc/")</li>
     *   <li>DIRINDEXES: Array of indices mapping each basename to its directory</li>
     * </ul>
     *
     * <p>This method reconstructs full paths by combining DIRNAMES[DIRINDEXES[i]] + BASENAMES[i].
     */
    private @NotNull Optional<List<String>> buildFilePathsFromComponents() {
        // RPM stores file paths as basenames + dirnames + dirindexes for compression
        Optional<List<String>> basenames = header.getStringArray(HeaderTag.BASENAMES.tag());
        Optional<List<String>> dirnames = header.getStringArray(HeaderTag.DIRNAMES.tag());
        Optional<int[]> dirindexes = header.getIntArray(HeaderTag.DIRINDEXES.tag());

        if (basenames.isEmpty() || dirnames.isEmpty() || dirindexes.isEmpty()) {
            return Optional.empty();
        }

        List<String> basenameList = basenames.get();
        List<String> dirnameList = dirnames.get();
        int[] indexArray = dirindexes.get();

        if (basenameList.size() != indexArray.length) {
            return Optional.empty();
        }

        List<String> paths = new ArrayList<>(basenameList.size());
        for (int i = 0; i < basenameList.size(); i++) {
            int dirIndex = indexArray[i];
            if (dirIndex >= 0 && dirIndex < dirnameList.size()) {
                paths.add(dirnameList.get(dirIndex) + basenameList.get(i));
            } else {
                paths.add(basenameList.get(i));
            }
        }
        return Optional.of(paths);
    }

    // Changelog

    /**
     * Returns the changelog entries.
     *
     * @return an unmodifiable list of changelog entries
     */
    public @NotNull List<Changelog> changelog() {
        int[] times = header.getIntArray(HeaderTag.CHANGELOGTIME.tag()).orElse(new int[0]);
        List<String> names = header.getStringArray(HeaderTag.CHANGELOGNAME.tag()).orElse(Collections.emptyList());
        List<String> texts = header.getStringArray(HeaderTag.CHANGELOGTEXT.tag()).orElse(Collections.emptyList());

        int count = Math.min(times.length, Math.min(names.size(), texts.size()));
        List<Changelog> entries = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            entries.add(Changelog.fromUnixTime(
                    Integer.toUnsignedLong(times[i]),
                    names.get(i),
                    texts.get(i)
            ));
        }

        return Collections.unmodifiableList(entries);
    }

    // Scripts

    /**
     * Returns the pre-install script.
     *
     * @return an Optional containing the script, or empty if not set
     */
    public @NotNull Optional<String> preInstallScript() {
        return header.getString(HeaderTag.PREIN.tag());
    }

    /**
     * Returns the post-install script.
     *
     * @return an Optional containing the script, or empty if not set
     */
    public @NotNull Optional<String> postInstallScript() {
        return header.getString(HeaderTag.POSTIN.tag());
    }

    /**
     * Returns the pre-uninstall script.
     *
     * @return an Optional containing the script, or empty if not set
     */
    public @NotNull Optional<String> preUninstallScript() {
        return header.getString(HeaderTag.PREUN.tag());
    }

    /**
     * Returns the post-uninstall script.
     *
     * @return an Optional containing the script, or empty if not set
     */
    public @NotNull Optional<String> postUninstallScript() {
        return header.getString(HeaderTag.POSTUN.tag());
    }

    /**
     * Returns the pre-transaction script.
     *
     * @return an Optional containing the script, or empty if not set
     */
    public @NotNull Optional<String> preTransactionScript() {
        return header.getString(HeaderTag.PRETRANS.tag());
    }

    /**
     * Returns the post-transaction script.
     *
     * @return an Optional containing the script, or empty if not set
     */
    public @NotNull Optional<String> postTransactionScript() {
        return header.getString(HeaderTag.POSTTRANS.tag());
    }

    /**
     * Returns the verify script.
     *
     * @return an Optional containing the script, or empty if not set
     */
    public @NotNull Optional<String> verifyScript() {
        return header.getString(HeaderTag.VERIFYSCRIPT.tag());
    }
}
