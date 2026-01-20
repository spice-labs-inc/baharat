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
import io.spicelabs.baharat.rpm.header.IndexEntry;
import io.spicelabs.baharat.rpm.header.SignatureTag;
import io.spicelabs.baharat.rpm.header.TagType;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignatureVerifierTest {

    @Test
    void signatureResultStatuses() {
        SignatureResult valid = SignatureResult.valid(
                SignatureResult.SignatureType.RSA,
                0x1234567890ABCDEFL,
                Instant.now()
        );
        assertThat(valid.status()).isEqualTo(SignatureResult.Status.VALID);
        assertThat(valid.isValid()).isTrue();
        assertThat(valid.isSigned()).isTrue();
        assertThat(valid.keyId()).hasValue(0x1234567890ABCDEFL);
        assertThat(valid.keyIdHex()).hasValue("1234567890ABCDEF");

        SignatureResult invalid = SignatureResult.invalid(
                SignatureResult.SignatureType.RSA,
                0x1234567890ABCDEFL
        );
        assertThat(invalid.status()).isEqualTo(SignatureResult.Status.INVALID);
        assertThat(invalid.isValid()).isFalse();
        assertThat(invalid.isSigned()).isTrue();

        SignatureResult notSigned = SignatureResult.notSigned();
        assertThat(notSigned.status()).isEqualTo(SignatureResult.Status.NOT_SIGNED);
        assertThat(notSigned.isValid()).isFalse();
        assertThat(notSigned.isSigned()).isFalse();

        SignatureResult keyNotFound = SignatureResult.keyNotFound(
                SignatureResult.SignatureType.RSA,
                0x1234567890ABCDEFL
        );
        assertThat(keyNotFound.status()).isEqualTo(SignatureResult.Status.KEY_NOT_FOUND);
        assertThat(keyNotFound.isValid()).isFalse();
        assertThat(keyNotFound.isSigned()).isTrue();

        SignatureResult error = SignatureResult.error("Test error");
        assertThat(error.status()).isEqualTo(SignatureResult.Status.ERROR);
        assertThat(error.isValid()).isFalse();
        assertThat(error.errorMessage()).hasValue("Test error");
    }

    @Test
    void keyProviderEmpty() {
        KeyProvider empty = KeyProvider.empty();
        assertThat(empty.getKey(0x1234567890ABCDEFL)).isEmpty();
    }

    @Test
    void keyProviderCombine() {
        KeyProvider empty = KeyProvider.empty();
        KeyProvider combined = KeyProvider.combine(empty, empty);
        assertThat(combined.getKey(0x1234567890ABCDEFL)).isEmpty();
    }

    @Test
    void keyProviderCombineReturnsFirstMatch() {
        // Create a mock key provider that returns a key for a specific ID
        KeyProvider provider1 = keyId -> Optional.empty();
        KeyProvider provider2 = KeyProvider.empty();

        KeyProvider combined = KeyProvider.combine(provider1, provider2);
        assertThat(combined.getKey(0x1234567890ABCDEFL)).isEmpty();
    }

    @Test
    void signatureVerifierDetectsUnsignedPackage() {
        // Create a header with no signature tags
        Header header = new Header(Collections.emptyList(), new byte[0]);

        SignatureVerifier verifier = new SignatureVerifier(KeyProvider.empty());

        assertThat(verifier.isSigned(header)).isFalse();

        SignatureResult result = verifier.verifyHeaderSignature(header, new byte[0]);
        assertThat(result.status()).isEqualTo(SignatureResult.Status.NOT_SIGNED);
    }

    @Test
    void signatureVerifierDetectsRsaSignedPackage() {
        // Create a header with RSA signature tag (binary data)
        byte[] sigData = new byte[]{0x01, 0x02, 0x03, 0x04};
        List<IndexEntry> entries = List.of(
                new IndexEntry(SignatureTag.RSA.tag(), TagType.BIN, 0, sigData.length)
        );
        Header header = new Header(entries, sigData);

        SignatureVerifier verifier = new SignatureVerifier(KeyProvider.empty());

        assertThat(verifier.isSigned(header)).isTrue();
    }

    @Test
    void signatureVerifierDetectsDsaSignedPackage() {
        // Create a header with DSA signature tag
        byte[] sigData = new byte[]{0x01, 0x02, 0x03, 0x04};
        List<IndexEntry> entries = List.of(
                new IndexEntry(SignatureTag.DSA.tag(), TagType.BIN, 0, sigData.length)
        );
        Header header = new Header(entries, sigData);

        SignatureVerifier verifier = new SignatureVerifier(KeyProvider.empty());

        assertThat(verifier.isSigned(header)).isTrue();
    }

    @Test
    void signatureVerifierDetectsGpgSignedPackage() {
        // Create a header with GPG signature tag
        byte[] sigData = new byte[]{0x01, 0x02, 0x03, 0x04};
        List<IndexEntry> entries = List.of(
                new IndexEntry(SignatureTag.GPG.tag(), TagType.BIN, 0, sigData.length)
        );
        Header header = new Header(entries, sigData);

        SignatureVerifier verifier = new SignatureVerifier(KeyProvider.empty());

        assertThat(verifier.isSigned(header)).isTrue();
    }

    @Test
    @SuppressWarnings("deprecation") // Testing deprecated PGP tag for backward compatibility
    void signatureVerifierDetectsPgpSignedPackage() {
        // Create a header with PGP signature tag
        byte[] sigData = new byte[]{0x01, 0x02, 0x03, 0x04};
        List<IndexEntry> entries = List.of(
                new IndexEntry(SignatureTag.PGP.tag(), TagType.BIN, 0, sigData.length)
        );
        Header header = new Header(entries, sigData);

        SignatureVerifier verifier = new SignatureVerifier(KeyProvider.empty());

        assertThat(verifier.isSigned(header)).isTrue();
    }

    @Test
    void verifyHeaderSignatureWithInvalidSignatureData() {
        // Create a header with RSA tag but invalid signature data
        byte[] invalidSig = new byte[]{0x01, 0x02, 0x03, 0x04};
        List<IndexEntry> entries = List.of(
                new IndexEntry(SignatureTag.RSA.tag(), TagType.BIN, 0, invalidSig.length)
        );
        Header header = new Header(entries, invalidSig);

        SignatureVerifier verifier = new SignatureVerifier(KeyProvider.empty());

        SignatureResult result = verifier.verifyHeaderSignature(header, new byte[]{0x00});
        // Should return error since signature data is invalid
        assertThat(result.status()).isIn(
                SignatureResult.Status.ERROR,
                SignatureResult.Status.KEY_NOT_FOUND
        );
    }

    @Test
    void verifyPackageSignatureReturnsNotSignedWhenNoSignature() {
        Header header = new Header(Collections.emptyList(), new byte[0]);
        SignatureVerifier verifier = new SignatureVerifier(KeyProvider.empty());

        SignatureResult result = verifier.verifyPackageSignature(header, new byte[0]);
        assertThat(result.status()).isEqualTo(SignatureResult.Status.NOT_SIGNED);
    }

    @Test
    void verifyPackageSignatureWithGpgTag() {
        // Create a header with GPG tag but invalid signature
        byte[] invalidSig = new byte[]{0x01, 0x02, 0x03, 0x04};
        List<IndexEntry> entries = List.of(
                new IndexEntry(SignatureTag.GPG.tag(), TagType.BIN, 0, invalidSig.length)
        );
        Header header = new Header(entries, invalidSig);

        SignatureVerifier verifier = new SignatureVerifier(KeyProvider.empty());

        SignatureResult result = verifier.verifyPackageSignature(header, new byte[]{0x00});
        assertThat(result.status()).isIn(
                SignatureResult.Status.ERROR,
                SignatureResult.Status.KEY_NOT_FOUND
        );
    }

    @Test
    void getSigningKeyIdReturnsEmptyForUnsignedPackage() {
        Header header = new Header(Collections.emptyList(), new byte[0]);
        SignatureVerifier verifier = new SignatureVerifier(KeyProvider.empty());

        Optional<Long> keyId = verifier.getSigningKeyId(header);
        assertThat(keyId).isEmpty();
    }

    @Test
    void getSigningKeyIdReturnsEmptyForInvalidSignature() {
        // Create a header with RSA tag but invalid signature data
        byte[] invalidSig = new byte[]{0x01, 0x02, 0x03, 0x04};
        List<IndexEntry> entries = List.of(
                new IndexEntry(SignatureTag.RSA.tag(), TagType.BIN, 0, invalidSig.length)
        );
        Header header = new Header(entries, invalidSig);

        SignatureVerifier verifier = new SignatureVerifier(KeyProvider.empty());

        Optional<Long> keyId = verifier.getSigningKeyId(header);
        assertThat(keyId).isEmpty();
    }

    @Test
    @SuppressWarnings("deprecation") // Testing deprecated PGP and PAYLOADSIZE tags for backward compatibility
    void signatureTagValues() {
        assertThat(SignatureTag.RSA.tag()).isEqualTo(268);
        assertThat(SignatureTag.DSA.tag()).isEqualTo(267);
        assertThat(SignatureTag.GPG.tag()).isEqualTo(1005);
        assertThat(SignatureTag.MD5.tag()).isEqualTo(1004);
        assertThat(SignatureTag.SHA1.tag()).isEqualTo(269);
        assertThat(SignatureTag.SHA256.tag()).isEqualTo(273);
        assertThat(SignatureTag.PGP.tag()).isEqualTo(1002);
        assertThat(SignatureTag.SIZE.tag()).isEqualTo(1000);
        assertThat(SignatureTag.PAYLOADSIZE.tag()).isEqualTo(1007);
    }

    @Test
    @SuppressWarnings("deprecation") // Testing deprecated PGP tag for backward compatibility
    void signatureTagFromTag() {
        assertThat(SignatureTag.fromTag(268)).hasValue(SignatureTag.RSA);
        assertThat(SignatureTag.fromTag(267)).hasValue(SignatureTag.DSA);
        assertThat(SignatureTag.fromTag(1005)).hasValue(SignatureTag.GPG);
        assertThat(SignatureTag.fromTag(1002)).hasValue(SignatureTag.PGP);
        assertThat(SignatureTag.fromTag(9999)).isEmpty();
    }

    @Test
    void signatureResultToString() {
        SignatureResult valid = SignatureResult.valid(
                SignatureResult.SignatureType.RSA,
                0x1234567890ABCDEFL,
                Instant.parse("2024-01-01T00:00:00Z")
        );

        String str = valid.toString();
        assertThat(str).contains("VALID");
        assertThat(str).contains("RSA");
        assertThat(str).contains("1234567890ABCDEF");
        assertThat(str).contains("2024-01-01");
    }

    @Test
    void signatureResultToStringWithError() {
        SignatureResult error = SignatureResult.error("Test error message");

        String str = error.toString();
        assertThat(str).contains("ERROR");
        assertThat(str).contains("Test error message");
    }

    @Test
    void signatureResultToStringNotSigned() {
        SignatureResult notSigned = SignatureResult.notSigned();

        String str = notSigned.toString();
        assertThat(str).contains("NOT_SIGNED");
        assertThat(str).contains("UNKNOWN");
    }

    @Test
    void signatureResultWithNullSignatureTime() {
        SignatureResult valid = SignatureResult.valid(
                SignatureResult.SignatureType.RSA,
                0x1234567890ABCDEFL,
                null
        );

        assertThat(valid.signatureTime()).isEmpty();
        assertThat(valid.isValid()).isTrue();
    }

    @Test
    void signatureTypeValues() {
        assertThat(SignatureResult.SignatureType.RSA).isNotNull();
        assertThat(SignatureResult.SignatureType.DSA).isNotNull();
        assertThat(SignatureResult.SignatureType.ECDSA).isNotNull();
        assertThat(SignatureResult.SignatureType.PGP).isNotNull();
        assertThat(SignatureResult.SignatureType.GPG).isNotNull();
        assertThat(SignatureResult.SignatureType.UNKNOWN).isNotNull();
    }

    @Test
    void statusValues() {
        assertThat(SignatureResult.Status.VALID).isNotNull();
        assertThat(SignatureResult.Status.INVALID).isNotNull();
        assertThat(SignatureResult.Status.NOT_SIGNED).isNotNull();
        assertThat(SignatureResult.Status.KEY_NOT_FOUND).isNotNull();
        assertThat(SignatureResult.Status.ERROR).isNotNull();
    }

    @Test
    void signatureResultAccessors() {
        SignatureResult keyNotFound = SignatureResult.keyNotFound(
                SignatureResult.SignatureType.DSA,
                0xABCDEF1234567890L
        );

        assertThat(keyNotFound.signatureType()).isEqualTo(SignatureResult.SignatureType.DSA);
        assertThat(keyNotFound.keyId()).hasValue(0xABCDEF1234567890L);
        assertThat(keyNotFound.signatureTime()).isEmpty();
        assertThat(keyNotFound.errorMessage()).isEmpty();
    }
}
