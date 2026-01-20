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

/**
 * Debian package (.deb) format support.
 *
 * <p>This package provides support for reading Debian package files,
 * used by Debian, Ubuntu, Linux Mint, Pop!_OS, and other distributions.
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link io.spicelabs.baharat.deb.DebReader} - Main entry point for reading DEB files</li>
 *   <li>{@link io.spicelabs.baharat.deb.DebPackage} - DEB package representation</li>
 *   <li>{@link io.spicelabs.baharat.deb.DebMetadata} - DEB-specific metadata</li>
 * </ul>
 *
 * <h2>DEB File Structure</h2>
 * <pre>
 * +------------------+
 * |    ar header     |  8 bytes - "!&lt;arch&gt;\n"
 * +------------------+
 * |  debian-binary   |  Version file ("2.0\n")
 * +------------------+
 * |   control.tar    |  Control files (control, md5sums, scripts)
 * +------------------+
 * |    data.tar      |  Payload files (gz, xz, or zstd compressed)
 * +------------------+
 * </pre>
 *
 * <h2>Supported Compressions</h2>
 * <ul>
 *   <li>gzip (.gz) - Original format</li>
 *   <li>xz (.xz) - Default since Debian 7</li>
 *   <li>zstd (.zst) - Supported in recent dpkg</li>
 *   <li>bzip2 (.bz2) - Legacy support</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Read a DEB file
 * DebPackage deb = DebReader.read(Path.of("package.deb"));
 *
 * // Access metadata
 * System.out.println("Name: " + deb.name());
 * System.out.println("Version: " + deb.version());
 * System.out.println("Priority: " + deb.debMetadata().priority().orElse("optional"));
 * System.out.println("Section: " + deb.debMetadata().section().orElse("misc"));
 *
 * // Stream payload entries
 * try (Stream<PackageEntry> entries = deb.payload()) {
 *     entries.forEach(e -> System.out.println(e.path()));
 * }
 * }</pre>
 *
 * @see io.spicelabs.baharat.deb.DebReader
 * @see io.spicelabs.baharat.deb.DebPackage
 * @see io.spicelabs.baharat.deb.DebMetadata
 */
package io.spicelabs.baharat.deb;
