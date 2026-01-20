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
PackageURL purl = pkg.packageUrl();
System.out.println("PURL: " + purl.canonicalize());
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

---

## 🔐 Security Considerations

This library includes protections against common attacks:

- **Path traversal**: Payload paths are validated to prevent `../` escapes
- **Decompression bombs**: Configurable limit on decompressed size (default 10GB)
- **Integer overflow**: Safe arithmetic in offset calculations
- **Resource exhaustion**: Limits on array sizes and string lengths

For security-sensitive applications, always verify package signatures before trusting content.

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
