package ee.cyber.cdoc2.server.scenarios;

import ee.cyber.cdoc2.server.TestDataGenerator;
import ee.cyber.cdoc2.server.conf.TestConfig;
import io.gatling.shared.util.Ssl;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.netty.handler.codec.http.HttpResponseStatus;
import javax.net.ssl.KeyManagerFactory;
import lombok.extern.slf4j.Slf4j;
import scala.Option;
import scala.Some;

import static io.gatling.javaapi.core.CoreDsl.scenario;

/**
 * Test scenarios for elliptic curve key capsules
 */
@Slf4j
@SuppressWarnings("unchecked") // compiler warning come from usage of Gatling API
public class EccKeyCapsuleScenarios extends KeyCapsuleScenarios {

    public EccKeyCapsuleScenarios(TestConfig conf, TestDataGenerator generator) {
        super(conf, generator);
    }

    // returns the key store to use for the user injected by Gatling
    public KeyManagerFactory getKeyManager(long userId) {
        var keyStore = this.testData.getEccKeyStore(userId);
        return Ssl.newKeyManagerFactory(
            new Some<>(keyStore.keyStoreType()),
            keyStore.file().getAbsolutePath(),
            keyStore.password(),
            Option.empty()
        );
    }

    public ScenarioBuilder sendAndGetEccKeyCapsule() {
        return scenario("Send and get ecc capsule")
            .exec(this.sendEccKeyCapsule(), this.getAndCheckEccKeyCapsule());
    }

    public ScenarioBuilder getRecipientMismatch() {
        return scenario("Request EC capsule with mismatching recipient")
            .exec(this.sendKeyCapsuleCheckSuccess(
                this.testData::generateEccCapsuleWithWrongRecipient, ScenarioIdentifiers.NEG_GET_08 + " create")
            )
            .exec(this.checkKeyCapsuleMismatch(ScenarioIdentifiers.NEG_GET_08 + " get"));
    }

    public ScenarioBuilder getWithInvalidTransactionIds() {
        return scenario("Request capsule with invalid transactionId values")
            .exec(
                this.checkInvalidTransactionId(
                    ScenarioIdentifiers.NEG_GET_02,
                    TestDataGenerator.randomString(TX_ID_MIN_LENGTH),
                    HttpResponseStatus.NOT_FOUND
                ),
                this.checkInvalidTransactionId(
                    ScenarioIdentifiers.NEG_GET_03,
                    "123",
                    HttpResponseStatus.BAD_REQUEST
                ),
                this.checkEmptyTransactionId(
                    ScenarioIdentifiers.NEG_GET_04,
                    "",
                    HttpResponseStatus.NOT_FOUND
                ),
                this.checkInvalidTransactionId(
                    ScenarioIdentifiers.NEG_GET_05,
                    null,
                    HttpResponseStatus.BAD_REQUEST
                ),
                this.checkMissingTransactionIdAndUriSlash(
                    ScenarioIdentifiers.NEG_GET_06,
                    HttpResponseStatus.METHOD_NOT_ALLOWED
                ),
                this.checkInvalidTransactionId(
                    ScenarioIdentifiers.NEG_GET_07,
                    TestDataGenerator.randomString(TX_ID_MAX_LENGTH + 1),
                    HttpResponseStatus.BAD_REQUEST
                )
            )
            .exitHereIfFailed();
    }

    public ChainBuilder sendEccKeyCapsule() {
        return this.sendKeyCapsuleCheckSuccess(
            this.testData::generateEccCapsule, ScenarioIdentifiers.POS_PUT_01 + " - Create ecc key capsule"
        );
    }

    public ScenarioBuilder sendEccKeyCapsuleRepeatedly() {
        var capsule = this.testData.generateEccCapsule(1L);

        return scenario("Send same ecc capsule twice").exec(
            this.sendKeyCapsuleCheckSuccess(x -> capsule, ScenarioIdentifiers.POS_PUT_03 + " - 1st"),
            this.sendKeyCapsuleCheckSuccess(x -> capsule, ScenarioIdentifiers.POS_PUT_03 + " - 2nd")
        );
    }

    private ChainBuilder getAndCheckEccKeyCapsule() {
        return this.checkLatestKeyCapsule(ScenarioIdentifiers.POS_GET_02 + " - Get correct ecc key capsule");
    }
}
