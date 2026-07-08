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
package io.spicelabs.baharat;

import io.spicelabs.baharat.apk.ApkPackage;
import io.spicelabs.baharat.deb.DebPackage;
import io.spicelabs.baharat.freebsd.FreeBsdPackage;
import io.spicelabs.baharat.openbsd.OpenBsdPackage;
import io.spicelabs.baharat.pacman.PacmanPackage;
import io.spicelabs.baharat.rpm.RpmPackage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for namespace inference utilities across all package formats.
 */
class NamespaceInferenceTest {

    // DEB namespace tests

    @Test
    void debInfersUbuntuNamespace() {
        assertThat(DebPackage.inferNamespace("/path/to/ubuntu/packages/foo.deb"))
                .contains("ubuntu");
        assertThat(DebPackage.inferNamespace("foo_1.0-1ubuntu1_amd64.deb"))
                .contains("ubuntu");
    }

    @Test
    void debInfersDebianNamespace() {
        assertThat(DebPackage.inferNamespace("/path/to/debian/packages/foo.deb"))
                .contains("debian");
        assertThat(DebPackage.inferNamespace("foo_1.0-1+deb12_amd64.deb"))
                .contains("debian");
    }

    @Test
    void debInfersLinuxMintNamespace() {
        assertThat(DebPackage.inferNamespace("/mint/packages/foo.deb"))
                .contains("linuxmint");
    }

    @Test
    void debInfersRaspbianNamespace() {
        assertThat(DebPackage.inferNamespace("/raspbian/pool/foo.deb"))
                .contains("raspbian");
    }

    @Test
    void debReturnsEmptyForUnknown() {
        assertThat(DebPackage.inferNamespace("/generic/path/foo.deb")).isEmpty();
    }

    // APK namespace tests

    @Test
    void apkDefaultsToAlpine() {
        assertThat(ApkPackage.inferNamespace("/path/to/package.apk"))
                .contains("alpine");
    }

    @Test
    void apkInfersPostmarketOs() {
        assertThat(ApkPackage.inferNamespace("/postmarket/repo/foo.apk"))
                .contains("postmarketos");
    }

    // Pacman namespace tests

    @Test
    void pacmanDefaultsToArch() {
        assertThat(PacmanPackage.inferNamespace("/path/to/package.pkg.tar.zst"))
                .contains("arch");
    }

    @Test
    void pacmanInfersManjaro() {
        assertThat(PacmanPackage.inferNamespace("/manjaro/repo/foo.pkg.tar.zst"))
                .contains("manjaro");
    }

    @Test
    void pacmanInfersEndeavourOs() {
        assertThat(PacmanPackage.inferNamespace("/endeavouros/repo/foo.pkg.tar.zst"))
                .contains("endeavouros");
    }

    @Test
    void pacmanInfersArtix() {
        assertThat(PacmanPackage.inferNamespace("/artix/pool/foo.pkg.tar.zst"))
                .contains("artix");
    }

    // OpenBSD namespace tests

    @Test
    void openBsdAlwaysReturnsOpenBsd() {
        assertThat(OpenBsdPackage.inferNamespace("/any/path/foo.tgz"))
                .contains("openbsd");
        assertThat(OpenBsdPackage.inferNamespace("foo-1.0.tgz"))
                .contains("openbsd");
    }

    // FreeBSD namespace tests

    @Test
    void freeBsdDefaultsToFreeBsd() {
        assertThat(FreeBsdPackage.inferNamespace("/path/to/package.pkg"))
                .contains("freebsd");
    }

    @Test
    void freeBsdInfersDragonFlyBsd() {
        assertThat(FreeBsdPackage.inferNamespace("/dragonfly/repo/foo.pkg"))
                .contains("dragonflybsd");
    }

    // RPM namespace tests

    @Test
    void rpmInfersFedoraFromReleaseTag() {
        assertThat(RpmPackage.inferNamespace("1.fc40", "", ""))
                .contains("fedora");
        assertThat(RpmPackage.inferNamespace("3.fc18", "Fedora Project", ""))
                .contains("fedora");
    }

    @Test
    void rpmInfersCentosFromElTagAndDistribution() {
        assertThat(RpmPackage.inferNamespace("16.el8", "CentOS", ""))
                .contains("centos");
    }

    @Test
    void rpmInfersRhelFromElTagWithoutDistribution() {
        assertThat(RpmPackage.inferNamespace("20.el9", "", ""))
                .contains("rhel");
    }

    @Test
    void rpmInfersOpensuseFromReleaseTag() {
        assertThat(RpmPackage.inferNamespace("150400.3.1.suse", "", ""))
                .contains("opensuse");
        assertThat(RpmPackage.inferNamespace("1.2.opensuse", "", ""))
                .contains("opensuse");
    }

    @Test
    void rpmInfersOpensuseFromDistributionField() {
        // SUSE packages may lack a distro tag in the release but have it in the
        // distribution field (e.g., vendor "SUSE LLC <https://www.suse.com/>")
        assertThat(RpmPackage.inferNamespace("160099.8.2", "openSUSE Tumbleweed", ""))
                .contains("opensuse");
        assertThat(RpmPackage.inferNamespace("160099.8.2", "SUSE Linux Enterprise", ""))
                .contains("opensuse");
    }

    @Test
    void rpmInfersAmazonFromReleaseTag() {
        assertThat(RpmPackage.inferNamespace("1.amzn2023", "", ""))
                .contains("amazon");
    }

    @Test
    void rpmInfersOracleLinuxFromReleaseTag() {
        assertThat(RpmPackage.inferNamespace("1.0.el9", "Oracle Linux", ""))
                .contains("oraclelinux");
        assertThat(RpmPackage.inferNamespace("1.el9", "Oracle Linux", ""))
                .contains("oraclelinux");
    }

    @Test
    void rpmInfersAlmaFromReleaseTag() {
        assertThat(RpmPackage.inferNamespace("1.el9", "AlmaLinux", ""))
                .contains("alma");
        assertThat(RpmPackage.inferNamespace("1.al9", "", ""))
                .contains("alma");
    }

    @Test
    void rpmInfersRockyFromReleaseTag() {
        assertThat(RpmPackage.inferNamespace("1.el9", "Rocky Linux", ""))
                .contains("rocky");
        assertThat(RpmPackage.inferNamespace("1.rocky", "", ""))
                .contains("rocky");
    }

    @Test
    void rpmInfersFromSourcePath() {
        assertThat(RpmPackage.inferNamespace("", "", "/repo/fedora/foo.rpm"))
                .contains("fedora");
        assertThat(RpmPackage.inferNamespace("", "", "/repo/centos/foo.rpm"))
                .contains("centos");
        assertThat(RpmPackage.inferNamespace("", "", "/repo/opensuse/foo.rpm"))
                .contains("opensuse");
    }

    @Test
    void rpmReturnsEmptyForUnknown() {
        assertThat(RpmPackage.inferNamespace("1", "", ""))
                .isEmpty();
        assertThat(RpmPackage.inferNamespace("", "", "/generic/path/foo.rpm"))
                .isEmpty();
    }

    @Test
    void rpmFilenameOnlyInferNamespace() {
        assertThat(RpmPackage.inferNamespace("sed-4.9-1.fc40.x86_64.rpm"))
                .contains("fedora");
        assertThat(RpmPackage.inferNamespace("which-2.21-16.el8.x86_64.rpm"))
                .contains("rhel");
        assertThat(RpmPackage.inferNamespace("/repo/centos/which-2.21-16.el8.x86_64.rpm"))
                .contains("centos");
    }
}
