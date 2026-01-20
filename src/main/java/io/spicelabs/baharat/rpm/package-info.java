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
 * RPM (Red Hat Package Manager) format support.
 *
 * <p>This package provides comprehensive support for reading RPM package files,
 * used by Fedora, RHEL, CentOS, openSUSE, Amazon Linux, and other distributions.
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link io.spicelabs.baharat.rpm.RpmReader} - Main entry point for reading RPM files</li>
 *   <li>{@link io.spicelabs.baharat.rpm.RpmPackage} - RPM package representation</li>
 * </ul>
 *
 * <h2>RPM File Structure</h2>
 * <pre>
 * +------------------+
 * |      Lead        |  96 bytes - Legacy header
 * +------------------+
 * | Signature Header |  Variable - Cryptographic signatures
 * +------------------+
 * |   Main Header    |  Variable - Package metadata
 * +------------------+
 * |     Payload      |  Variable - Compressed CPIO archive
 * +------------------+
 * </pre>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Read an RPM file
 * RpmPackage rpm = RpmReader.read(Path.of("package.rpm"));
 *
 * // Access metadata
 * System.out.println("Name: " + rpm.name());
 * System.out.println("NEVRA: " + rpm.nevra());
 * System.out.println("Version: " + rpm.version());
 *
 * // Stream payload entries
 * try (Stream<PayloadEntry> entries = RpmReader.streamPayload(path)) {
 *     entries.forEach(e -> System.out.println(e.path()));
 * }
 * }</pre>
 *
 * <h2>Sub-packages</h2>
 * <ul>
 *   <li>{@link io.spicelabs.baharat.rpm.header} - Header parsing</li>
 *   <li>{@link io.spicelabs.baharat.rpm.payload} - CPIO payload streaming</li>
 *   <li>{@link io.spicelabs.baharat.rpm.signature} - Signature verification</li>
 *   <li>{@link io.spicelabs.baharat.rpm.lead} - Lead section parsing</li>
 *   <li>{@link io.spicelabs.baharat.rpm.metadata} - Metadata extraction</li>
 *   <li>{@link io.spicelabs.baharat.rpm.exception} - RPM-specific exceptions</li>
 * </ul>
 *
 * @see io.spicelabs.baharat.rpm.RpmReader
 * @see io.spicelabs.baharat.rpm.RpmPackage
 */
package io.spicelabs.baharat.rpm;
