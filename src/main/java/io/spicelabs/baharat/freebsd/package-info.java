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
 * FreeBSD pkg format support.
 *
 * <p>This package provides support for reading FreeBSD package files (.pkg, .txz),
 * used by FreeBSD 10 and later versions.
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link io.spicelabs.baharat.freebsd.FreeBsdReader} - Main entry point for reading FreeBSD packages</li>
 *   <li>{@link io.spicelabs.baharat.freebsd.FreeBsdPackage} - FreeBSD package representation</li>
 *   <li>{@link io.spicelabs.baharat.freebsd.FreeBsdMetadata} - FreeBSD-specific metadata</li>
 * </ul>
 *
 * <h2>Package Structure</h2>
 * <p>FreeBSD packages are compressed tar archives containing:
 * <ul>
 *   <li>{@code +COMPACT_MANIFEST} - JSON metadata file</li>
 *   <li>{@code +MANIFEST} - Full JSON manifest (optional)</li>
 *   <li>Payload files - The actual package contents</li>
 * </ul>
 *
 * <h2>Supported Compressions</h2>
 * <ul>
 *   <li>xz (.txz, .pkg) - Default format</li>
 *   <li>zstd (.tzst) - Modern format</li>
 *   <li>gzip (.tgz) - Legacy support</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Read a FreeBSD package
 * FreeBsdPackage pkg = FreeBsdReader.read(Path.of("package.pkg"));
 *
 * // Access metadata
 * System.out.println("Name: " + pkg.name());
 * System.out.println("Version: " + pkg.version());
 * System.out.println("Comment: " + pkg.freeBsdMetadata().comment().orElse(""));
 * System.out.println("WWW: " + pkg.freeBsdMetadata().www().orElse(""));
 *
 * // Stream payload entries
 * try (Stream<PackageEntry> entries = pkg.payload()) {
 *     entries.forEach(e -> System.out.println(e.path()));
 * }
 * }</pre>
 *
 * @see io.spicelabs.baharat.freebsd.FreeBsdReader
 * @see io.spicelabs.baharat.freebsd.FreeBsdPackage
 * @see io.spicelabs.baharat.freebsd.FreeBsdMetadata
 */
package io.spicelabs.baharat.freebsd;
