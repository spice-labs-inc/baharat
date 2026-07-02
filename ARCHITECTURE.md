# Architecture

This document describes the architecture of Baharat, a Java library for reading Linux and BSD package files.

## Overview

Baharat provides a unified API for reading six different package formats while maintaining access to format-specific features through a layered architecture.

```
┌─────────────────────────────────────────────────────────────────┐
│                        User Application                          │
└─────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                     Unified API Layer                            │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │
│  │PackageReader│  │   Package   │  │    PackageMetadata      │  │
│  └─────────────┘  └─────────────┘  └─────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                               │
        ┌──────────┬───────────┼───────────┬──────────┬──────────┐
        ▼          ▼           ▼           ▼          ▼          ▼
┌───────────┐┌───────────┐┌───────────┐┌───────────┐┌─────────┐┌─────────┐
│    RPM    ││    DEB    ││  Pacman   ││    APK    ││ FreeBSD ││ OpenBSD │
│  Reader   ││  Reader   ││  Reader   ││  Reader   ││ Reader  ││ Reader  │
└───────────┘└───────────┘└───────────┘└───────────┘└─────────┘└─────────┘
        │          │           │           │          │          │
        ▼          ▼           ▼           ▼          ▼          ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Common Components                             │
│  ┌──────────┐  ┌──────────┐  ┌────────────┐  ┌───────────────┐  │
│  │Dependency│  │ FileInfo │  │SecurityUtil│  │InputStreamSrc │  │
│  └──────────┘  └──────────┘  └────────────┘  └───────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

## Package Structure

```
io.spicelabs.baharat
├── Package.java              # Common package interface
├── PackageReader.java        # Main entry point with auto-detection
├── PackageFormat.java        # Format enumeration and detection
├── PackageMetadata.java      # Common metadata interface
├── PackageEntry.java         # Payload entry types
├── PackageException.java     # Exception hierarchy
│
├── common/                   # Shared types across formats
│   ├── Dependency.java       # Dependency representation
│   ├── FileInfo.java         # File metadata
│   └── SecurityUtils.java    # Path validation, limits
│
├── adapter/                  # I/O adapters
│   ├── InputStreamSource.java
│   └── PathInputStreamSource.java
│
├── rpm/                      # RPM format support
│   ├── RpmReader.java        # RPM-specific reader
│   ├── RpmPackage.java       # RPM package representation
│   ├── MetadataAdapter.java  # Adapts RPM to common interface
│   ├── exception/            # RPM-specific exceptions
│   ├── header/               # RPM header parsing
│   ├── lead/                 # RPM lead parsing
│   ├── payload/              # CPIO payload streaming
│   ├── signature/            # GPG/PGP signature verification
│   ├── verify/               # Digest verification
│   ├── metadata/             # RPM metadata types
│   ├── io/                   # Binary I/O utilities
│   ├── extract/              # File extraction
│   ├── delta/                # Delta RPM support
│   └── database/             # RPM database reading
│
├── deb/                      # Debian format support
│   ├── DebReader.java
│   ├── DebPackage.java
│   ├── DebMetadata.java
│   ├── DebControlParser.java
│   └── ArArchiveReader.java
│
├── pacman/                   # Arch Linux format support
│   ├── PacmanReader.java
│   ├── PacmanPackage.java
│   ├── PacmanMetadata.java
│   └── PkgInfoParser.java
│
├── apk/                      # Alpine format support
│   ├── ApkReader.java
│   ├── ApkPackage.java
│   ├── ApkMetadata.java
│   └── ApkInfoParser.java
│
├── freebsd/                  # FreeBSD format support
│   ├── FreeBsdReader.java
│   ├── FreeBsdPackage.java
│   ├── FreeBsdMetadata.java
│   └── ManifestParser.java
│
└── openbsd/                  # OpenBSD format support
    ├── OpenBsdReader.java
    ├── OpenBsdPackage.java
    ├── OpenBsdMetadata.java
    └── ContentsParser.java
```

## Core Interfaces

### Package Interface

The `Package` interface provides a unified view of any package format:

```java
public interface Package {
    PackageFormat format();           // Which format this package is
    PackageMetadata metadata();       // Common metadata
    Stream<PackageEntry> payload();   // Payload entries

    // Convenience methods
    String name();
    String version();
    String arch();

