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
package io.spicelabs.baharat.rpm.database;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Optional;

/**
 * Represents an installed RPM package from the RPM database.
 *
 * <p>This record contains the core metadata for a package that has been
 * installed on a system. The information is read from the RPM database
 * (typically at /var/lib/rpm/rpmdb.sqlite on modern systems).
 *
 * @param name the package name
 * @param epoch the package epoch (used for version comparison)
 * @param version the package version
 * @param release the package release
 * @param arch the package architecture
 * @param installTime when the package was installed
 * @param size the installed size in bytes
 * @param summary a brief description of the package
 * @param vendor the package vendor
 * @param packager who built/packaged this package
 */
public record InstalledPackage(
        @NotNull String name,
        @NotNull Optional<Integer> epoch,
        @NotNull String version,
        @NotNull String release,
        @NotNull String arch,
        @NotNull Instant installTime,
        long size,
        @NotNull Optional<String> summary,
        @NotNull Optional<String> vendor,
        @NotNull Optional<String> packager
) {

    /**
     * Returns the NEVRA (Name-Epoch:Version-Release.Arch) string.
     *
     * @return the full NEVRA string
     */
    public @NotNull String nevra() {
        if (epoch.isPresent() && epoch.get() != 0) {
            return String.format("%s-%d:%s-%s.%s", name, epoch.get(), version, release, arch);
        }
        return String.format("%s-%s-%s.%s", name, version, release, arch);
    }

    /**
     * Returns the NVR (Name-Version-Release) string.
     *
     * @return the NVR string
     */
    public @NotNull String nvr() {
        return String.format("%s-%s-%s", name, version, release);
    }

    /**
     * Returns the NEVR (Name-Epoch:Version-Release) string.
     *
     * @return the NEVR string
     */
    public @NotNull String nevr() {
        if (epoch.isPresent() && epoch.get() != 0) {
            return String.format("%s-%d:%s-%s", name, epoch.get(), version, release);
        }
        return String.format("%s-%s-%s", name, version, release);
    }

    @Override
    public String toString() {
        return nevra();
    }
}
