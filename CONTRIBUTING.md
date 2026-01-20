# Contributing to Baharat

Thank you for your interest in contributing to Baharat! This document provides guidelines and instructions for contributing.

## Code of Conduct

This project adheres to a Code of Conduct. By participating, you are expected to uphold this code. Please report unacceptable behavior to dev@spicelabs.io.

## How to Contribute

### Reporting Bugs

Before creating a bug report, please check existing issues to avoid duplicates. When creating a bug report, include:

- **Clear title** describing the issue
- **Steps to reproduce** the behavior
- **Expected behavior** vs actual behavior
- **Environment details** (Java version, OS, RPM file details if relevant)
- **Sample RPM file** if the issue is file-specific (ensure it doesn't contain sensitive data)

### Suggesting Features

Feature suggestions are welcome! Please include:

- **Use case** - Why is this feature needed?
- **Proposed solution** - How should it work?
- **Alternatives considered** - What other approaches did you consider?

### Pull Requests

1. **Fork the repository** and create your branch from `main`
2. **Write tests** for any new functionality
3. **Follow the code style** (see below)
4. **Update documentation** if needed
5. **Ensure tests pass** with `mvn test`
6. **Submit the pull request** with a clear description

## Development Setup

### Prerequisites

- Java 21 or higher
- Maven 3.6+
- Git

### Building

```bash
# Clone your fork
git clone https://github.com/YOUR_USERNAME/baharat.git
cd baharat

# Build and run tests
mvn clean install

# Run tests only
mvn test

# Generate coverage report
mvn test jacoco:report
# Report is in target/site/jacoco/index.html
```

### Running Specific Tests

```bash
# Run a specific test class
mvn test -Dtest=RpmReaderIntegrationTest

# Run tests matching a pattern
mvn test -Dtest="*Payload*"
```

## Code Style

### General Guidelines

- Use **4 spaces** for indentation (no tabs)
- Maximum line length: **120 characters**
- Use **meaningful names** for variables, methods, and classes
- Write **self-documenting code**; add comments only when necessary
- Follow **Java naming conventions**

### Null Safety

This project enforces a strict no-null policy:

- Use `@NotNull` annotation on all parameters that must not be null
- Use `Optional<T>` for return values that may be absent
- Never return `null` from any method
- Use `@Nullable` only for methods that interact with external APIs

```java
// Good
public @NotNull Optional<String> getName() {
    return Optional.ofNullable(header.getString(TAG_NAME));
}

// Bad
public String getName() {
    return header.getString(TAG_NAME); // Could return null!
}
```

### Immutability

- Prefer immutable objects (records, final fields)
- Use `List.copyOf()` and `Map.copyOf()` for defensive copies
- Don't expose mutable internal state

```java
// Good
public record FileInfo(@NotNull String path, long size) {}

// Good - defensive copy
public @NotNull List<FileInfo> files() {
    return List.copyOf(files);
}
```

### Error Handling

- Use `PackageException` and its subclasses for package-related errors
- Use format-specific exceptions when appropriate (e.g., `InvalidFormatException` for RPM)
- Include helpful error messages with context
- Don't catch generic `Exception` unless re-throwing

```java
// Good
throw new PackageException("Invalid magic: expected 0x" +
    Integer.toHexString(EXPECTED) + ", got 0x" + Integer.toHexString(actual),
    PackageFormat.RPM);

// Also good - format-specific exception
throw new InvalidFormatException(
    String.format("Invalid magic: expected 0x%08X, got 0x%08X", EXPECTED, actual));

// Bad
throw new RuntimeException("Invalid magic");
```

### Testing

- Write unit tests for all new functionality
- Use descriptive test method names
- Test edge cases and error conditions
- Aim for 80%+ code coverage

```java
@Test
void readPackage_withValidRpm_returnsMetadata() {
    // Arrange
    Path rpmPath = testRpms.resolve("valid-package.rpm");

    // Act
    RpmPackage rpm = RpmReader.read(rpmPath);

    // Assert
    assertThat(rpm.name()).isEqualTo("test-package");
    assertThat(rpm.version()).isEqualTo("1.0.0");
}

@Test
void readPackage_withCorruptedFile_throwsInvalidRpmException() {
    Path corruptedPath = testRpms.resolve("corrupted.rpm");

    assertThatThrownBy(() -> RpmReader.read(corruptedPath))
        .isInstanceOf(InvalidRpmException.class)
        .hasMessageContaining("Invalid magic");
}
```

### Documentation

- Add Javadoc to all public classes and methods
- Include `@param`, `@return`, and `@throws` tags
- Provide usage examples for complex APIs

```java
/**
 * Reads an RPM package from the given file path.
 *
 * <p>Example usage:
 * <pre>{@code
 * RpmPackage rpm = RpmReader.read(Path.of("package.rpm"));
 * System.out.println(rpm.name());
 * }</pre>
 *
 * @param path the path to the RPM file
 * @return the parsed RPM package
 * @throws InvalidRpmException if the file is not a valid RPM
 * @throws IOException if an I/O error occurs
 */
public static @NotNull RpmPackage read(@NotNull Path path)
        throws InvalidRpmException, IOException {
    // ...
}
```

## Project Structure

```
src/
├── main/java/io/spicelabs/baharat/
│   ├── Package.java            # Common package interface
│   ├── PackageReader.java      # Main entry point (auto-detection)
│   ├── PackageFormat.java      # Format enumeration and detection
│   ├── PackageMetadata.java    # Common metadata interface
│   ├── PackageEntry.java       # Payload entry types
│   ├── PackageException.java   # Exception hierarchy
│   │
│   ├── common/                 # Shared types
│   │   ├── Dependency.java     # Dependency representation
│   │   ├── FileInfo.java       # File metadata
│   │   └── SecurityUtils.java  # Security utilities
│   │
│   ├── rpm/                    # RPM format support
│   │   ├── RpmReader.java      # RPM-specific reader
│   │   ├── RpmPackage.java     # RPM package
│   │   ├── header/             # RPM header parsing
│   │   ├── payload/            # CPIO payload streaming
│   │   ├── signature/          # Signature verification
│   │   └── ...
│   │
│   ├── deb/                    # Debian format support
│   │   ├── DebReader.java
│   │   ├── DebPackage.java
│   │   └── ...
│   │
│   ├── pacman/                 # Arch Linux format support
│   ├── apk/                    # Alpine Linux format support
│   ├── freebsd/                # FreeBSD format support
│   └── openbsd/                # OpenBSD format support
│
└── test/
    ├── java/                   # Test classes (mirrors main structure)
    └── resources/
        ├── rpms/               # Test RPM files
        ├── debs/               # Test DEB files
        ├── pacman/             # Test Pacman packages
        ├── apks/               # Test APK files
        ├── freebsd/            # Test FreeBSD packages
        └── openbsd/            # Test OpenBSD packages
```

See [ARCHITECTURE.md](ARCHITECTURE.md) for detailed architecture documentation.

## Commit Messages

Follow the [Conventional Commits](https://www.conventionalcommits.org/) specification:

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

Types:
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation only
- `style`: Code style (formatting, no code change)
- `refactor`: Code change that neither fixes a bug nor adds a feature
- `test`: Adding or correcting tests
- `chore`: Maintenance tasks

Examples:
```
feat(payload): add support for zstd compression

fix(signature): handle missing key ID gracefully

docs: update README with extraction examples

test(header): add tests for malformed headers
```

## Release Process

Releases are managed by maintainers. The process is:

1. Update version in `pom.xml`
2. Update `CHANGELOG.md`
3. Create a release branch
4. Run full test suite
5. Create GitHub release with tag
6. Deploy to Maven Central

## Getting Help

- **Questions**: Open a GitHub Discussion
- **Bugs**: Open a GitHub Issue
- **Security**: Email security@spicelabs.io (do not open public issues)

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.