    // Package URL generation
    Purl purl();
}
```

Each format implements this interface (`RpmPackage`, `DebPackage`, etc.) while adding format-specific methods.

### PackageMetadata Interface

Common metadata available across all formats:

```java
public interface PackageMetadata {
    // Required fields
    String name();
    String version();
    String arch();
    long installedSize();

    // Optional fields
    Optional<String> release();
    Optional<Integer> epoch();
    Optional<String> description();
    Optional<String> summary();
    Optional<String> license();
    Optional<String> url();
    Optional<String> maintainer();
    Optional<Instant> buildTime();

    // Collections
    List<Dependency> dependencies();
    List<Dependency> provides();
    List<FileInfo> files();

    // Package URL
    Purl purl();
}
```

### PackageEntry Sealed Interface

Package payload entries use a sealed interface with pattern matching:

```java
public sealed interface PackageEntry permits
        PackageEntry.FileEntry,
        PackageEntry.DirectoryEntry,
        PackageEntry.SymlinkEntry {

    String path();
    int mode();
    Instant mtime();
    String userName();
    String groupName();

    record FileEntry(/* ... */) implements PackageEntry {
        long size();
        InputStream content();
    }

    record DirectoryEntry(/* ... */) implements PackageEntry { }

    record SymlinkEntry(/* ... */) implements PackageEntry {
        String target();
    }
}
```

## Format Detection

Format detection uses a multi-strategy approach:

1. **Magic bytes** - Most reliable; RPM has unique `0xEDABEEDB`, DEB has `!<arch>\n`
2. **File extension** - Fallback for ambiguous formats
3. **Internal structure** - For formats sharing magic (gzip-compressed)

```java
public static Optional<PackageFormat> detect(Path path) {
    byte[] header = readHeader(path);

    // Check unique magic bytes first
    if (matchesMagic(header, RPM.magic)) return Optional.of(RPM);
    if (matchesMagic(header, DEB.magic)) return Optional.of(DEB);

    // For gzip magic, use extension to disambiguate
    if (isGzip(header)) {
        if (fileName.endsWith(".apk")) return Optional.of(APK);
        if (fileName.endsWith(".tgz")) return Optional.of(OPENBSD_PKG);
        if (fileName.contains(".pkg.tar")) return Optional.of(PACMAN);
    }

    // Fall back to extension-based detection
    return detectFromExtension(fileName);
}
```

## RPM Architecture (Most Complex)

RPM has the most complex internal structure:

```
┌────────────────────────────────────────────────────────────────┐
│                         RpmReader                               │
└────────────────────────────────────────────────────────────────┘
                              │
          ┌───────────────────┼───────────────────┐
          ▼                   ▼                   ▼
    ┌──────────┐       ┌────────────┐      ┌─────────────┐
    │LeadParser│       │HeaderParser│      │PayloadReader│
    └──────────┘       └────────────┘      └─────────────┘
          │                   │                   │
          ▼                   ▼                   ▼
    ┌──────────┐       ┌────────────┐      ┌─────────────┐
    │   Lead   │       │   Header   │      │CpioArchive │
    │ (96 bytes)│       │(metadata) │      │  Reader    │
    └──────────┘       └────────────┘      └─────────────┘
                              │
                    ┌─────────┴─────────┐
                    ▼                   ▼
             ┌────────────┐      ┌────────────┐
             │ Signature  │      │   Main     │
             │  Header    │      │  Header    │
             └────────────┘      └────────────┘
```

### RPM Header Structure

Headers use a tag-based binary format:

```java
public final class Header {
    private final Map<Integer, IndexEntry> entries;

    public Optional<String> getString(int tag);
    public Optional<String[]> getStringArray(int tag);
    public Optional<Integer> getInt32(int tag);
    public Optional<int[]> getInt32Array(int tag);
    public Optional<Long> getInt64(int tag);
    public Optional<byte[]> getBinary(int tag);
}
```

### Payload Decompression

RPM payloads support multiple compression formats:

```java
public enum CompressionType {
    GZIP, BZIP2, XZ, ZSTD, LZMA, NONE;

