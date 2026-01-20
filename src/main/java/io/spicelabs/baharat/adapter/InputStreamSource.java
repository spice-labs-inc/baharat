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

import java.io.IOException;
import java.io.InputStream;

/**
 * Adapter interface for integration with external artifact abstractions.
 *
 * <p>This interface allows reading packages from non-Path sources, enabling
 * integration with systems like Goat Rodeo's ArtifactWrapper that provide
 * byte stream access to artifacts.
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Wrap an ArtifactWrapper for use with package readers
 * InputStreamSource source = new InputStreamSource() {
 *     public String path() { return wrapper.path(); }
 *     public long size() { return wrapper.size(); }
 *     public InputStream openStream() throws IOException {
 *         return wrapper.asStream();
 *     }
 * };
 *
 * DebPackage pkg = DebReader.read(source);
 * }</pre>
 *
 * @see io.spicelabs.baharat.deb.DebReader
 * @see io.spicelabs.baharat.apk.ApkReader
 * @see io.spicelabs.baharat.pacman.PacmanReader
 */
public interface InputStreamSource {

    /**
     * Returns the nominal path or name of this artifact.
     *
     * <p>This is used for logging, error messages, and in some cases
     * for determining the package format or compression type.
     *
     * @return the path or filename
     */
    @NotNull String path();

    /**
     * Returns the size of the artifact in bytes.
     *
     * <p>This may be used for validation or progress reporting.
     * Return -1 if the size is unknown.
     *
     * @return the size in bytes, or -1 if unknown
     */
    long size();

    /**
     * Opens a new input stream to read the artifact content.
     *
     * <p>Each call should return a fresh stream positioned at the beginning
     * of the content. The caller is responsible for closing the stream.
     *
     * @return a new input stream
     * @throws IOException if the stream cannot be opened
     */
    @NotNull InputStream openStream() throws IOException;
}
