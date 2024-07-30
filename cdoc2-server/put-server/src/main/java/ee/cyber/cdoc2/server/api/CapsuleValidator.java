package ee.cyber.cdoc2.server.api;

import ee.cyber.cdoc2.shared.crypto.ECKeys;
import ee.cyber.cdoc2.shared.crypto.RsaUtils;
import java.io.IOException;


import lombok.extern.slf4j.Slf4j;


import java.security.GeneralSecurityException;
import java.security.interfaces.ECPublicKey;

import ee.cyber.cdoc2.server.generated.model.Capsule;

/**
 * Utility class for validating capsules.
 */
@Slf4j
public final class CapsuleValidator {

    private CapsuleValidator() {
        // utility class
    }

    static boolean isValid(Capsule capsule) {
        switch (capsule.getCapsuleType()) {
            case ECC_SECP384R1:
                return validateEcSecp34r1Capsule(capsule);
            case RSA:
                return validateRSACapsule(capsule);
            default:
                throw new IllegalArgumentException("Unexpected capsule type: " + capsule.getCapsuleType());
        }
    }

    private static boolean validateEcSecp34r1Capsule(Capsule capsule) {

        try {
            int tlsEncodedKeyLen = 2 * ECKeys.SECP_384_R_1_LEN_BYTES + 1;

            if (capsule.getRecipientId() == null || capsule.getEphemeralKeyMaterial() == null) {
                log.error("Recipient id or ephemeral key was null");
                return false;
            }
            if (capsule.getRecipientId().length != tlsEncodedKeyLen
                    || capsule.getEphemeralKeyMaterial().length != tlsEncodedKeyLen) {
                log.error("Invalid secp384r1 curve key length");
                return false;
            }

            ECPublicKey recipientPubKey = ECKeys.decodeSecP384R1EcPublicKeyFromTls(capsule.getRecipientId());
            ECPublicKey senderPubKey = ECKeys.decodeSecP384R1EcPublicKeyFromTls(capsule.getEphemeralKeyMaterial());

            return (ECKeys.isValidSecP384R1(recipientPubKey) && ECKeys.isValidSecP384R1(senderPubKey));
        } catch (GeneralSecurityException gse) {
            log.error("Invalid secp384r1 EC key", gse);
        }
        return false;
    }

    private static boolean validateRSACapsule(Capsule capsule) {

        try {
            RsaUtils.decodeRsaPubKey(capsule.getRecipientId());
            return true;
        } catch (GeneralSecurityException | IOException exc) {
            log.error("Failed to parse capsule recipient's RSA public key", exc);
            return false;
        }
    }
}
