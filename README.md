# 🫙 Baharat

[![Maven Central](https://img.shields.io/maven-central/v/io.spicelabs/baharat?label=Maven%20Central)](https://central.sonatype.com/artifact/io.spicelabs/baharat)
[![GitHub Release](https://img.shields.io/github/v/release/spice-labs-inc/baharat?label=GitHub%20Release)](https://github.com/spice-labs-inc/baharat/releases)
[![GitHub Package](https://img.shields.io/badge/GitHub-Packages-blue?logo=github)](https://github.com/spice-labs-inc/baharat/packages/)
[![Build Status](https://github.com/spice-labs-inc/baharat/actions/workflows/buildAndTest.yml/badge.svg)](https://github.com/spice-labs-inc/baharat/actions)

**Baharat** (Arabic for "spices") is a Java library for reading Linux and BSD package files, extracting metadata, and streaming payload contents. Just as the spice blend combines many flavors, Baharat blends support for six major package formats into a unified API.

## 🚀 Quick Start

### ⚡️ Prerequisites

- **Java 21** or higher
- **Maven 3.6+** (for building)

### 📦 Installation

#### Maven

```xml
<dependency>
    <groupId>io.spicelabs</groupId>
    <artifactId>baharat</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

#### Gradle

```groovy
implementation 'io.spicelabs:baharat:0.0.1-SNAPSHOT'
```

---

## 📋 Supported Formats

| Format | Extension | Distributions | PURL Type |
|--------|-----------|---------------|-----------|
| **RPM** | `.rpm` | Fedora, RHEL, CentOS, openSUSE, Amazon Linux | `pkg:rpm/...` |
| **DEB** | `.deb` | Debian, Ubuntu, Linux Mint, Pop!_OS | `pkg:deb/...` |
| **Pacman** | `.pkg.tar.*` | Arch Linux, Manjaro, EndeavourOS | `pkg:alpm/...` |
| **APK** | `.apk` | Alpine Linux, postmarketOS | `pkg:apk/...` |
| **FreeBSD pkg** | `.pkg`, `.txz` | FreeBSD 10+ | `pkg:freebsd/...` |
| **OpenBSD pkg** | `.tgz` | OpenBSD | `pkg:openbsd/...` |

---

## ⚙️ Features

- **Unified API**: Read any supported format with `PackageReader.read(path)`
- **Format-specific access**: Use typed readers for format-specific metadata
- **Package URL (PURL) support**: Generate standard Package URLs for all formats
- **Stream payload contents**: Extract files without loading entire package into memory
- **Signature verification**: Verify RSA, DSA, and GPG signatures (RPM)
- **File digest verification**: Verify SHA256/MD5 checksums
- **Multiple compression formats**: gzip, xz, zstd, bzip2, lzma
- **Null-safe API**: Uses `Optional<T>` and `@NotNull` annotations throughout
- **Security hardened**: Protection against path traversal, decompression bombs, and malformed inputs

---

## 🔧 Basic Usage

### Reading Any Package (Auto-Detection)

```java
import io.spicelabs.baharat.Package;
import io.spicelabs.baharat.PackageReader;

// Auto-detect format and read
Package pkg = PackageReader.read(Path.of("package.rpm"));

// Access basic information (works for all formats)
System.out.println("Name: " + pkg.name());
System.out.println("Version: " + pkg.version());
System.out.println("Architecture: " + pkg.arch());
System.out.println("Format: " + pkg.format());

// Generate Package URL
Purl purl = pkg.purl();
System.out.println("PURL: " + purl.toCanonical());
// Output: pkg:rpm/fedora/curl@7.50.3-1.fc25?arch=x86_64
```

### Format-Specific Reading

```java
import io.spicelabs.baharat.rpm.RpmPackage;
import io.spicelabs.baharat.rpm.RpmReader;
import io.spicelabs.baharat.deb.DebPackage;
import io.spicelabs.baharat.deb.DebReader;

// Read RPM with full access to RPM-specific features
RpmPackage rpm = RpmReader.read(Path.of("package.rpm"));
System.out.println("NEVRA: " + rpm.nevra());

// Read DEB with access to Debian-specific fields
DebPackage deb = DebReader.read(Path.of("package.deb"));
System.out.println("Priority: " + deb.debMetadata().priority().orElse("optional"));
```

### Using Pattern Matching for Multi-Format Code

```java
Package pkg = PackageReader.read(path);

String description = switch (pkg) {
    case RpmPackage rpm -> "RPM: " + rpm.nevra();
    case DebPackage deb -> "DEB: " + deb.name() + "_" + deb.version();
    case PacmanPackage pac -> "Pacman: " + pac.name() + "-" + pac.version();
    case ApkPackage apk -> "APK: " + apk.name() + "-" + apk.version();
    case FreeBsdPackage fbsd -> "FreeBSD: " + fbsd.name() + "-" + fbsd.version();
    case OpenBsdPackage obsd -> "OpenBSD: " + obsd.name() + "-" + obsd.version();
    default -> "Unknown: " + pkg.name();
};
```

### Streaming Payload Contents

```java
import io.spicelabs.baharat.PackageEntry;

// Stream payload entries without extracting to disk
try (Stream<PackageEntry> entries = PackageReader.streamPayload(path)) {
    entries.forEach(entry -> {
        System.out.println(entry.path() + " (" + entry.getClass().getSimpleName() + ")");

        if (entry instanceof PackageEntry.FileEntry file) {
            System.out.println("  Size: " + file.size());
            // Access file content stream
            try (InputStream content = file.content()) {
                // Process file content...
            }
        } else if (entry instanceof PackageEntry.SymlinkEntry symlink) {
            System.out.println("  -> " + symlink.target());
        }
    });
}
```

You can also stream via the package object itself when it was read from a file
path (this is the path Goat Rodeo uses):

```java
Package pkg = PackageReader.read(path);
try (Stream<PackageEntry> entries = pkg.payload()) {
    entries.forEach(entry -> System.out.println(entry.path()));
}
```

> **Note:** `Package.payload()` requires the package to have been read from a
> `Path`. Packages read from an `InputStream` cannot re-read their source, so
> `payload()` throws `PackageException` with guidance; stream those directly via
> `streamPayload(Path)` or the format reader's `streamPayload(InputStream)`.
> See [docs/payload-streaming.md](docs/payload-streaming.md).

---

## 🔐 Security Considerations

This library includes protections against common attacks:

- **Path traversal**: Payload paths are validated to prevent `../` escapes
- **Decompression bombs**: Configurable limit on decompressed size (default 2 GiB per read pass,
  injectable via `BudgetLimits`) — verified by `DebBudgetTest`, `ApkBudgetTest`,
  `FreeBsdBudgetTest`, `OpenBsdBudgetTest`, `PacmanBudgetTest`
- **Integer overflow**: Safe arithmetic (`Math.addExact`) in offset calculations —
  `HeaderStrictAccessorTest.overflowedOffsetRejectedByIntAccessors`, `HeaderParserCapTest`
- **Resource exhaustion**: Limits on array sizes and string lengths — RPM header caps
  (100k entries / 64 MiB data store, `HeaderParserCapTest`), entry-count caps, 10 MiB
  metadata-member caps
- **Strict truncation**: truncated archives throw instead of returning partial data —
  `TruncationSweepBombTest`, `CpioSecurityTest`, `ArArchiveSecurityTest`
- **Stream exception boundary**: mid-stream corruption surfaces as the documented
  `BaharatStreamException` (never a bare `RuntimeException`) — `StreamBoundaryAndCriticalTagsTest`
- **Extractor write-through protection**: extraction refuses to write or delete through
  archive-created symlinks — `ExtractorSymlinkWriteThroughTest`
- **Property-based fuzzing** (jqwik): `PackageReaderFuzzBombTest`

For security-sensitive applications, always verify package signatures before trusting content.

See [plans/adr/](plans/adr/) for the architecture decision records.

---

## 🛠️ Maintainers

### 🔨 Build Locally

Install JDK 21+ and Maven 3.6+.

Clone the repo:

```bash
git clone https://github.com/spice-labs-inc/baharat.git
cd baharat
```

Build with Maven:

```bash
mvn clean install
```

Run tests only:

```bash
mvn test
```

Generate Javadoc:

```bash
mvn javadoc:javadoc
```

Check test coverage (report in `target/site/jacoco/`):

```bash
mvn test jacoco:report
```

---

### 🚀 Releasing

1. **Create a GitHub Release**
   Use a tag like `v0.1.0`. This triggers GitHub Actions to:

   - Build the JAR
   - Publish to GitHub Packages
   - Upload artifacts to Maven Central (automated)

2. **Monitor Maven Central** (optional)
   Visit [https://central.sonatype.com](https://central.sonatype.com) → Deployments
   Propagation takes ~40 minutes.

3. **Verify the JAR**

```bash
mvn dependency:get \
  -Dartifact=io.spicelabs:baharat:0.1.0
```

---

## 📦 Repository

Maintained by [Spice Labs](https://github.com/spice-labs-inc).

- [`baharat`](https://github.com/spice-labs-inc/baharat) — this library
- [`spice-labs-cli`](https://github.com/spice-labs-inc/spice-labs-cli) — Spice Labs Surveyor CLI

---

## 🐐 Goat Rodeo Integration

[Goat Rodeo](https://github.com/spice-labs-inc/goatrodeo) is Spice Labs' tool for building Artifact Dependency Graphs (ADGs). Baharat can be used as a processing strategy in Goat Rodeo to handle Linux/BSD package formats.

### Adding Baharat to Goat Rodeo

Add Baharat as a dependency in your `build.sbt`:

```scala
libraryDependencies += "io.spicelabs" % "baharat" % "0.0.1-SNAPSHOT"
```

### Creating a Baharat Strategy

Goat Rodeo uses a strategy pattern where each processor implements the `ToProcess` trait. Here's how to create a strategy using Baharat that handles all six package formats:

```scala
package io.spicelabs.goatrodeo.omnibor.strategies

import io.spicelabs.goatrodeo.omnibor.{ToProcess, ProcessingState, ByUUID, ByName}
import io.spicelabs.baharat.{PackageReader, Package, PackageFormat}
import io.spicelabs.baharat.rpm.RpmPackage
import io.spicelabs.baharat.deb.DebPackage
import io.spicelabs.baharat.pacman.PacmanPackage
import io.spicelabs.baharat.apk.ApkPackage
import io.spicelabs.baharat.freebsd.FreeBsdPackage
import io.spicelabs.baharat.openbsd.OpenBsdPackage

import java.io.File
import java.nio.file.Path
import scala.jdk.CollectionConverters._
import scala.util.{Try, Success, Failure}

object BaharatStrategy {

  /** MIME types for all supported package formats */
  val supportedMimeTypes: Set[String] = Set(
    "application/x-rpm",
    "application/x-debian-package",
    "application/x-xz",           // Pacman .pkg.tar.xz
    "application/zstd",           // Pacman .pkg.tar.zst
    "application/gzip",           // APK, OpenBSD .tgz
    "application/x-tar"           // FreeBSD .pkg (tar+zstd)
  )

  /**
   * Compute files to process using Baharat.
   * This can replace the existing Debian strategy and adds support for
   * RPM, Pacman, APK, FreeBSD, and OpenBSD packages.
   */
  def computeBaharatFiles(
      mimeType: String,
      file: File,
      state: ProcessingState
  ): (Vector[ToProcess], ByUUID, ByName, String) = {

    // Check if this is a supported package format
    val path = file.toPath
    val formatOpt = Try(PackageReader.detect(path)).toOption.flatten

    formatOpt match {
      case Some(format) =>
        Try(PackageReader.read(path)) match {
          case Success(pkg) =>
            val purl = pkg.purl().toCanonical()

            // Create ToProcess entries for payload files
            val payloadEntries = Try {
              pkg.payload().iterator().asScala.flatMap { entry =>
                entry match {
                  case file: io.spicelabs.baharat.PackageEntry.FileEntry =>
                    Some(createFileToProcess(file, state))
                  case _ => None
                }
              }.toVector
            }.getOrElse(Vector.empty)

            (payloadEntries, state.byUUID, state.byName, purl)

          case Failure(e) =>
            // Log error and return empty
            (Vector.empty, state.byUUID, state.byName, "")
        }

      case None =>
        // Not a recognized package format
        (Vector.empty, state.byUUID, state.byName, "")
    }
  }

  private def createFileToProcess(
      entry: io.spicelabs.baharat.PackageEntry.FileEntry,
      state: ProcessingState
  ): ToProcess = {
    // Implementation depends on your ToProcess structure
    // This is a placeholder showing the pattern
    ???
  }
}
```

### Registering the Strategy

In `ToProcess.scala`, add the Baharat strategy to the `computeToProcess` vector:

```scala
val computeToProcess: Vector[
  (String, File, ProcessingState) => (Vector[ToProcess], ByUUID, ByName, String)
] = Vector(
  MavenToProcess.computeMavenFiles,
  DockerToProcess.computeDockerFiles,
  BaharatStrategy.computeBaharatFiles,  // Replaces Debian.computeDebianFiles
  DotnetFile.computeDotnetFiles,
  GenericFile.computeGenericFiles
)
```

### Replacing the Debian Strategy

Baharat's unified API provides several advantages over the existing Debian strategy:

1. **Six formats with one API**: Handle RPM, DEB, Pacman, APK, FreeBSD, and OpenBSD packages
2. **Consistent PURL generation**: All formats use the same `purl()` method
3. **Stream-based payload access**: Memory-efficient processing of large packages
4. **Type-safe metadata access**: Pattern matching for format-specific metadata

Example showing format-specific handling:

```scala
val pkg = PackageReader.read(path)
val formatInfo = pkg match {
  case rpm: RpmPackage => s"RPM: ${rpm.nevra()}"
  case deb: DebPackage => s"DEB: ${deb.name()}_${deb.version()}"
  case pac: PacmanPackage => s"Pacman: ${pac.name()}-${pac.version()}"
  case apk: ApkPackage => s"APK: ${apk.name()}-${apk.version()}"
  case fbsd: FreeBsdPackage => s"FreeBSD: ${fbsd.name()}-${fbsd.version()}"
  case obsd: OpenBsdPackage => s"OpenBSD: ${obsd.name()}-${obsd.version()}"
}
```

### PURL Examples

Baharat generates standard Package URLs for all formats:

| Format | Example PURL |
|--------|-------------|
| RPM | `pkg:rpm/fedora/curl@7.50.3-1.fc25?arch=x86_64` |
| DEB | `pkg:deb/debian/curl@7.50.3-1?arch=amd64` |
| Pacman | `pkg:alpm/arch/curl@7.50.3-1?arch=x86_64` |
| APK | `pkg:apk/alpine/curl@7.50.3-r0?arch=x86_64` |
| FreeBSD | `pkg:freebsd/curl@7.50.3?arch=amd64` |
| OpenBSD | `pkg:openbsd/curl@7.50.3?arch=amd64` |

---

## 📚 References

- [RPM File Format](https://rpm-software-management.github.io/rpm/manual/format.html)
- [DEB Policy Manual](https://www.debian.org/doc/debian-policy/)
- [ALPM/Pacman Package Guidelines](https://wiki.archlinux.org/title/PKGBUILD)
- [Alpine APK Format](https://wiki.alpinelinux.org/wiki/Apk_spec)
- [FreeBSD pkg(8)](https://man.freebsd.org/cgi/man.cgi?query=pkg&sektion=8)
- [OpenBSD Packages](https://www.openbsd.org/faq/faq15.html)
- [Package URL Specification](https://github.com/package-url/purl-spec)

---

## ⚖️ License

Apache License 2.0. See [`LICENSE`](LICENSE).
