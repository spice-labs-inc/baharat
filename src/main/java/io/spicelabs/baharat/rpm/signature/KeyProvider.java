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

import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * Interface for providing public keys for signature verification.
 * Implementations can load keys from various sources (files, keyrings, key servers).
 */
public interface KeyProvider {

    /**
     * Retrieves a public key by its key ID.
     *
     * @param keyId the key ID (lower 64 bits of the key fingerprint)
     * @return an Optional containing the public key, or empty if not found
     */
    @NotNull Optional<PGPPublicKey> getKey(long keyId);

    /**
     * Creates a key provider that loads keys from a PGP keyring file.
     *
     * @param keyringPath the path to the keyring file (ASCII-armored or binary)
     * @return a new key provider
     * @throws IOException if the keyring cannot be read
     * @throws PGPException if the keyring format is invalid
     */
    static @NotNull KeyProvider fromKeyring(@NotNull Path keyringPath) throws IOException, PGPException {
        try (InputStream in = Files.newInputStream(keyringPath);
             InputStream decoderStream = PGPUtil.getDecoderStream(in)) {

            PGPPublicKeyRingCollection keyRingCollection =
                    new PGPPublicKeyRingCollection(decoderStream, new JcaKeyFingerprintCalculator());

            Map<Long, PGPPublicKey> keys = new HashMap<>();

            Iterator<PGPPublicKeyRing> keyRings = keyRingCollection.getKeyRings();
            while (keyRings.hasNext()) {
                PGPPublicKeyRing keyRing = keyRings.next();
                Iterator<PGPPublicKey> publicKeys = keyRing.getPublicKeys();
                while (publicKeys.hasNext()) {
                    PGPPublicKey key = publicKeys.next();
                    keys.put(key.getKeyID(), key);
                }
            }

            return keyId -> Optional.ofNullable(keys.get(keyId));
        }
    }

    /**
     * Creates a key provider that loads a single key from a file.
     *
     * @param keyPath the path to the public key file
     * @return a new key provider
     * @throws IOException if the key cannot be read
     */
    static @NotNull KeyProvider fromKeyFile(@NotNull Path keyPath) throws IOException {
        try (InputStream in = Files.newInputStream(keyPath);
             InputStream decoderStream = PGPUtil.getDecoderStream(in)) {

            PGPPublicKeyRing keyRing = new PGPPublicKeyRing(decoderStream, new JcaKeyFingerprintCalculator());

            Map<Long, PGPPublicKey> keys = new HashMap<>();
            Iterator<PGPPublicKey> publicKeys = keyRing.getPublicKeys();
            while (publicKeys.hasNext()) {
                PGPPublicKey key = publicKeys.next();
                keys.put(key.getKeyID(), key);
            }

            return keyId -> Optional.ofNullable(keys.get(keyId));
        }
    }

    /**
     * Creates a key provider that combines multiple key providers.
     * Keys are looked up in order, and the first match is returned.
     *
     * @param providers the key providers to combine
     * @return a new combined key provider
     */
    static @NotNull KeyProvider combine(@NotNull KeyProvider @NotNull ... providers) {
        return keyId -> {
            for (KeyProvider provider : providers) {
                Optional<PGPPublicKey> key = provider.getKey(keyId);
                if (key.isPresent()) {
                    return key;
                }
            }
            return Optional.empty();
        };
    }

    /**
     * Creates an empty key provider that never returns any keys.
     *
     * @return an empty key provider
     */
    static @NotNull KeyProvider empty() {
        return keyId -> Optional.empty();
    }
}
