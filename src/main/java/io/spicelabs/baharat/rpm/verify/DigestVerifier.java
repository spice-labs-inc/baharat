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
package io.spicelabs.baharat.rpm.verify;

import io.spicelabs.baharat.rpm.RpmPackage;
import io.spicelabs.baharat.rpm.RpmReader;
import io.spicelabs.baharat.rpm.exception.FormatException;
import io.spicelabs.baharat.rpm.metadata.FileInfo;
import io.spicelabs.baharat.rpm.payload.PayloadEntry;
import io.spicelabs.baharat.rpm.payload.PayloadReader;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Verifies file digests (checksums) within RPM packages.
 *
 * <p>RPM packages contain digest information for each file in the package header.
 * This class extracts file contents from the payload and verifies that the
 * computed digests match the expected values.
 *
 * <p>Supported digest algorithms:
 * <ul>
 *   <li>SHA-256 (modern RPMs, RPM 4.6+)</li>
 *   <li>MD5 (legacy RPMs)</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * // Verify all files in a package
 * DigestVerifier.Result result = DigestVerifier.verify(Path.of("package.rpm"));
 *
 * if (result.isValid()) {
 *     System.out.println("All " + result.verifiedCount() + " files verified");
 * } else {
 *     for (DigestVerifier.Failure failure : result.failures()) {
 *         System.out.println("FAILED: " + failure.path() + " - " + failure.reason());
 *     }
 * }
 * }</pre>
 *
 * @see FileInfo#digest()
 */
public final class DigestVerifier {

    private static final Logger log = LoggerFactory.getLogger(DigestVerifier.class);

    // Common digest lengths to detect algorithm
    private static final int MD5_HEX_LENGTH = 32;
    private static final int SHA256_HEX_LENGTH = 64;
    private static final int SHA512_HEX_LENGTH = 128;

    private DigestVerifier() {
        // Utility class
    }

    /**
     * Verifies all file digests in the given RPM package.
     *
     * @param rpmPath path to the RPM file
     * @return the verification result
     * @throws IOException if an I/O error occurs
     * @throws FormatException if the RPM file is invalid
     */
    public static @NotNull Result verify(@NotNull Path rpmPath) throws IOException, FormatException {
        RpmPackage rpm = RpmReader.read(rpmPath);
        return verify(rpmPath, rpm);
    }

    /**
     * Verifies all file digests using a pre-parsed RPM package.
     *
     * @param rpmPath path to the RPM file (for reading payload)
     * @param rpm the parsed RPM package
     * @return the verification result
     * @throws IOException if an I/O error occurs
     * @throws FormatException if the RPM payload cannot be read
     */
    public static @NotNull Result verify(@NotNull Path rpmPath, @NotNull RpmPackage rpm)
            throws IOException, FormatException {
        List<FileInfo> files = rpm.rpmMetadata().files();

        // Build lookup map of expected digests
        Map<String, String> expectedDigests = new HashMap<>();
        for (FileInfo file : files) {
            file.digest().ifPresent(digest -> {
                if (!digest.isEmpty()) {
                    expectedDigests.put(file.path(), digest);
                }
            });
        }

        if (expectedDigests.isEmpty()) {
            log.debug("No digests found in package {}", rpm.name());
            return new Result(0, 0, List.of());
        }

        // Detect digest algorithm from first digest
        String sampleDigest = expectedDigests.values().iterator().next();
        String algorithm = detectAlgorithm(sampleDigest);
        log.debug("Detected digest algorithm: {} (length {})", algorithm, sampleDigest.length());

        List<Failure> failures = new ArrayList<>();
        int verifiedCount = 0;
        int totalFiles = 0;

        try (PayloadReader reader = RpmReader.openPayload(rpmPath)) {
            PayloadEntry entry;
            while ((entry = reader.nextEntry()) != null) {
                if (entry instanceof PayloadEntry.FileEntry file) {
                    totalFiles++;
                    String path = file.path();
                    String expectedDigest = expectedDigests.get(path);

                    if (expectedDigest != null && !expectedDigest.isEmpty()) {
                        try {
                            String actualDigest = computeDigest(file.content(), algorithm);

                            if (expectedDigest.equalsIgnoreCase(actualDigest)) {
                                verifiedCount++;
                                log.trace("Verified: {}", path);
                            } else {
                                failures.add(new Failure(path, FailureReason.DIGEST_MISMATCH,
                                        String.format("expected %s, got %s", expectedDigest, actualDigest)));
                                log.warn("Digest mismatch for {}: expected {}, got {}",
                                        path, expectedDigest, actualDigest);
                            }
                        } catch (NoSuchAlgorithmException e) {
                            failures.add(new Failure(path, FailureReason.ALGORITHM_NOT_SUPPORTED,
                                    "Algorithm not supported: " + algorithm));
                        } catch (IOException e) {
                            failures.add(new Failure(path, FailureReason.READ_ERROR,
                                    "Failed to read file: " + e.getMessage()));
                        }
                    }
                }
            }
        }

        log.info("Verified {}/{} files in {}, {} failures",
                verifiedCount, totalFiles, rpm.name(), failures.size());

        return new Result(verifiedCount, totalFiles, failures);
    }

