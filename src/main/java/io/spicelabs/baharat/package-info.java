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
 * Baharat - A Java library for reading Linux and BSD package files.
 *
 * <p>This library provides a unified API for reading package files from six major formats:
 * <ul>
 *   <li><strong>RPM</strong> - Red Hat Package Manager (Fedora, RHEL, CentOS, openSUSE)</li>
 *   <li><strong>DEB</strong> - Debian packages (Debian, Ubuntu, Linux Mint)</li>
 *   <li><strong>Pacman</strong> - Arch Linux Package Manager (Arch, Manjaro)</li>
 *   <li><strong>APK</strong> - Alpine Package Keeper (Alpine Linux)</li>
 *   <li><strong>FreeBSD pkg</strong> - FreeBSD package format</li>
 *   <li><strong>OpenBSD pkg</strong> - OpenBSD package format</li>
 * </ul>
 *
 * <h2>Quick Start</h2>
 * <pre>{@code
 * // Auto-detect format and read any package
 * Package pkg = PackageReader.read(Path.of("package.rpm"));
 * System.out.println("Name: " + pkg.name());
 * System.out.println("Version: " + pkg.version());
 *
 * // Generate Package URL
 * Purl purl = pkg.purl();
 * System.out.println("PURL: " + purl.toCanonical());
 *
 * // Stream payload without extracting to disk
 * try (Stream<PackageEntry> entries = PackageReader.streamPayload(path)) {
 *     entries.forEach(entry -> System.out.println(entry.path()));
 * }
 * }</pre>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link io.spicelabs.baharat.PackageReader} - Main entry point for reading packages</li>
 *   <li>{@link io.spicelabs.baharat.Package} - Common interface for all package types</li>
 *   <li>{@link io.spicelabs.baharat.PackageMetadata} - Common metadata interface</li>
 *   <li>{@link io.spicelabs.baharat.PackageFormat} - Format enumeration and detection</li>
 *   <li>{@link io.spicelabs.baharat.PackageEntry} - Payload entry types (files, directories, symlinks)</li>
 * </ul>
 *
 * <h2>Format-Specific Packages</h2>
 * <ul>
 *   <li>{@link io.spicelabs.baharat.rpm} - RPM format support</li>
 *   <li>{@link io.spicelabs.baharat.deb} - DEB format support</li>
 *   <li>{@link io.spicelabs.baharat.pacman} - Pacman format support</li>
 *   <li>{@link io.spicelabs.baharat.apk} - APK format support</li>
 *   <li>{@link io.spicelabs.baharat.freebsd} - FreeBSD format support</li>
 *   <li>{@link io.spicelabs.baharat.openbsd} - OpenBSD format support</li>
 * </ul>
 *
 * @see io.spicelabs.baharat.PackageReader
 * @see io.spicelabs.baharat.Package
 */
package io.spicelabs.baharat;
