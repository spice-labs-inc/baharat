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

import io.spicelabs.baharat.rpm.header.Header;
import io.spicelabs.baharat.rpm.header.SignatureTag;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider;

import java.io.ByteArrayInputStream;
import java.security.Security;
import java.time.Instant;
import java.util.Optional;

/**
 * Verifies RPM package signatures using PGP/GPG keys.
 *
 * <p>RPM packages can contain multiple types of signatures:
 * <ul>
 *   <li><b>RSA/DSA (header signature):</b> Signs only the main header, faster to verify</li>
 *   <li><b>GPG/PGP (package signature):</b> Signs header + payload, verifies entire content</li>
 * </ul>
 *
 * <p>To verify signatures, you must provide a {@link KeyProvider} that can look up
 * the public keys used for signing. Keys can be loaded from:
 * <ul>
 *   <li>RPM keyring files (usually in /etc/pki/rpm-gpg/)</li>
 *   <li>GPG keyring</li>
 *   <li>Custom key store</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * KeyProvider keys = keyId -> loadKeyFromKeyring(keyId);
 * SignatureVerifier verifier = new SignatureVerifier(keys);
 *
 * RpmPackage rpm = RpmReader.read(path);
 * SignatureResult result = verifier.verifyHeaderSignature(
 *     rpm.signatureHeader(), headerBytes);
 *
 * if (result.isValid()) {
 *     System.out.println("Signature valid, key: " + result.keyIdHex().orElse(""));
 * }
 * }</pre>
 *
 * @see KeyProvider
 * @see SignatureResult
 */
