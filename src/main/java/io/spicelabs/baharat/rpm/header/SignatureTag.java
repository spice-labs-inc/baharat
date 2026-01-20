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
package io.spicelabs.baharat.rpm.header;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Enumeration of RPM signature header tags.
 * These tags are found in the signature header section and contain
 * cryptographic signatures and integrity checks.
 */
public enum SignatureTag {

    /**
     * Header + payload size (INT32).
     */
    SIZE(1000),

    /**
     * Uncompressed payload size in bytes (INT32).
     * @deprecated Use LONGARCHIVESIZE for sizes > 4GB.
     */
    @Deprecated
    PAYLOADSIZE(1007),

    /**
     * Header SHA1 digest (STRING).
     */
    SHA1(269),

    /**
     * Header SHA256 digest (STRING).
     */
    SHA256(273),

    /**
     * MD5 digest of header + payload (BIN, 16 bytes).
     */
    MD5(1004),

    /**
     * DSA signature of header (BIN).
     */
    DSA(267),

    /**
     * RSA signature of header (BIN).
     */
    RSA(268),

    /**
     * PGP signature of header + payload (BIN).
     * @deprecated Use RSA instead.
     */
    @Deprecated
    PGP(1002),

    /**
     * GPG signature of header + payload (BIN).
     */
    GPG(1005),

    /**
     * Reserved space for signatures (BIN).
     */
    RESERVEDSPACE(1008),

    /**
     * Header + payload size for packages > 4GB (INT64).
     */
    LONGSIZE(270),

    /**
     * Uncompressed payload size for packages > 4GB (INT64).
     */
    LONGARCHIVESIZE(271),

    /**
     * File digest algorithm (INT32).
     */
    FILEDIGESTALGO(272),

    /**
     * Header region tag (BIN).
     */
    HEADERSIGNATURES(62),

    /**
     * VERITYSIGNATURES - dm-verity signature (STRING).
     */
    VERITYSIGNATURES(276),

    /**
     * VERITYSIGNATUREALGO - dm-verity algorithm (INT32).
     */
    VERITYSIGNATUREALGO(277);

    private final int tag;

    SignatureTag(int tag) {
        this.tag = tag;
    }

    /**
     * Returns the numeric tag value.
     *
     * @return the tag number
     */
    public int tag() {
        return tag;
    }

    /**
     * Looks up a signature tag by its numeric value.
     *
     * @param tag the tag number
     * @return an Optional containing the tag, or empty if unknown
     */
    public static @NotNull Optional<SignatureTag> fromTag(int tag) {
        for (SignatureTag sigTag : values()) {
            if (sigTag.tag == tag) {
                return Optional.of(sigTag);
            }
        }
        return Optional.empty();
    }
}
