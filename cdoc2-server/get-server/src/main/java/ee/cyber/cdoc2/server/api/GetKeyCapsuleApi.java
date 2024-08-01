package ee.cyber.cdoc2.server.api;

import ee.cyber.cdoc2.shared.crypto.ECKeys;
import ee.cyber.cdoc2.shared.crypto.RsaUtils;
import ee.cyber.cdoc2.shared.crypto.KeyAlgorithm;
import ee.cyber.cdoc2.server.model.db.KeyCapsuleDb;
import ee.cyber.cdoc2.server.model.db.KeyCapsuleRepository;
import ee.cyber.cdoc2.server.generated.model.Capsule;
import ee.cyber.cdoc2.server.generated.api.KeyCapsulesApi;
import ee.cyber.cdoc2.server.generated.api.KeyCapsulesApiDelegate;


import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;
import javax.security.auth.x500.X500Principal;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.NativeWebRequest;


/**
 * Implements API for getting CDOC2 key capsules {@link KeyCapsulesApi}
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GetKeyCapsuleApi implements KeyCapsulesApiDelegate {

    private final NativeWebRequest nativeWebRequest;
    private final KeyCapsuleRepository capsuleRepository;

    @Override
    public Optional<NativeWebRequest> getRequest() {
        return Optional.of(this.nativeWebRequest);
    }

    @Override
    public ResponseEntity<Capsule> getCapsuleByTransactionId(String transactionId) {
        var clientCertOpt = this.getClientCertFromRequest();
        if (clientCertOpt.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        var clientCert = clientCertOpt.get();
        PublicKey clientPubKey = clientCert.getPublicKey();

        Optional<KeyCapsuleDb> capsuleDbOpt = this.capsuleRepository.findById(transactionId);
        if (capsuleDbOpt.isEmpty()) {
            log.debug("Capsule with transactionId {} not found", transactionId);
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        var capsule = capsuleDbOpt.get();
        if (isRecipient(clientPubKey, capsule)) {
            log.info("Found capsule(transaction={}) for client certificate", transactionId);
            return ResponseEntity.ok(toDto(capsule));
        } else {
            log.info("Client certificate does not match capsule(transactionId={}) recipient", transactionId);
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @Override
    public ResponseEntity<Void> createCapsule(Capsule capsule, LocalDateTime xExpiryTime) {
        log.error("createCapsule() operation not supported on key capsule get server");
        return new ResponseEntity<>(HttpStatus.METHOD_NOT_ALLOWED);
    }

    private static boolean isRecipient(PublicKey publicKey, KeyCapsuleDb capsule) {
        try {
            if (capsule.getCapsuleType() == KeyCapsuleDb.CapsuleType.SECP384R1
                    && KeyAlgorithm.isEcKeysAlgorithm(publicKey.getAlgorithm())
                    && ECKeys.isEcSecp384r1Curve((ECPublicKey) publicKey)) {
                return Arrays.equals(
                    capsule.getRecipient(),
                    ECKeys.encodeEcPubKeyForTls((ECPublicKey) publicKey)
                );
            }
            if (capsule.getCapsuleType() == KeyCapsuleDb.CapsuleType.RSA
                    && KeyAlgorithm.isRsaKeysAlgorithm(publicKey.getAlgorithm())) {
                return Arrays.equals(capsule.getRecipient(), RsaUtils.encodeRsaPubKey((RSAPublicKey) publicKey));
            }
        } catch (GeneralSecurityException exc) {
            log.error("Error occurred while verifying recipient", exc);
        }
        return false;
    }

    private static Capsule toDto(KeyCapsuleDb db) {
        var dto = new Capsule();
        dto.setRecipientId(db.getRecipient());
        dto.setEphemeralKeyMaterial(db.getPayload());

        switch (db.getCapsuleType()) {
            case SECP384R1:
                dto.setCapsuleType(Capsule.CapsuleTypeEnum.ECC_SECP384R1);
                break;
            case RSA:
                dto.setCapsuleType(Capsule.CapsuleTypeEnum.RSA);
                break;
            default:
                throw new IllegalArgumentException("Unsupported capsule type: " + db.getCapsuleType());
        }
        return dto;
    }

    private Optional<X509Certificate> getClientCertFromRequest() {
        HttpServletRequest req = this.nativeWebRequest.getNativeRequest(HttpServletRequest.class);
        X509Certificate[] certs = (req != null)
                ? (X509Certificate[]) req.getAttribute("jakarta.servlet.request.X509Certificate")
                : new X509Certificate[0];
        if (null == certs) {
            String errorMsg = "Failed to get client certificate from http request";
            log.error(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }

        if (certs.length > 0) {
            var clientCert = certs[0];
            log.info("Got client certificate(subject='{}')", getCertSubjectName(clientCert));
            return Optional.of(clientCert);
        } else {
            log.info("No client certificate in http request");
            return Optional.empty();
        }
    }

    private static String getCertSubjectName(X509Certificate certificate) {
        return Optional.ofNullable(certificate.getSubjectX500Principal())
            .map(X500Principal::getName)
            .orElse("");
    }

}
