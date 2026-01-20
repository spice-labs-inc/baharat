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

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Optional;

/**
 * Represents the result of a signature verification operation.
 */
public final class SignatureResult {

    /**
     * The status of signature verification.
     */
    public enum Status {
        /**
         * The signature is valid and verified successfully.
         */
        VALID,

        /**
         * The signature is invalid (data was modified or signature corrupted).
         */
        INVALID,

        /**
         * The package is not signed.
         */
        NOT_SIGNED,

        /**
         * The public key needed for verification was not found.
         */
        KEY_NOT_FOUND,

        /**
         * An error occurred during verification.
         */
        ERROR
    }

    /**
     * The type of signature that was verified.
     */
    public enum SignatureType {
        /**
         * RSA signature.
         */
        RSA,

        /**
         * DSA signature.
         */
        DSA,

        /**
         * ECDSA signature.
         */
        ECDSA,

        /**
         * Legacy PGP signature.
         */
        PGP,

        /**
         * GPG signature (header + payload).
         */
        GPG,

        /**
         * Unknown signature type.
         */
        UNKNOWN
    }

    private final @NotNull Status status;
    private final @NotNull SignatureType signatureType;
    private final @NotNull Optional<Long> keyId;
    private final @NotNull Optional<Instant> signatureTime;
    private final @NotNull Optional<String> errorMessage;

    private SignatureResult(@NotNull Status status, @NotNull SignatureType signatureType,
                            @NotNull Optional<Long> keyId, @NotNull Optional<Instant> signatureTime,
                            @NotNull Optional<String> errorMessage) {
        this.status = status;
        this.signatureType = signatureType;
        this.keyId = keyId;
        this.signatureTime = signatureTime;
        this.errorMessage = errorMessage;
    }

    /**
     * Creates a result for a valid signature.
     *
     * @param signatureType the type of signature
     * @param keyId the key ID used for signing
     * @param signatureTime the time the signature was created
     * @return the verification result
     */
    public static @NotNull SignatureResult valid(@NotNull SignatureType signatureType, long keyId, Instant signatureTime) {
        return new SignatureResult(Status.VALID, signatureType,
                Optional.of(keyId), Optional.ofNullable(signatureTime), Optional.empty());
    }

    /**
     * Creates a result for an invalid signature.
     *
     * @param signatureType the type of signature
     * @param keyId the key ID used for signing
     * @return the verification result
     */
    public static @NotNull SignatureResult invalid(@NotNull SignatureType signatureType, long keyId) {
        return new SignatureResult(Status.INVALID, signatureType,
                Optional.of(keyId), Optional.empty(), Optional.empty());
    }

    /**
     * Creates a result indicating the package is not signed.
     *
     * @return the verification result
     */
    public static @NotNull SignatureResult notSigned() {
        return new SignatureResult(Status.NOT_SIGNED, SignatureType.UNKNOWN,
                Optional.empty(), Optional.empty(), Optional.empty());
    }

    /**
     * Creates a result indicating the signing key was not found.
     *
     * @param signatureType the type of signature
     * @param keyId the key ID that was not found
     * @return the verification result
     */
    public static @NotNull SignatureResult keyNotFound(@NotNull SignatureType signatureType, long keyId) {
        return new SignatureResult(Status.KEY_NOT_FOUND, signatureType,
                Optional.of(keyId), Optional.empty(), Optional.empty());
    }

    /**
     * Creates a result for an error during verification.
     *
     * @param errorMessage the error message
     * @return the verification result
     */
    public static @NotNull SignatureResult error(@NotNull String errorMessage) {
        return new SignatureResult(Status.ERROR, SignatureType.UNKNOWN,
                Optional.empty(), Optional.empty(), Optional.of(errorMessage));
    }

    /**
     * Returns the verification status.
     *
     * @return the status
     */
    public @NotNull Status status() {
        return status;
    }

    /**
     * Returns the signature type.
     *
     * @return the signature type
     */
    public @NotNull SignatureType signatureType() {
        return signatureType;
    }

    /**
     * Returns the key ID used for signing.
     *
     * @return an Optional containing the key ID, or empty if not applicable
     */
    public @NotNull Optional<Long> keyId() {
        return keyId;
    }

    /**
     * Returns the key ID as a hex string.
     *
     * @return an Optional containing the hex key ID, or empty if not applicable
     */
    public @NotNull Optional<String> keyIdHex() {
        return keyId.map(id -> String.format("%016X", id));
    }

    /**
     * Returns the time the signature was created.
     *
     * @return an Optional containing the signature time, or empty if not available
     */
    public @NotNull Optional<Instant> signatureTime() {
        return signatureTime;
    }

    /**
     * Returns the error message if an error occurred.
     *
     * @return an Optional containing the error message, or empty if no error
     */
    public @NotNull Optional<String> errorMessage() {
        return errorMessage;
    }

    /**
     * Returns true if the signature is valid.
     *
     * @return true if valid
     */
    public boolean isValid() {
        return status == Status.VALID;
    }

    /**
     * Returns true if the package is signed (regardless of verification status).
     *
     * @return true if signed
     */
    public boolean isSigned() {
        return status != Status.NOT_SIGNED;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("SignatureResult{status=");
        sb.append(status);
        sb.append(", type=").append(signatureType);
        keyId.ifPresent(id -> sb.append(", keyId=").append(String.format("%016X", id)));
        signatureTime.ifPresent(t -> sb.append(", time=").append(t));
        errorMessage.ifPresent(e -> sb.append(", error=").append(e));
        sb.append("}");
        return sb.toString();
    }
}
