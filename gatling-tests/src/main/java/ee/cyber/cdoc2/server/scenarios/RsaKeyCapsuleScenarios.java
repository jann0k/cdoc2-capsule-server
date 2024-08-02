package ee.cyber.cdoc2.server.scenarios;

import ee.cyber.cdoc2.server.TestDataGenerator;
import ee.cyber.cdoc2.server.conf.TestConfig;
import ee.cyber.cdoc2.server.datagen.CertUtil;
import ee.cyber.cdoc2.server.dto.GeneratedCapsule;
import ee.cyber.cdoc2.server.dto.KeyCapsuleRequest;
import ee.cyber.cdoc2.server.dto.KeyCapsuleType;
import io.gatling.shared.util.Ssl;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import javax.net.ssl.KeyManagerFactory;
import lombok.extern.slf4j.Slf4j;
import scala.Option;
import scala.Some;

import static io.gatling.javaapi.core.CoreDsl.scenario;

/**
 * Test scenarios for RSA key capsules
 */
@Slf4j
@SuppressWarnings("unchecked") // compiler warning come from usage of Gatling API
public class RsaKeyCapsuleScenarios extends KeyCapsuleScenarios {

    public RsaKeyCapsuleScenarios(TestConfig conf, TestDataGenerator generator) {
        super(conf, generator);
    }

    // returns the key store to use for the user injected by Gatling
    public KeyManagerFactory getKeyManager(long userId) {
        var keyStore = this.testData.getRsaKeyStore(userId);
        return Ssl.newKeyManagerFactory(
            new Some<>(keyStore.keyStoreType()),
            keyStore.file().getAbsolutePath(),
            keyStore.password(),
            Option.empty()
        );
    }

    public ScenarioBuilder sendAndGetRsaKeyCapsule() {
        return scenario("Send and get rsa capsule")
            .exec(this.sendRsaKeyCapsule(), this.getAndCheckRsaKeyCapsule());
    }

    public ScenarioBuilder sendAndGetRecipientMismatch() {
        return scenario("Request RSA capsule with mismatching recipient")
            .exec(
                this.sendKeyCapsuleCheckSuccess(
                    this.testData::generateRsaCapsuleWithWrongRecipient, ScenarioIdentifiers.NEG_GET_09 + " create"
                )
            )
            .exec(this.checkKeyCapsuleMismatch(ScenarioIdentifiers.NEG_GET_09 + " get"));
    }

    public ScenarioBuilder sendRsaKeyCapsuleRepeatedly() {
        var capsule = this.testData.generateRsaCapsule(1L);

        return scenario("Send same rsa capsule twice").exec(
            this.sendKeyCapsuleCheckSuccess(x -> capsule, ScenarioIdentifiers.POS_PUT_04 + " - 1st"),
            this.sendKeyCapsuleCheckSuccess(x -> capsule, ScenarioIdentifiers.POS_PUT_04 + " - 2nd")
        );
    }

    public ScenarioBuilder sendRsaKeyCapsuleRandomKeyMaterial() {
        var keyStore = this.testData.getRandomRsaKeyStore();
        var payload = new KeyCapsuleRequest(
            CertUtil.encodePublicKey((RSAPublicKey) keyStore.publicKey()),
            Base64.getEncoder().encodeToString(TestDataGenerator.randomBytes(KEY_MATERIAL_MAX_LENGTH)),
            KeyCapsuleType.RSA
        );
        var capsule = new GeneratedCapsule(keyStore, payload);

        return scenario("Send rsa capsule with random material").exec(
            this.sendKeyCapsuleCheckSuccess(x -> capsule, ScenarioIdentifiers.POS_PUT_06)
        );
    }

    public ScenarioBuilder sendRsaKeyCapsuleTooBigKeyMaterial() {
        var keyStore = this.testData.getRandomRsaKeyStore();
        var payload = new KeyCapsuleRequest(
            CertUtil.encodePublicKey((RSAPublicKey) keyStore.publicKey()),
            Base64.getEncoder().encodeToString(TestDataGenerator.randomBytes(KEY_MATERIAL_MAX_LENGTH + 1)),
            KeyCapsuleType.RSA
        );
        var capsule = new GeneratedCapsule(keyStore, payload);

        return scenario("Create rsa capsule with too big key material").exec(
            this.sendKeyCapsuleCheckError(x -> capsule, ScenarioIdentifiers.NEG_PUT_01, HttpResponseStatus.BAD_REQUEST)
        );
    }

    public ChainBuilder sendRsaKeyCapsule() {
        return this.sendKeyCapsuleCheckSuccess(
            this.testData::generateRsaCapsule,
            ScenarioIdentifiers.POS_PUT_02 + " - Create rsa key capsule"
        );
    }

    private ChainBuilder getAndCheckRsaKeyCapsule() {
        return this.checkLatestKeyCapsule(ScenarioIdentifiers.POS_GET_01 + " - Get correct rsa key capsule");
    }

}
