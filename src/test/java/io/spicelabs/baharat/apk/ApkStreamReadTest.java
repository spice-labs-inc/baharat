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
package io.spicelabs.baharat.apk;

import io.spicelabs.baharat.PackageEntry;
import io.spicelabs.baharat.adapter.PathInputStreamSource;
import io.spicelabs.baharat.testdata.PackageTestFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for InputStream-based APK reading.
 */
class ApkStreamReadTest {

    static boolean hasApkFiles() {
        return PackageTestFiles.hasFiles(PackageTestFiles.APKS);
    }

    @Test
    @EnabledIf("hasApkFiles")
    void readFromInputStream() throws Exception {
        List<Path> apkFiles = PackageTestFiles.getFiles(PackageTestFiles.APKS, ".apk", 1);
        assertThat(apkFiles).isNotEmpty();

        Path path = apkFiles.get(0);
        try (InputStream in = new BufferedInputStream(Files.newInputStream(path))) {
            ApkPackage pkg = ApkReader.read(in, path.toString());

            assertThat(pkg).isNotNull();
            assertThat(pkg.metadata().name()).isNotEmpty();
            assertThat(pkg.metadata().version()).isNotEmpty();
        }
    }

    @Test
    @EnabledIf("hasApkFiles")
    void readFromInputStreamSource() throws Exception {
        List<Path> apkFiles = PackageTestFiles.getFiles(PackageTestFiles.APKS, ".apk", 1);
        assertThat(apkFiles).isNotEmpty();

        Path path = apkFiles.get(0);
        PathInputStreamSource source = new PathInputStreamSource(path);

        ApkPackage pkg = ApkReader.read(source);

        assertThat(pkg).isNotNull();
        assertThat(pkg.metadata().name()).isNotEmpty();
        assertThat(pkg.metadata().version()).isNotEmpty();
    }

    @Test
    @EnabledIf("hasApkFiles")
    void streamPayloadFromInputStream() throws Exception {
        List<Path> apkFiles = PackageTestFiles.getFiles(PackageTestFiles.APKS, ".apk", 1);
        assertThat(apkFiles).isNotEmpty();

        Path path = apkFiles.get(0);
        try (InputStream in = new BufferedInputStream(Files.newInputStream(path));
             Stream<PackageEntry> entries = ApkReader.streamPayload(in, path.toString())) {
            long count = entries.count();
            assertThat(count).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    @EnabledIf("hasApkFiles")
    void streamPayloadFromInputStreamSource() throws Exception {
        List<Path> apkFiles = PackageTestFiles.getFiles(PackageTestFiles.APKS, ".apk", 1);
        assertThat(apkFiles).isNotEmpty();

        Path path = apkFiles.get(0);
        PathInputStreamSource source = new PathInputStreamSource(path);

        try (Stream<PackageEntry> entries = ApkReader.streamPayload(source)) {
            long count = entries.count();
            assertThat(count).isGreaterThanOrEqualTo(0);
        }
    }
}