    public InputStream decompress(InputStream in) {
        return switch (this) {
            case GZIP -> new GZIPInputStream(in);
            case BZIP2 -> new BZip2CompressorInputStream(in);
            case XZ -> new XZInputStream(in);
            case ZSTD -> new ZstdInputStream(in);
            case LZMA -> new LZMAInputStream(in);
            case NONE -> in;
        };
    }
}
```

## DEB Architecture

DEB uses the `ar` archive format:

```
┌────────────────────────────────────────────────────────────────┐
│                         DebReader                               │
└────────────────────────────────────────────────────────────────┘
                              │
          ┌───────────────────┼───────────────────┐
          ▼                   ▼                   ▼
    ┌──────────────┐  ┌────────────────┐   ┌─────────────┐
    │ArArchiveReader│  │DebControlParser│   │ TarArchive │
    └──────────────┘  └────────────────┘   │  Streaming  │
          │                   │            └─────────────┘
          ▼                   ▼
    ┌──────────────┐  ┌────────────────┐
    │debian-binary │  │  control file  │
    │ control.tar  │  │ (RFC 822-like) │
    │  data.tar    │  └────────────────┘
    └──────────────┘
```

The control file uses a simple key-value format:

```
Package: curl
Version: 7.81.0-1ubuntu1.7
Architecture: amd64
Depends: libc6 (>= 2.17), libcurl4 (= 7.81.0-1ubuntu1.7)
```

## Tar-Based Formats (Pacman, APK, FreeBSD, OpenBSD)

These formats use compressed tar archives with metadata files:

```
┌────────────────────────────────────────────────────────────────┐
│                    TarBasedReader                               │
│              (Pacman, APK, FreeBSD, OpenBSD)                    │
└────────────────────────────────────────────────────────────────┘
                              │
          ┌───────────────────┴───────────────────┐
          ▼                                       ▼
    ┌──────────────┐                       ┌─────────────┐
    │ Decompressor │                       │ TarArchive  │
    │ (gzip/xz/zst)│                       │   Reader    │
    └──────────────┘                       └─────────────┘
          │                                       │
          │                    ┌──────────────────┼──────────────────┐
          │                    ▼                  ▼                  ▼
          │              ┌──────────┐       ┌──────────┐       ┌──────────┐
          └──────────────│ Metadata │       │  Files   │       │ Scripts  │
                         │   File   │       │          │       │          │
                         └──────────┘       └──────────┘       └──────────┘
```

| Format | Metadata File | Parser |
|--------|---------------|--------|
| Pacman | `.PKGINFO` | `PkgInfoParser` |
| APK | `.PKGINFO` | `ApkInfoParser` |
| FreeBSD | `+MANIFEST` (JSON) | `ManifestParser` |
| OpenBSD | `+CONTENTS` | `ContentsParser` |

## Package URL (PURL) Generation

PURLs follow the [Package URL specification](https://github.com/package-url/purl-spec):

```
pkg:TYPE/NAMESPACE/NAME@VERSION?QUALIFIERS
```

### PURL Type Mapping

| Format | PURL Type | Example |
|--------|-----------|---------|
| RPM | `rpm` | `pkg:rpm/fedora/curl@7.50.3-1.fc25?arch=x86_64` |
| DEB | `deb` | `pkg:deb/debian/curl@7.81.0-1?arch=amd64` |
| Pacman | `alpm` | `pkg:alpm/arch/curl@7.50.3-1?arch=x86_64` |
| APK | `apk` | `pkg:apk/alpine/curl@7.50.3-r0?arch=x86_64` |
| FreeBSD | `freebsd` | `pkg:freebsd/curl@7.50.3?arch=amd64` |
| OpenBSD | `openbsd` | `pkg:openbsd/curl@7.50.3?arch=amd64` |

### PURL Implementation

There is exactly one canonical PURL per package. `Package.purl()` delegates to `PackageMetadata.purl()`, which is implemented per format and produces a `io.spicelabs.coordinates.Purl` value.

```java
// Package.purl() - delegates to metadata
public default Purl purl() {
    return metadata().purl();
}