    /**
     * Verifies digests for files already extracted to disk.
     *
     * @param rpm the parsed RPM package
     * @param extractedDir the directory where files were extracted
     * @return the verification result
     * @throws IOException if an I/O error occurs
     */
    public static @NotNull Result verifyExtracted(@NotNull RpmPackage rpm, @NotNull Path extractedDir)
            throws IOException {
        List<Failure> failures = new ArrayList<>();
        int verifiedCount = 0;
        int totalFiles = 0;

        List<FileInfo> files = rpm.rpmMetadata().files();
        String algorithm = null;

        for (FileInfo file : files) {
            Optional<String> digestOpt = file.digest();
            if (digestOpt.isEmpty() || digestOpt.get().isEmpty()) {
                continue;
            }

            totalFiles++;
            String expectedDigest = digestOpt.get();

            if (algorithm == null) {
                algorithm = detectAlgorithm(expectedDigest);
            }

            // Resolve path relative to extraction directory
            String relativePath = file.path();
            if (relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1);
            }
            Path filePath = extractedDir.resolve(relativePath);

            if (!java.nio.file.Files.exists(filePath)) {
                failures.add(new Failure(file.path(), FailureReason.FILE_NOT_FOUND,
                        "File not found: " + filePath));
                continue;
            }

            if (java.nio.file.Files.isDirectory(filePath)) {
                continue; // Skip directories
            }

            try (InputStream in = java.nio.file.Files.newInputStream(filePath)) {
                String actualDigest = computeDigest(in, algorithm);

                if (expectedDigest.equalsIgnoreCase(actualDigest)) {
                    verifiedCount++;
                } else {
                    failures.add(new Failure(file.path(), FailureReason.DIGEST_MISMATCH,
                            String.format("expected %s, got %s", expectedDigest, actualDigest)));
                }
            } catch (NoSuchAlgorithmException e) {
                failures.add(new Failure(file.path(), FailureReason.ALGORITHM_NOT_SUPPORTED,
                        "Algorithm not supported: " + algorithm));
            }
        }

        return new Result(verifiedCount, totalFiles, failures);
    }

    private static @NotNull String detectAlgorithm(@NotNull String hexDigest) {
        return switch (hexDigest.length()) {
            case MD5_HEX_LENGTH -> "MD5";
            case SHA256_HEX_LENGTH -> "SHA-256";
            case SHA512_HEX_LENGTH -> "SHA-512";
            default -> "SHA-256"; // Default assumption
        };
    }

    private static @NotNull String computeDigest(@NotNull InputStream input, @NotNull String algorithm)
            throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        byte[] buffer = new byte[8192];
        int read;

        while ((read = input.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
        }

        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * The result of digest verification.
     */
    public static final class Result {
        private final int verifiedCount;
        private final int totalFiles;
        private final List<Failure> failures;

        Result(int verifiedCount, int totalFiles, @NotNull List<Failure> failures) {
            this.verifiedCount = verifiedCount;
            this.totalFiles = totalFiles;
            this.failures = List.copyOf(failures);
        }

        /**
         * Returns true if all files were verified successfully.
         *
         * @return true if no failures occurred
         */
        public boolean isValid() {
            return failures.isEmpty();
        }

        /**
         * Returns the number of files that were verified successfully.
         *
         * @return the verified file count
         */
        public int verifiedCount() {
            return verifiedCount;
        }

        /**
         * Returns the total number of files that were checked.
         *
         * @return the total file count
         */
        public int totalFiles() {
            return totalFiles;
        }

        /**
         * Returns the number of files that failed verification.
         *
         * @return the failure count
         */
        public int failureCount() {
            return failures.size();
        }

        /**
         * Returns the list of verification failures.
         *
         * @return unmodifiable list of failures
         */
        public @NotNull List<Failure> failures() {
            return failures;
        }

        @Override
        public String toString() {
            if (isValid()) {
                return String.format("DigestVerifier.Result{valid, verified=%d/%d}", verifiedCount, totalFiles);
            } else {
                return String.format("DigestVerifier.Result{invalid, verified=%d/%d, failures=%d}",
                        verifiedCount, totalFiles, failures.size());
            }
        }
    }

    /**
     * Represents a verification failure for a single file.
     */
    public record Failure(
            @NotNull String path,
            @NotNull FailureReason reason,
            @NotNull String details
    ) {
        @Override
        public String toString() {
            return path + ": " + reason + " - " + details;
        }
    }

    /**
     * Reasons why digest verification can fail.
     */
    public enum FailureReason {
        /** The computed digest did not match the expected digest */
        DIGEST_MISMATCH,
        /** The file was not found in the extracted location */
        FILE_NOT_FOUND,
        /** The digest algorithm is not supported */
        ALGORITHM_NOT_SUPPORTED,
        /** An error occurred reading the file */
        READ_ERROR
    }
}
