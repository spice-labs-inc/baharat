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
 * Common types shared across all package formats.
 *
 * <p>This package provides format-agnostic representations of common package
 * concepts that are shared across RPM, DEB, Pacman, APK, FreeBSD, and OpenBSD formats.
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link io.spicelabs.baharat.common.Dependency} - Package dependency representation</li>
 *   <li>{@link io.spicelabs.baharat.common.FileInfo} - File metadata (path, size, mode, checksum)</li>
 *   <li>{@link io.spicelabs.baharat.common.SecurityUtils} - Security utilities for validation</li>
 * </ul>
 *
 * <h2>Dependency Types</h2>
 * <p>The {@link io.spicelabs.baharat.common.Dependency} class provides a unified
 * model for dependencies across all formats:
 * <table>
 *   <caption>Dependency type mapping across formats</caption>
 *   <tr><th>Concept</th><th>RPM</th><th>DEB</th><th>Pacman</th><th>APK</th><th>FreeBSD</th></tr>
 *   <tr><td>Requires</td><td>Requires:</td><td>Depends:</td><td>depend</td><td>depend</td><td>deps</td></tr>
 *   <tr><td>Provides</td><td>Provides:</td><td>Provides:</td><td>provides</td><td>provides</td><td>provides</td></tr>
 *   <tr><td>Conflicts</td><td>Conflicts:</td><td>Conflicts:</td><td>conflict</td><td>-</td><td>conflicts</td></tr>
 *   <tr><td>Replaces</td><td>Obsoletes:</td><td>Replaces:</td><td>replaces</td><td>replaces</td><td>-</td></tr>
 * </table>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Access dependencies from any package
 * Package pkg = PackageReader.read(path);
 * for (Dependency dep : pkg.metadata().dependencies()) {
 *     System.out.println(dep.type() + ": " + dep.name() + " " + dep.versionConstraint());
 * }
 *
 * // Access file information
 * for (FileInfo file : pkg.metadata().files()) {
 *     System.out.println(file.path() + " (" + file.size() + " bytes)");
 * }
 * }</pre>
 *
 * @see io.spicelabs.baharat.common.Dependency
 * @see io.spicelabs.baharat.common.FileInfo
 * @see io.spicelabs.baharat.PackageMetadata
 */
package io.spicelabs.baharat.common;
