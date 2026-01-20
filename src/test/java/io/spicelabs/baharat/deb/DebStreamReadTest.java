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
 * Tests for InputStream-based DEB reading.
 */
class DebStreamReadTest {

    static boolean hasDebFiles() {
        return PackageTestFiles.hasFiles(PackageTestFiles.DEBS);
    }

    @Test
    @EnabledIf("hasDebFiles")
    void readFromInputStream() throws Exception {
        List<Path> debFiles = PackageTestFiles.getFiles(PackageTestFiles.DEBS, ".deb", 1);
        assertThat(debFiles).isNotEmpty();

        Path path = debFiles.get(0);
        try (InputStream in = new BufferedInputStream(Files.newInputStream(path))) {
            DebPackage pkg = DebReader.read(in, path.toString());

            assertThat(pkg).isNotNull();
            assertThat(pkg.metadata().name()).isNotEmpty();
            assertThat(pkg.metadata().version()).isNotEmpty();
        }
    }

    @Test
    @EnabledIf("hasDebFiles")
    void readFromInputStreamSource() throws Exception {
        List<Path> debFiles = PackageTestFiles.getFiles(PackageTestFiles.DEBS, ".deb", 1);
        assertThat(debFiles).isNotEmpty();

        Path path = debFiles.get(0);
        PathInputStreamSource source = new PathInputStreamSource(path);

        DebPackage pkg = DebReader.read(source);

        assertThat(pkg).isNotNull();
        assertThat(pkg.metadata().name()).isNotEmpty();
        assertThat(pkg.metadata().version()).isNotEmpty();
    }

    @Test
    @EnabledIf("hasDebFiles")
    void streamPayloadFromInputStream() throws Exception {
        List<Path> debFiles = PackageTestFiles.getFiles(PackageTestFiles.DEBS, ".deb", 1);
        assertThat(debFiles).isNotEmpty();

        Path path = debFiles.get(0);
        try (InputStream in = new BufferedInputStream(Files.newInputStream(path));
             Stream<PackageEntry> entries = DebReader.streamPayload(in, path.toString())) {
            long count = entries.count();
            assertThat(count).isGreaterThan(0);
        }
    }

    @Test
    @EnabledIf("hasDebFiles")
    void streamPayloadFromInputStreamSource() throws Exception {
        List<Path> debFiles = PackageTestFiles.getFiles(PackageTestFiles.DEBS, ".deb", 1);
        assertThat(debFiles).isNotEmpty();

        Path path = debFiles.get(0);
        PathInputStreamSource source = new PathInputStreamSource(path);

        try (Stream<PackageEntry> entries = DebReader.streamPayload(source)) {
            long count = entries.count();
            assertThat(count).isGreaterThan(0);
        }
    }

    @Test
    @EnabledIf("hasDebFiles")
    void rawControlContentIsCapture() throws Exception {
        List<Path> debFiles = PackageTestFiles.getFiles(PackageTestFiles.DEBS, ".deb", 1);
        assertThat(debFiles).isNotEmpty();

        Path path = debFiles.get(0);
        DebPackage pkg = DebReader.read(path);

        DebMetadata metadata = pkg.debMetadata();
        assertThat(metadata.rawControlContent()).isPresent();
        String rawContent = metadata.rawControlContent().get();

        // Raw content should contain the package name field
        assertThat(rawContent).contains("Package:");
        // And should match the parsed name
        assertThat(rawContent).contains(metadata.name());
    }

    @Test
    @EnabledIf("hasDebFiles")
    void getAllFieldsReturnsControlFields() throws Exception {
        List<Path> debFiles = PackageTestFiles.getFiles(PackageTestFiles.DEBS, ".deb", 1);
        assertThat(debFiles).isNotEmpty();

        Path path = debFiles.get(0);
        DebPackage pkg = DebReader.read(path);

        var fields = pkg.debMetadata().getAllFields();
        assertThat(fields).isNotEmpty();
        assertThat(fields).containsKey("Package");
        assertThat(fields).containsKey("Version");
    }
}
