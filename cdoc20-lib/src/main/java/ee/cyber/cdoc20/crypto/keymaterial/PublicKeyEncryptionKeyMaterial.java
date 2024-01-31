package ee.cyber.cdoc20.crypto.keymaterial;

import java.security.PublicKey;

import ee.cyber.cdoc20.crypto.EncryptionKeyOrigin;


/**
 * Represents key material required for encryption key derived from the public key.
 *
 * @param publicKey public key
 * @param keyLabel  key label
 */
public record PublicKeyEncryptionKeyMaterial(
    PublicKey publicKey,
    String keyLabel
) implements EncryptionKeyMaterial {

    @Override
    public String getLabel() {
        return keyLabel;
    }

    @Override
    public EncryptionKeyOrigin getKeyOrigin() {
        return EncryptionKeyOrigin.FROM_PUBLIC_KEY;
    }

    /**
     * @return public key to derive the encryption key
     */
    public PublicKey getPublicKey() {
        return publicKey;
    }

}
