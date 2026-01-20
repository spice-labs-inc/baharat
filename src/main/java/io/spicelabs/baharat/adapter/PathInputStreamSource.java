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

import org.jetbrains.annotations.NotNull;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * An {@link InputStreamSource} implementation backed by a filesystem path.
 *
 * <p>This is the default implementation used when reading packages from
 * local files. It provides efficient buffered access to file contents.
 *
 * @see InputStreamSource
 */
public final class PathInputStreamSource implements InputStreamSource {

    private final @NotNull Path path;
    private final long size;

    /**
     * Creates a new source from a filesystem path.
     *
     * @param path the path to the file
     * @throws IOException if the file size cannot be determined
     */
    public PathInputStreamSource(@NotNull Path path) throws IOException {
        this.path = path;
        this.size = Files.size(path);
    }

    /**
     * Creates a new source from a filesystem path without checking size.
     *
     * @param path the path to the file
     * @param size the known size, or -1 if unknown
     */
    public PathInputStreamSource(@NotNull Path path, long size) {
        this.path = path;
        this.size = size;
    }

    @Override
    public @NotNull String path() {
        return path.toString();
    }

    @Override
    public long size() {
        return size;
    }

    @Override
    public @NotNull InputStream openStream() throws IOException {
        return new BufferedInputStream(Files.newInputStream(path));
    }

    /**
     * Returns the underlying filesystem path.
     *
     * @return the path
     */
    public @NotNull Path getPath() {
        return path;
    }
}
