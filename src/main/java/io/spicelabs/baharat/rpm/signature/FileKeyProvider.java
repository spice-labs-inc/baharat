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
package io.spicelabs.baharat.rpm.signature;

import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.jcajce.JcaPGPPublicKeyRingCollection;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * A {@link KeyProvider} implementation that loads PGP public keys from
 * RPM-GPG-KEY files commonly found in /etc/pki/rpm-gpg/.
 *
 * <p>This class can load keys from:
 * <ul>
 *   <li>Individual key files (ASCII-armored or binary)</li>
 *   <li>Directories containing multiple key files</li>
 *   <li>GPG keyring files</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * // Load all keys from the standard RPM GPG key directory
 * KeyProvider keys = FileKeyProvider.fromDirectory(Path.of("/etc/pki/rpm-gpg"));
 *
 * // Load a specific key file
 * KeyProvider keys = FileKeyProvider.fromFile(Path.of("/etc/pki/rpm-gpg/RPM-GPG-KEY-fedora"));
 *
 * // Use with SignatureVerifier
 * SignatureVerifier verifier = new SignatureVerifier(keys);
 * }</pre>
 *
 * @see KeyProvider
 * @see SignatureVerifier
 */
public final class FileKeyProvider implements KeyProvider {

    private static final Logger log = LoggerFactory.getLogger(FileKeyProvider.class);

    private final Map<Long, PGPPublicKey> keys;

    private FileKeyProvider(@NotNull Map<Long, PGPPublicKey> keys) {
        this.keys = Map.copyOf(keys);
    }

    /**
     * Creates a key provider with no keys.
     *
     * @return an empty key provider
     */
    public static @NotNull FileKeyProvider empty() {
        return new FileKeyProvider(Map.of());
    }

    /**
     * Creates a key provider from a single PGP public key file.
     *
     * <p>The file can be ASCII-armored (text format starting with
     * "-----BEGIN PGP PUBLIC KEY BLOCK-----") or binary format.
     *
     * @param keyFile path to the key file
     * @return a key provider containing the keys from the file
     * @throws IOException if the file cannot be read or parsed
     */
    public static @NotNull FileKeyProvider fromFile(@NotNull Path keyFile) throws IOException {
        Map<Long, PGPPublicKey> keys = new HashMap<>();
        loadKeysFromFile(keyFile, keys);
        log.info("Loaded {} keys from {}", keys.size(), keyFile);
        return new FileKeyProvider(keys);
    }

    /**
     * Creates a key provider from all key files in a directory.
     *
     * <p>This method scans the directory for files matching common GPG key
     * naming patterns (RPM-GPG-KEY-*, *.gpg, *.asc) and loads all keys found.
     *
     * @param directory path to the directory containing key files
     * @return a key provider containing all keys found
     * @throws IOException if the directory cannot be read
     */
    public static @NotNull FileKeyProvider fromDirectory(@NotNull Path directory) throws IOException {
        Map<Long, PGPPublicKey> keys = new HashMap<>();

        if (!Files.isDirectory(directory)) {
            log.warn("Not a directory: {}", directory);
            return new FileKeyProvider(keys);
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path file : stream) {
                if (isKeyFile(file)) {
                    try {
                        loadKeysFromFile(file, keys);
                    } catch (Exception e) {
                        log.debug("Skipping file {} - not a valid key file: {}", file, e.getMessage());
                    }
                }
            }
        }

