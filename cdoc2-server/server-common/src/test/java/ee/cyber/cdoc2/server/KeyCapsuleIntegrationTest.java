package ee.cyber.cdoc2.server;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import ee.cyber.cdoc2.server.generated.model.Capsule;
import ee.cyber.cdoc2.server.model.db.KeyCapsuleDb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


@Slf4j
abstract class KeyCapsuleIntegrationTest extends BaseInitializationTest {

    // context path of the key capsule api
    protected static final String API_KEY_CAPSULES = "/key-capsules";

    @Autowired
    private ExpiredCapsuleCleanUpJob cleanUpJob;

    @Test
    void testKeyCapsuleJpaConstraints() {
        KeyCapsuleDb model = new KeyCapsuleDb();

        model.setCapsuleType(KeyCapsuleDb.CapsuleType.SECP384R1);
        model.setPayload("123".getBytes());
        model.setRecipient(null);

        Throwable cause = assertThrows(Throwable.class, () -> this.capsuleRepository.save(model));
        assertThrowsConstraintViolationException(cause);
    }

    @Test
    void testJpaSaveAndFindByIdForKeyCapsule() {
        // test that jpa is up and running (expect no exceptions)
        this.capsuleRepository.count();

        KeyCapsuleDb model = new KeyCapsuleDb();
        model.setCapsuleType(KeyCapsuleDb.CapsuleType.SECP384R1);

        model.setRecipient("123".getBytes());
        model.setPayload("345".getBytes());
        model.setExpiryTime(EXPIRY_TIME);
        KeyCapsuleDb saved = this.capsuleRepository.save(model);

        assertNotNull(saved);
        String txId = saved.getTransactionId();
        assertNotNull(txId);
        log.debug("Created {}", txId);

        Optional<KeyCapsuleDb> retrievedOpt = this.capsuleRepository.findById(txId);
        assertTrue(retrievedOpt.isPresent());

        var dbRecord = retrievedOpt.get();
        assertNotNull(dbRecord.getTransactionId()); // transactionId was generated
        assertTrue(dbRecord.getTransactionId().startsWith("KC"));
        assertNotNull(dbRecord.getCreatedAt()); // createdAt field was filled
        assertEquals(dbRecord.getCapsuleType(), model.getCapsuleType());
        log.debug("Retrieved {}", dbRecord);
    }

    @Test
    void shouldCleanUpExpiredKeyCapsules() {
        // prepare database for testing
        this.capsuleRepository.deleteAll();

        Instant expiryTime = Instant.now();
        KeyCapsuleDb model = new KeyCapsuleDb();
        model.setCapsuleType(KeyCapsuleDb.CapsuleType.SECP384R1);

        model.setRecipient("123".getBytes());
        model.setPayload("345".getBytes());
        model.setExpiryTime(expiryTime);
        List<KeyCapsuleDb> savedCapsules = this.capsuleRepository.saveAll(List.of(model, model, model));
        assertEquals(3, savedCapsules.size());

        cleanUpJob.cleanUpExpiredCapsules();

        long count = this.capsuleRepository.count();
        assertEquals(0, count);
    }

    /**
     * Saves the capsule in the database
     * @param dto the capsule dto
     * @return the saved capsule
     */
    protected KeyCapsuleDb saveCapsule(Capsule dto, Instant expiryTime) {
        return this.capsuleRepository.save(
            new KeyCapsuleDb()
                .setCapsuleType(
                    dto.getCapsuleType() == Capsule.CapsuleTypeEnum.ECC_SECP384R1
                        ? KeyCapsuleDb.CapsuleType.SECP384R1
                        : KeyCapsuleDb.CapsuleType.RSA
                )
                .setRecipient(dto.getRecipientId())
                .setPayload(dto.getEphemeralKeyMaterial())
                .setExpiryTime(expiryTime)
        );
    }

    @SneakyThrows
    protected String capsuleApiUrl() {
        return this.baseUrl + API_KEY_CAPSULES;
    }

}
