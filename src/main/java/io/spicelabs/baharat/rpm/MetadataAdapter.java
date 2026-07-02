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
package io.spicelabs.baharat.rpm;

import io.spicelabs.baharat.common.Dependency;
import io.spicelabs.baharat.common.FileInfo;
import io.spicelabs.baharat.common.PurlHelper;
import io.spicelabs.coordinates.Purl;
import io.spicelabs.baharat.rpm.metadata.PackageMetadata;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Adapter that wraps RPM PackageMetadata to implement the common PackageMetadata interface.
 */
final class MetadataAdapter implements io.spicelabs.baharat.PackageMetadata {

    private final @NotNull PackageMetadata rpm;

    MetadataAdapter(@NotNull PackageMetadata rpm) {
        this.rpm = rpm;
    }

    @Override
    public @NotNull String name() {
        return rpm.name();
    }

    @Override
    public @NotNull String version() {
        return rpm.version();
    }

    @Override
    public @NotNull Optional<String> release() {
        String rel = rpm.release();
        return rel.isEmpty() ? Optional.empty() : Optional.of(rel);
    }

    @Override
    public @NotNull Optional<Integer> epoch() {
        return rpm.epoch();
    }

    @Override
    public @NotNull String arch() {
        return rpm.arch();
    }

    @Override
    public @NotNull Optional<String> summary() {
        String sum = rpm.summary();
        return sum.isEmpty() ? Optional.empty() : Optional.of(sum);
    }

    @Override
    public @NotNull Optional<String> description() {
        String desc = rpm.description();
        return desc.isEmpty() ? Optional.empty() : Optional.of(desc);
    }

    @Override
    public @NotNull Optional<String> maintainer() {
        return rpm.packager();
    }

    @Override
    public @NotNull Optional<String> url() {
        return rpm.url();
    }

    @Override
    public @NotNull Optional<String> license() {
        String lic = rpm.license();
        return lic.isEmpty() ? Optional.empty() : Optional.of(lic);
    }

    @Override
    public long installedSize() {
        return rpm.size();
    }

    @Override
    public @NotNull Optional<Instant> buildTime() {
        return rpm.buildTime();
    }

    @Override
    public @NotNull List<Dependency> dependencies() {
        return rpm.requires().stream()
                .map(this::convertDependency)
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public @NotNull List<Dependency> provides() {
        return rpm.provides().stream()
                .map(this::convertDependency)
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public @NotNull List<FileInfo> files() {
        return rpm.files().stream()
                .map(this::convertFileInfo)
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public @NotNull Optional<String> vendor() {
        return rpm.vendor();
    }

    @Override
    public @NotNull Optional<String> group() {
        String grp = rpm.group();
        return grp.isEmpty() ? Optional.empty() : Optional.of(grp);
    }

    @Override
    public @NotNull Purl purl() {
        String namespace = vendor().map(PurlHelper::normalizeNamespace).filter(s -> !s.isEmpty()).orElse("unknown");

        String ver = version();
        String rel = rpm.release();
        String version = rel.isEmpty() ? ver : (ver + "-" + rel);

        var qualifiers = PurlHelper.newQualifiers();
        if (!PurlHelper.isArchitectureIndependent(arch())) {
            qualifiers.put("arch", arch());
        }
        epoch().filter(e -> e != 0).ifPresent(e -> qualifiers.put("epoch", String.valueOf(e)));

        return PurlHelper.build("rpm", namespace, name(), version.isEmpty() ? null : version, qualifiers);
    }

    private @NotNull Dependency convertDependency(@NotNull io.spicelabs.baharat.rpm.metadata.Dependency rpmDep) {
        Dependency.Type type = switch (rpmDep.type()) {
            case REQUIRES -> Dependency.Type.REQUIRES;
            case PROVIDES -> Dependency.Type.PROVIDES;
            case CONFLICTS -> Dependency.Type.CONFLICTS;
            case OBSOLETES -> Dependency.Type.OBSOLETES;
            case RECOMMENDS -> Dependency.Type.RECOMMENDS;
            case SUGGESTS -> Dependency.Type.SUGGESTS;
            case SUPPLEMENTS -> Dependency.Type.SUPPLEMENTS;
            case ENHANCES -> Dependency.Type.ENHANCES;
            case ORDER -> Dependency.Type.REQUIRES; // ORDER is treated as a requires dependency
        };

        Dependency.Operator op = Dependency.Operator.ANY;
        if (rpmDep.isLessThan() && rpmDep.isEqual()) {
            op = Dependency.Operator.LESS_THAN_OR_EQUAL;
        } else if (rpmDep.isGreaterThan() && rpmDep.isEqual()) {
            op = Dependency.Operator.GREATER_THAN_OR_EQUAL;
        } else if (rpmDep.isLessThan()) {
            op = Dependency.Operator.LESS_THAN;
        } else if (rpmDep.isGreaterThan()) {
            op = Dependency.Operator.GREATER_THAN;
        } else if (rpmDep.isEqual()) {
            op = Dependency.Operator.EQUAL;
        }

        return new Dependency(type, rpmDep.name(), rpmDep.version(), op);
    }

    private @NotNull FileInfo convertFileInfo(@NotNull io.spicelabs.baharat.rpm.metadata.FileInfo rpmFile) {
        int flags = 0;
        if (rpmFile.isConfig()) {
            flags |= FileInfo.FLAG_CONFIG;
        }
        if (rpmFile.isDoc()) {
            flags |= FileInfo.FLAG_DOC;
        }
        if (rpmFile.isLicense()) {
            flags |= FileInfo.FLAG_LICENSE;
        }
        if (rpmFile.isGhost()) {
            flags |= FileInfo.FLAG_GHOST;
        }
        if (rpmFile.isNoReplace()) {
            flags |= FileInfo.FLAG_NOREPLACE;
        }

        return new FileInfo(
                rpmFile.path(),
                rpmFile.size(),
                rpmFile.mode(),
                rpmFile.mtime(),
                rpmFile.userName(),
                rpmFile.groupName(),
                rpmFile.digest(),
                rpmFile.linkTo(),
                flags
        );
    }
}
