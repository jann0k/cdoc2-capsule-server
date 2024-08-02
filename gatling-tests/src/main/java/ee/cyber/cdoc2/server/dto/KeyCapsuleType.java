package ee.cyber.cdoc2.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * CDOC2 key capsule type
 */
public enum KeyCapsuleType {
    @JsonProperty("ecc_secp384r1")
    ECC_SECP384R1,
    @JsonProperty("rsa")
    RSA
}
