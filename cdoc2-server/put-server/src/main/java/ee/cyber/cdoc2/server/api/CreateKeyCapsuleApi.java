package ee.cyber.cdoc2.server.api;

import ee.cyber.cdoc2.server.config.KeyCapsuleConfigProperties;
import ee.cyber.cdoc2.server.generated.model.Capsule;
import ee.cyber.cdoc2.server.generated.api.KeyCapsulesApi;
import ee.cyber.cdoc2.server.generated.api.KeyCapsulesApiDelegate;
import ee.cyber.cdoc2.server.generated.api.KeyCapsulesApiController;
import ee.cyber.cdoc2.server.model.db.KeyCapsuleDb;
import ee.cyber.cdoc2.server.model.db.KeyCapsuleRepository;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;

import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.NativeWebRequest;

import static ee.cyber.cdoc2.server.Utils.getCapsuleExpirationTime;
import static ee.cyber.cdoc2.server.Utils.getPathAndQueryPart;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;


/**
 * Implements API for creating CDOC2 key capsules {@link KeyCapsulesApi}
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CreateKeyCapsuleApi implements KeyCapsulesApiDelegate {

    private final NativeWebRequest nativeWebRequest;
    private final KeyCapsuleConfigProperties configProperties;
    private final KeyCapsuleRepository keyCapsuleRepository;

    @Override
    public Optional<NativeWebRequest> getRequest() {
        return Optional.of(nativeWebRequest);
    }

    @Override
    public ResponseEntity<Void> createCapsule(
        Capsule capsule,
        @Nullable LocalDateTime xExpiryTime
    ) {
        log.trace("createCapsule(type={}, recipientId={} bytes, ephemeralKey={} bytes)",
            capsule.getCapsuleType(), capsule.getRecipientId().length,
            capsule.getEphemeralKeyMaterial().length
        );

        if (!CapsuleValidator.isValid(capsule)) {
            return ResponseEntity.badRequest().build();
        }

        OffsetDateTime expiryTime = getExpiryTime(xExpiryTime);

        try {
            var saved = this.keyCapsuleRepository.save(
                new KeyCapsuleDb()
                    .setCapsuleType(getDbCapsuleType(capsule.getCapsuleType()))
                    .setRecipient(capsule.getRecipientId())
                    .setPayload(capsule.getEphemeralKeyMaterial())
                    .setExpiryTime(expiryTime.toInstant())
            );

            log.info(
                "Capsule(transactionId={}, type={}) created",
                saved.getTransactionId(), saved.getCapsuleType()
            );

            URI created = getResourceLocation(saved.getTransactionId());

            return ResponseEntity.created(created).build();
        } catch (Exception e) {
            log.error(
                "Failed to save key capsule(type={}, recipient={}, payloadLength={})",
                capsule.getCapsuleType(),
                Base64.getEncoder().encodeToString(capsule.getRecipientId()),
                capsule.getEphemeralKeyMaterial().length,
                e
            );
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ResponseEntity<Capsule> getCapsuleByTransactionId(String transactionId) {
        log.error("getCapsuleByTransactionId() operation not supported on key capsule put server");
        return new ResponseEntity<>(HttpStatus.METHOD_NOT_ALLOWED);
    }

    /**
     * Get URI for getting Key Capsule resource (Location).
     * @param id Capsule id example: KC9b7036de0c9fce889850c4bbb1e23482
     * @return URI (path and query) example: /key-capsules/KC9b7036de0c9fce889850c4bbb1e23482
     * @throws URISyntaxException if URI is invalid
     */
    private static URI getResourceLocation(String id) throws URISyntaxException {
        return getPathAndQueryPart(
            linkTo(methodOn(KeyCapsulesApiController.class).getCapsuleByTransactionId(id)).toUri()
        );
    }

    private static KeyCapsuleDb.CapsuleType getDbCapsuleType(Capsule.CapsuleTypeEnum dtoType) {
        switch (dtoType) {
            case ECC_SECP384R1:
                return KeyCapsuleDb.CapsuleType.SECP384R1;
            case RSA:
                return KeyCapsuleDb.CapsuleType.RSA;
            default:
                throw new IllegalArgumentException("Unknown capsule type: " + dtoType);
        }
    }

    private OffsetDateTime getExpiryTime(LocalDateTime xExpiryTime) {
        if (null != xExpiryTime) {
            validateExpiryTime(xExpiryTime);

            return toOffsetDateTime(xExpiryTime);
        } else {
            return getCapsuleExpirationTime(configProperties.defaultExpirationDuration());
        }
    }

    private void validateExpiryTime(LocalDateTime expiryTime) {
        OffsetDateTime xMaxExpiryTime = getCapsuleExpirationTime(configProperties.maxExpirationDuration());
        if (toOffsetDateTime(expiryTime).isAfter(xMaxExpiryTime)) {
            throw new IllegalArgumentException("Key capsule expire time cannot exceed max allowed");
        }
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime expiryTime) {
        return OffsetDateTime.of(expiryTime, ZoneOffset.UTC);
    }

}
