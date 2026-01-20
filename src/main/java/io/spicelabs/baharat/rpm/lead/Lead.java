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
package io.spicelabs.baharat.rpm.lead;

import org.jetbrains.annotations.NotNull;

/**
 * Represents the lead section of an RPM file (first 96 bytes).
 * The lead is a legacy header that is largely obsolete but still present
 * for backwards compatibility. Most metadata should be read from the
 * main header instead.
 *
 * @param majorVersion the RPM format major version (typically 3 or 4)
 * @param minorVersion the RPM format minor version
 * @param type the package type (0 = binary, 1 = source)
 * @param architecture the architecture number
 * @param name the package name (from lead, may differ from header)
 * @param osNumber the OS number
 * @param signatureType the signature type
 */
public record Lead(
        int majorVersion,
        int minorVersion,
        int type,
        int architecture,
        @NotNull String name,
        int osNumber,
        int signatureType
) {
    /**
     * The size of the lead section in bytes.
     */
    public static final int SIZE = 96;

    /**
     * The magic number that identifies an RPM file.
     */
    public static final int MAGIC = 0xEDABEEDB;

    /**
     * Package type constant for binary packages.
     */
    public static final int TYPE_BINARY = 0;

    /**
     * Package type constant for source packages.
     */
    public static final int TYPE_SOURCE = 1;

    /**
     * Returns true if this is a binary package.
     *
     * @return true if binary package, false if source package
     */
    public boolean isBinary() {
        return type == TYPE_BINARY;
    }

    /**
     * Returns true if this is a source package.
     *
     * @return true if source package, false if binary package
     */
    public boolean isSource() {
        return type == TYPE_SOURCE;
    }

    /**
     * Returns the RPM format version as a string (e.g., "3.0" or "4.0").
     *
     * @return the version string
     */
    public @NotNull String version() {
        return majorVersion + "." + minorVersion;
    }
}
