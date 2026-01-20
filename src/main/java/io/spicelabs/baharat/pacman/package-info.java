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
 * Pacman (Arch Linux Package Manager) format support.
 *
 * <p>This package provides support for reading Pacman package files (.pkg.tar.*),
 * used by Arch Linux, Manjaro, EndeavourOS, and other Arch-based distributions.
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link io.spicelabs.baharat.pacman.PacmanReader} - Main entry point for reading Pacman packages</li>
 *   <li>{@link io.spicelabs.baharat.pacman.PacmanPackage} - Pacman package representation</li>
 *   <li>{@link io.spicelabs.baharat.pacman.PacmanMetadata} - Pacman-specific metadata</li>
 * </ul>
 *
 * <h2>Package Structure</h2>
 * <p>Pacman packages are compressed tar archives containing:
 * <ul>
 *   <li>{@code .PKGINFO} - Package metadata in key=value format</li>
 *   <li>{@code .BUILDINFO} - Build information (optional)</li>
 *   <li>{@code .MTREE} - File integrity information</li>
 *   <li>Payload files - The actual package contents</li>
 * </ul>
 *
 * <h2>Supported Compressions</h2>
 * <ul>
 *   <li>gzip (.pkg.tar.gz)</li>
 *   <li>xz (.pkg.tar.xz) - Most common</li>
 *   <li>zstd (.pkg.tar.zst) - Default since 2020</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Read a Pacman package
 * PacmanPackage pkg = PacmanReader.read(Path.of("package.pkg.tar.zst"));
 *
 * // Access metadata
 * System.out.println("Name: " + pkg.name());
 * System.out.println("Version: " + pkg.version());
 * System.out.println("Packager: " + pkg.pacmanMetadata().packager().orElse("unknown"));
 *
 * // Stream payload entries
 * try (Stream<PackageEntry> entries = pkg.payload()) {
 *     entries.forEach(e -> System.out.println(e.path()));
 * }
 * }</pre>
 *
 * @see io.spicelabs.baharat.pacman.PacmanReader
 * @see io.spicelabs.baharat.pacman.PacmanPackage
 * @see io.spicelabs.baharat.pacman.PacmanMetadata
 */
package io.spicelabs.baharat.pacman;