public final class SignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(SignatureVerifier.class);

    static {
        // Register Bouncy Castle provider if not already registered
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
            log.debug("Registered Bouncy Castle security provider");
        }
    }

    private final @NotNull KeyProvider keyProvider;

    /**
     * Creates a new signature verifier with the given key provider.
     *
     * @param keyProvider the provider for public keys
     */
    public SignatureVerifier(@NotNull KeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    /**
     * Verifies the RSA header signature.
     *
     * @param signatureHeader the signature header
     * @param headerBytes the raw header bytes that were signed
     * @return the verification result
     */
    public @NotNull SignatureResult verifyHeaderSignature(@NotNull Header signatureHeader, byte @NotNull [] headerBytes) {
        // Try RSA signature first
        Optional<byte[]> rsaSig = signatureHeader.getBinary(SignatureTag.RSA.tag());
        if (rsaSig.isPresent()) {
            return verifyPgpSignature(rsaSig.get(), headerBytes, SignatureResult.SignatureType.RSA);
        }

        // Try DSA signature
        Optional<byte[]> dsaSig = signatureHeader.getBinary(SignatureTag.DSA.tag());
        if (dsaSig.isPresent()) {
            return verifyPgpSignature(dsaSig.get(), headerBytes, SignatureResult.SignatureType.DSA);
        }

        return SignatureResult.notSigned();
    }

    /**
     * Verifies the GPG header + payload signature.
     *
     * @param signatureHeader the signature header
     * @param headerAndPayloadBytes the raw header + payload bytes that were signed
     * @return the verification result
     */
    public @NotNull SignatureResult verifyPackageSignature(@NotNull Header signatureHeader, byte @NotNull [] headerAndPayloadBytes) {
        // Try GPG signature (covers header + payload)
        Optional<byte[]> gpgSig = signatureHeader.getBinary(SignatureTag.GPG.tag());
        if (gpgSig.isPresent()) {
            return verifyPgpSignature(gpgSig.get(), headerAndPayloadBytes, SignatureResult.SignatureType.GPG);
        }

        // Try legacy PGP signature
        @SuppressWarnings("deprecation")
        Optional<byte[]> pgpSig = signatureHeader.getBinary(SignatureTag.PGP.tag());
        if (pgpSig.isPresent()) {
            return verifyPgpSignature(pgpSig.get(), headerAndPayloadBytes, SignatureResult.SignatureType.PGP);
        }

        return SignatureResult.notSigned();
    }

    /**
     * Checks if the package has any signatures.
     *
     * @param signatureHeader the signature header
     * @return true if the package is signed
     */
    @SuppressWarnings("deprecation") // PGP is deprecated but we still support legacy packages
    public boolean isSigned(@NotNull Header signatureHeader) {
        return signatureHeader.hasTag(SignatureTag.RSA.tag())
                || signatureHeader.hasTag(SignatureTag.DSA.tag())
                || signatureHeader.hasTag(SignatureTag.GPG.tag())
                || signatureHeader.hasTag(SignatureTag.PGP.tag());
    }

    /**
     * Gets the key ID of the signing key without full verification.
     *
     * @param signatureHeader the signature header
     * @return an Optional containing the key ID, or empty if not signed
     */
    public @NotNull Optional<Long> getSigningKeyId(@NotNull Header signatureHeader) {
        byte[] sigBytes = null;

        Optional<byte[]> rsaSig = signatureHeader.getBinary(SignatureTag.RSA.tag());
        if (rsaSig.isPresent()) {
            sigBytes = rsaSig.get();
        } else {
            Optional<byte[]> dsaSig = signatureHeader.getBinary(SignatureTag.DSA.tag());
            if (dsaSig.isPresent()) {
                sigBytes = dsaSig.get();
            } else {
                Optional<byte[]> gpgSig = signatureHeader.getBinary(SignatureTag.GPG.tag());
                if (gpgSig.isPresent()) {
                    sigBytes = gpgSig.get();
                }
            }
        }

        if (sigBytes == null) {
            return Optional.empty();
        }

        try {
            PGPSignature signature = extractSignature(sigBytes);
            if (signature != null) {
                return Optional.of(signature.getKeyID());
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }

        return Optional.empty();
    }

    private @NotNull SignatureResult verifyPgpSignature(byte @NotNull [] signatureBytes, byte @NotNull [] data,
                                                @NotNull SignatureResult.SignatureType type) {
        log.debug("Verifying {} signature ({} bytes signature, {} bytes data)",
                type, signatureBytes.length, data.length);
        try {
            PGPSignature signature = extractSignature(signatureBytes);
            if (signature == null) {
                // Security: Malformed signature is treated as invalid, not an error
                // This distinguishes between "no signature" and "malformed signature"
                log.warn("Failed to parse {} signature - treating as invalid", type);
                return SignatureResult.invalid(type, 0L);
            }

            long keyId = signature.getKeyID();
            log.debug("Signature uses key ID: {}", String.format("%016X", keyId));
            Optional<PGPPublicKey> keyOpt = keyProvider.getKey(keyId);

            if (keyOpt.isEmpty()) {
                log.warn("Signing key not found: {}", String.format("%016X", keyId));
                return SignatureResult.keyNotFound(type, keyId);
            }

            PGPPublicKey key = keyOpt.get();
            signature.init(new JcaPGPContentVerifierBuilderProvider().setProvider("BC"), key);
            signature.update(data);

            if (signature.verify()) {
                Instant sigTime = signature.getCreationTime() != null
                        ? signature.getCreationTime().toInstant()
                        : null;
                log.info("Signature verification successful: type={}, keyId={}",
                        type, String.format("%016X", keyId));
                return SignatureResult.valid(type, keyId, sigTime);
            } else {
                log.warn("Signature verification failed: type={}, keyId={}",
                        type, String.format("%016X", keyId));
                return SignatureResult.invalid(type, keyId);
            }

        } catch (Exception e) {
            // Security: Don't expose internal exception details in error messages
            // Log the full error for debugging, but return a sanitized message
            log.error("Signature verification error: {}", e.getMessage(), e);
            return SignatureResult.error("Signature verification failed");
        }
    }

    private PGPSignature extractSignature(byte[] signatureBytes) throws Exception {
        try (ByteArrayInputStream in = new ByteArrayInputStream(signatureBytes)) {
            JcaPGPObjectFactory factory = new JcaPGPObjectFactory(in);
            Object obj = factory.nextObject();

            if (obj instanceof PGPSignatureList sigList) {
                if (!sigList.isEmpty()) {
                    return sigList.get(0);
                }
            } else if (obj instanceof PGPSignature sig) {
                return sig;
            }
        }
        return null;
    }
}
