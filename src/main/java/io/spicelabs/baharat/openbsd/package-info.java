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
 * OpenBSD pkg format support.
 *
 * <p>This package provides support for reading OpenBSD package files (.tgz),
 * used by OpenBSD.
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link io.spicelabs.baharat.openbsd.OpenBsdReader} - Main entry point for reading OpenBSD packages</li>
 *   <li>{@link io.spicelabs.baharat.openbsd.OpenBsdPackage} - OpenBSD package representation</li>
 *   <li>{@link io.spicelabs.baharat.openbsd.OpenBsdMetadata} - OpenBSD-specific metadata</li>
 * </ul>
 *
 * <h2>Package Structure</h2>
 * <p>OpenBSD packages are gzip-compressed tar archives containing:
 * <ul>
 *   <li>{@code +CONTENTS} - Package contents listing with metadata</li>
 *   <li>{@code +DESC} - Package description</li>
 *   <li>{@code +COMMENT} - Short package comment</li>
 *   <li>Payload files - The actual package contents</li>
 * </ul>
 *
 * <h2>Package Naming</h2>
 * <p>OpenBSD packages follow the naming convention:
 * <ul>
 *   <li>{@code name-version.tgz} - Basic package</li>
 *   <li>{@code name-version-flavor.tgz} - Package with flavor</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Read an OpenBSD package
 * OpenBsdPackage pkg = OpenBsdReader.read(Path.of("package.tgz"));
 *
 * // Access metadata
 * System.out.println("Name: " + pkg.name());
 * System.out.println("Version: " + pkg.version());
 * System.out.println("Comment: " + pkg.openBsdMetadata().comment().orElse(""));
 *
 * // Stream payload entries
 * try (Stream<PackageEntry> entries = pkg.payload()) {
 *     entries.forEach(e -> System.out.println(e.path()));
 * }
 * }</pre>
 *
 * @see io.spicelabs.baharat.openbsd.OpenBsdReader
 * @see io.spicelabs.baharat.openbsd.OpenBsdPackage
 * @see io.spicelabs.baharat.openbsd.OpenBsdMetadata
 */
package io.spicelabs.baharat.openbsd;