        log.info("Loaded {} keys from directory {}", keys.size(), directory);
        return new FileKeyProvider(keys);
    }

    /**
     * Creates a key provider from multiple sources.
     *
     * @param paths paths to key files or directories
     * @return a key provider containing all keys found
     * @throws IOException if any path cannot be read
     */
    public static @NotNull FileKeyProvider fromPaths(@NotNull Path... paths) throws IOException {
        Map<Long, PGPPublicKey> keys = new HashMap<>();

        for (Path path : paths) {
            if (Files.isDirectory(path)) {
                FileKeyProvider dirProvider = fromDirectory(path);
                keys.putAll(dirProvider.keys);
            } else if (Files.isRegularFile(path)) {
                loadKeysFromFile(path, keys);
            }
        }

        return new FileKeyProvider(keys);
    }

    /**
     * Creates a builder for constructing a key provider with custom configuration.
     *
     * @return a new builder
     */
    public static @NotNull Builder builder() {
        return new Builder();
    }

    @Override
    public @NotNull Optional<PGPPublicKey> getKey(long keyId) {
        // First try exact match
        PGPPublicKey key = keys.get(keyId);
        if (key != null) {
            return Optional.of(key);
        }

        // Try matching by short key ID (last 32 bits)
        long shortKeyId = keyId & 0xFFFFFFFFL;
        for (Map.Entry<Long, PGPPublicKey> entry : keys.entrySet()) {
            if ((entry.getKey() & 0xFFFFFFFFL) == shortKeyId) {
                return Optional.of(entry.getValue());
            }
        }

        return Optional.empty();
    }

    /**
     * Returns the number of keys in this provider.
     *
     * @return the key count
     */
    public int keyCount() {
        return keys.size();
    }

    /**
     * Returns all key IDs in this provider.
     *
     * @return iterable of key IDs
     */
    public @NotNull Iterable<Long> keyIds() {
        return keys.keySet();
    }

    /**
     * Checks if this provider contains a key with the given ID.
     *
     * @param keyId the key ID to check
     * @return true if the key is present
     */
    public boolean hasKey(long keyId) {
        return getKey(keyId).isPresent();
    }

    private static void loadKeysFromFile(@NotNull Path file, @NotNull Map<Long, PGPPublicKey> keys)
            throws IOException {
        try (InputStream fileIn = Files.newInputStream(file);
             BufferedInputStream bufferedIn = new BufferedInputStream(fileIn);
             InputStream decoderIn = PGPUtil.getDecoderStream(bufferedIn)) {

            PGPPublicKeyRingCollection keyRings = new JcaPGPPublicKeyRingCollection(decoderIn);

            Iterator<PGPPublicKeyRing> ringIterator = keyRings.getKeyRings();
            while (ringIterator.hasNext()) {
                PGPPublicKeyRing ring = ringIterator.next();
                Iterator<PGPPublicKey> keyIterator = ring.getPublicKeys();
                while (keyIterator.hasNext()) {
                    PGPPublicKey key = keyIterator.next();
                    keys.put(key.getKeyID(), key);
                    log.debug("Loaded key ID {} from {}", String.format("%016X", key.getKeyID()), file);
                }
            }
        } catch (org.bouncycastle.openpgp.PGPException e) {
            throw new IOException("Failed to parse PGP key file: " + file, e);
        }
    }

    private static boolean isKeyFile(@NotNull Path file) {
        if (!Files.isRegularFile(file)) {
            return false;
        }

        String fileName = file.getFileName().toString();
        return fileName.startsWith("RPM-GPG-KEY")
                || fileName.endsWith(".gpg")
                || fileName.endsWith(".asc")
                || fileName.endsWith(".key")
                || fileName.startsWith("GPG-KEY");
    }

    /**
     * Builder for constructing FileKeyProvider instances.
     */
    public static final class Builder {
        private final Map<Long, PGPPublicKey> keys = new HashMap<>();

        private Builder() {}

        /**
         * Adds keys from a file.
         *
         * @param file path to a key file
         * @return this builder
         * @throws IOException if the file cannot be read
         */
        public @NotNull Builder addFile(@NotNull Path file) throws IOException {
            loadKeysFromFile(file, keys);
            return this;
        }

        /**
         * Adds keys from all files in a directory.
         *
         * @param directory path to a directory
         * @return this builder
         * @throws IOException if the directory cannot be read
         */
        public @NotNull Builder addDirectory(@NotNull Path directory) throws IOException {
            if (Files.isDirectory(directory)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
                    for (Path file : stream) {
                        if (isKeyFile(file)) {
                            try {
                                loadKeysFromFile(file, keys);
                            } catch (Exception e) {
                                log.debug("Skipping {}: {}", file, e.getMessage());
                            }
                        }
                    }
                }
            }
            return this;
        }

        /**
         * Adds a single PGP public key.
         *
         * @param key the key to add
         * @return this builder
         */
        public @NotNull Builder addKey(@NotNull PGPPublicKey key) {
            keys.put(key.getKeyID(), key);
            return this;
        }

        /**
         * Builds the key provider.
         *
         * @return the constructed key provider
         */
        public @NotNull FileKeyProvider build() {
            return new FileKeyProvider(keys);
        }
    }
}