// PackageMetadata.purl() - format-specific implementation
public Purl purl() {
    // Build PURL with type-specific namespace, qualifiers, etc.
    return PurlHelper.build(type, namespace, name(), version, qualifiers);
}
```

## Security Architecture

### Path Traversal Prevention

All payload paths are validated before use:

```java
public static String sanitizePath(String path) {
    // Reject absolute paths
    if (path.startsWith("/")) {
        path = path.substring(1);
    }

    // Reject path traversal attempts
    if (path.contains("..")) {
        throw new SecurityException("Path traversal detected: " + path);
    }

    // Normalize path separators
    return path.replace("\\", "/");
}
```

### Decompression Bomb Protection

Configurable limits prevent resource exhaustion:

```java
public class BoundedInputStream extends FilterInputStream {
    private final long maxBytes;
    private long bytesRead = 0;

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int result = super.read(b, off, len);
        if (result > 0) {
            bytesRead += result;
            if (bytesRead > maxBytes) {
                throw new IOException("Decompression bomb detected");
            }
        }
        return result;
    }
}
```

### Integer Overflow Protection

Safe arithmetic for offset calculations:

```java
public static long safeAdd(long a, long b) {
    long result = a + b;
    if (((a ^ result) & (b ^ result)) < 0) {
        throw new ArithmeticException("Integer overflow");
    }
    return result;
}
```

## Testing Architecture

### Test Structure

```
src/test/java/io/spicelabs/baharat/
├── PackageUrlTest.java              # PURL unit tests
├── PackageMetadataPurlTest.java     # Metadata PURL tests
├── CrossFormatPurlIntegrationTest.java  # Cross-format consistency
├── PackageFormatTest.java           # Format detection tests
│
├── rpm/
│   ├── ReaderIntegrationTest.java   # Real RPM file tests
│   ├── MalformedPackageTest.java    # Error handling
│   ├── security/                    # Security tests
│   └── ...
│
├── deb/
│   ├── DebReaderIntegrationTest.java
│   └── ...
│
└── ... (similar for each format)
```

### Test Data

Test packages are stored in `src/test/resources/`:
- `rpms/` - Real RPM files
- `debs/` - Real DEB files
- `pacman/` - Real Pacman packages
- `apks/` - Real APK files
- `freebsd/` - Real FreeBSD packages
- `openbsd/` - Real OpenBSD packages

### Integration Test Pattern

```java
@Test
@EnabledIf("hasTestFiles")
void readRealPackage() throws Exception {
    Package pkg = Reader.read(testFilePath);

    assertThat(pkg.name()).isNotEmpty();
    assertThat(pkg.version()).isNotEmpty();

    // Verify PURL roundtrip
    Purl purl = pkg.purl();
    Purl parsed = Purl.parse(purl.toCanonical());
    assertThat(parsed.getName()).isEqualTo(purl.getName());
}
```

## Thread Safety Model

| Component | Thread Safety | Notes |
|-----------|---------------|-------|
| `PackageReader` | Thread-safe | Static utility methods |
| `*Package` classes | Immutable | Safe to share |
| `*Metadata` classes | Immutable | Safe to share |
| `PayloadReader` | Not thread-safe | Single-thread streaming |
| `Stream<PackageEntry>` | Not thread-safe | Close after use |

## Performance Considerations

1. **Lazy Loading** - Payload is not read until `payload()` is called
2. **Streaming** - Payload entries are streamed, not loaded into memory
3. **Buffered I/O** - All I/O uses buffered streams
4. **Memory Limits** - Configurable limits prevent excessive memory use

### Typical Memory Usage

- Small packages (< 1MB): ~2-5MB heap
- Large packages (> 100MB): ~10-20MB heap (streaming)
- Payload extraction: Proportional to largest single file

## Extension Points

### Adding a New Format

1. Create format enum value in `PackageFormat`
2. Implement format-specific reader class
3. Implement `Package` and `PackageMetadata` interfaces
4. Add case to `PackageReader.read()` switch
5. Add format detection logic
6. Add PURL type mapping
7. Write integration tests

### Custom Decompression

Implement the compression interface:

```java
public InputStream decompress(InputStream in, String compressor) {
    return switch (compressor) {
        case "gzip" -> new GZIPInputStream(in);
        case "custom" -> new CustomDecompressor(in);
        default -> throw new UnsupportedOperationException(compressor);
    };
}
```

## Dependencies

| Dependency | Purpose | Format(s) |
|------------|---------|-----------|
| XZ for Java | XZ/LZMA decompression | RPM, DEB, Pacman, FreeBSD |
| zstd-jni | Zstandard decompression | RPM, DEB, Pacman, FreeBSD |
| Commons Compress | bzip2 decompression | RPM, DEB |
| Bouncy Castle | PGP signature verification | RPM |
| coordinates     | Canonical identifiers and PURLs | All |
| Gson | JSON parsing | FreeBSD |
| SLF4J | Logging | All |
