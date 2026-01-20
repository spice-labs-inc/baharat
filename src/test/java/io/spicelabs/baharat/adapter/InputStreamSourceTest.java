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
package io.spicelabs.baharat.adapter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for InputStreamSource implementations.
 */
class InputStreamSourceTest {

    @TempDir
    Path tempDir;

    @Test
    void pathInputStreamSourceReturnsCorrectPath() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "test content");

        PathInputStreamSource source = new PathInputStreamSource(file);
        assertThat(source.path()).isEqualTo(file.toString());
        assertThat(source.getPath()).isEqualTo(file);
    }

    @Test
    void pathInputStreamSourceReturnsCorrectSize() throws IOException {
        Path file = tempDir.resolve("test.txt");
        String content = "test content here";
        Files.writeString(file, content);

        PathInputStreamSource source = new PathInputStreamSource(file);
        assertThat(source.size()).isEqualTo(content.getBytes(StandardCharsets.UTF_8).length);
    }

    @Test
    void pathInputStreamSourceOpensStream() throws IOException {
        Path file = tempDir.resolve("test.txt");
        String content = "hello world";
        Files.writeString(file, content);

        PathInputStreamSource source = new PathInputStreamSource(file);
        try (InputStream in = source.openStream()) {
            byte[] bytes = in.readAllBytes();
            assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo(content);
        }
    }

    @Test
    void pathInputStreamSourceCanBeOpenedMultipleTimes() throws IOException {
        Path file = tempDir.resolve("test.txt");
        String content = "multiple opens test";
        Files.writeString(file, content);

        PathInputStreamSource source = new PathInputStreamSource(file);

        // First open
        try (InputStream in1 = source.openStream()) {
            assertThat(new String(in1.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(content);
        }

        // Second open should work too
        try (InputStream in2 = source.openStream()) {
            assertThat(new String(in2.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(content);
        }
    }

    @Test
    void customInputStreamSourceWorks() {
        byte[] data = "custom data".getBytes(StandardCharsets.UTF_8);

        InputStreamSource source = new InputStreamSource() {
            @Override
            public String path() {
                return "memory://test";
            }

            @Override
            public long size() {
                return data.length;
            }

            @Override
            public InputStream openStream() {
                return new ByteArrayInputStream(data);
            }
        };

        assertThat(source.path()).isEqualTo("memory://test");
        assertThat(source.size()).isEqualTo(data.length);
    }
}
