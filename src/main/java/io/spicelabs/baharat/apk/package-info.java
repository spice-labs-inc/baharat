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
 * Alpine Package Keeper (APK) format support.
 *
 * <p>This package provides support for reading Alpine Linux package files (.apk),
 * used by Alpine Linux, postmarketOS, and other Alpine-based distributions.
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link io.spicelabs.baharat.apk.ApkReader} - Main entry point for reading APK files</li>
 *   <li>{@link io.spicelabs.baharat.apk.ApkPackage} - APK package representation</li>
 *   <li>{@link io.spicelabs.baharat.apk.ApkMetadata} - APK-specific metadata</li>
 * </ul>
 *
 * <h2>Package Structure</h2>
 * <p>APK packages are gzip-compressed tar archives containing:
 * <ul>
 *   <li>{@code .PKGINFO} - Package metadata in key=value format</li>
 *   <li>{@code .SIGN.*} - Signature files</li>
 *   <li>Payload files - The actual package contents</li>
 * </ul>
 *
 * <h2>Version Format</h2>
 * <p>APK versions typically include a release suffix:
 * <ul>
 *   <li>{@code 1.0-r0} - Version 1.0, release 0</li>
 *   <li>{@code 2.3.4-r1} - Version 2.3.4, release 1</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Read an APK file
 * ApkPackage apk = ApkReader.read(Path.of("package.apk"));
 *
 * // Access metadata
 * System.out.println("Name: " + apk.name());
 * System.out.println("Version: " + apk.version());  // e.g., "1.0-r1"
 * System.out.println("Origin: " + apk.apkMetadata().origin().orElse("unknown"));
 *
 * // Stream payload entries
 * try (Stream<PackageEntry> entries = apk.payload()) {
 *     entries.forEach(e -> System.out.println(e.path()));
 * }
 * }</pre>
 *
 * @see io.spicelabs.baharat.apk.ApkReader
 * @see io.spicelabs.baharat.apk.ApkPackage
 * @see io.spicelabs.baharat.apk.ApkMetadata
 */
package io.spicelabs.baharat.apk;
