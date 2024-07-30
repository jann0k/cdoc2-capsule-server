package ee.cyber.cdoc2.server;

import ee.cyber.cdoc2.client.Cdoc2KeyCapsuleApiClient;
import ee.cyber.cdoc2.client.EcCapsuleClientImpl;
import ee.cyber.cdoc2.client.ExtApiException;
import ee.cyber.cdoc2.client.KeyCapsuleClient;
import ee.cyber.cdoc2.client.KeyCapsuleClientImpl;
import ee.cyber.cdoc2.client.RsaCapsuleClientImpl;
import ee.cyber.cdoc2.crypto.Crypto;
import ee.cyber.cdoc2.crypto.ECKeys;
import ee.cyber.cdoc2.crypto.EllipticCurve;
import ee.cyber.cdoc2.crypto.PemTools;
import ee.cyber.cdoc2.crypto.Pkcs11DeviceConfiguration;
import ee.cyber.cdoc2.crypto.Pkcs11Tools;
import ee.cyber.cdoc2.crypto.RsaUtils;
import ee.cyber.cdoc2.server.generated.model.Capsule;
import ee.cyber.cdoc2.server.model.db.KeyCapsuleDb;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Base64;

import java.util.Collections;
import java.util.Properties;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


@Slf4j
class CreateKeyCapsuleIntegrationTest extends KeyCapsuleIntegrationTest {

    // read hardware PKCS11 device conf from a properties file
    private static final Pkcs11DeviceConfiguration PKCS11_CONF = Pkcs11DeviceConfiguration.load();

    @Qualifier("trustAllNoClientAuth")
    @Autowired
    private RestTemplate restTemplate;

    @Test
    void shouldCreateEcCapsuleUsingPKCS12Client() throws Exception {
        Cdoc2KeyCapsuleApiClient noAuthClient = createClient();

        EcCapsuleClientImpl client = new EcCapsuleClientImpl(
                KeyCapsuleClientImpl.create("shouldCreateEcCapsuleUsingPKCS12Client", noAuthClient, noAuthClient));

        // Client public key TLS encoded and base64 encoded from client-certificate.pem
        File[] certs = {TestData.getKeysDirectory().resolve("ca_certs/client-certificate.pem").toFile()};
        ECPublicKey recipientKey = ECKeys.loadCertKeys(certs).get(0);
        EllipticCurve curve = EllipticCurve.forPubKey(recipientKey);

        // Sender public key
        KeyPair senderKeyPair = curve.generateEcKeyPair();
        ECPublicKey senderPubKey = (ECPublicKey) senderKeyPair.getPublic();

        String id = client.storeSenderKey(recipientKey, senderPubKey);
        assertNotNull(id);

        this.checkCapsuleExistsInDb(
            id,
            KeyCapsuleDb.CapsuleType.SECP384R1,
            ECKeys.encodeEcPubKeyForTls(recipientKey),
            ECKeys.encodeEcPubKeyForTls(senderPubKey)
        );

        // getting the capsule must not succeed without client auth
        assertThrows(ExtApiException.class, () -> client.getSenderKey(id));
    }

    @Test
    void shouldCreateCapsuleUsingKeyServerPropertiesClientPKCS12() throws Exception {
        String prop = "cdoc2.client.server.id=testKeyServerPropertiesClientPKCS12\n";
        prop += "cdoc2.client.server.base-url.post=" + this.baseUrl + "\n";
        prop += "cdoc2.client.server.base-url.get=" + this.baseUrl + "\n";
        prop += "cdoc2.client.ssl.trust-store.type=JKS\n";
        prop += "cdoc2.client.ssl.trust-store=" + TestData.getKeysDirectory().resolve("clienttruststore.jks") + "\n";
        prop += "cdoc2.client.ssl.trust-store-password=passwd\n";

        Properties p = new Properties();
        p.load(new StringReader(prop));

        //use KeyCapsulesClientImpl directly to get access to client public certificate loaded using properties file
        KeyCapsuleClientImpl client = (KeyCapsuleClientImpl) KeyCapsuleClientImpl.create(p);

        File[] certs = {TestData.getKeysDirectory().resolve("ca_certs/client-certificate.pem").toFile()};
        ECPublicKey recipientPubKey = ECKeys.loadCertKeys(certs).get(0);

        ECPublicKey senderPubKey = (ECPublicKey) EllipticCurve.SECP384R1.generateEcKeyPair().getPublic();

        log.debug("Sender pub key: {}",
            Base64.getEncoder().encodeToString(
                ECKeys.encodeEcPubKeyForTls(EllipticCurve.SECP384R1, senderPubKey)
            )
        );

        assertNotNull(client.getServerIdentifier());

        String transactionID = new EcCapsuleClientImpl(client).storeSenderKey(
            recipientPubKey,
            senderPubKey
        );

        assertNotNull(transactionID);

        var dbCapsule = this.capsuleRepository.findById(transactionID);
        assertTrue(dbCapsule.isPresent());
    }

    @Test
    void shouldCreateRsaCapsuleUsingPKCS12Client() throws Exception {
        String prop = "cdoc2.client.server.id=shouldCreateRsaCapsuleUsingPKCS12Client\n";
        prop += "cdoc2.client.server.base-url.post=" + this.baseUrl + "\n";
        prop += "cdoc2.client.server.base-url.get=" + this.baseUrl + "\n";
        prop += "cdoc2.client.ssl.trust-store.type=JKS\n";
        prop += "cdoc2.client.ssl.trust-store=" + TestData.getKeysDirectory().resolve("clienttruststore.jks") + "\n";
        prop += "cdoc2.client.ssl.trust-store-password=passwd\n";

        Properties p = new Properties();
        p.load(new StringReader(prop));

        KeyCapsuleClient client = KeyCapsuleClientImpl.create(p);

        assertNotNull(client.getServerIdentifier());

        X509Certificate cert = PemTools.loadCertificate(
            Files.newInputStream(TestData.getKeysDirectory().resolve("rsa/client-rsa-2048-cert.pem"))
        );

        RSAPublicKey rsaPublicKey = (RSAPublicKey) cert.getPublicKey();
        byte[] kek = new byte[Crypto.FMK_LEN_BYTES];
        Crypto.getSecureRandom().nextBytes(kek);

        byte[] encryptedKek = RsaUtils.rsaEncrypt(kek, rsaPublicKey);

        String transactionID = new RsaCapsuleClientImpl(client).storeRsaCapsule(
            rsaPublicKey,
            encryptedKek
        );

        assertNotNull(transactionID);

        var dbCapsule = this.capsuleRepository.findById(transactionID);
        assertTrue(dbCapsule.isPresent());

        assertEquals(KeyCapsuleDb.CapsuleType.RSA, dbCapsule.get().getCapsuleType());
        assertArrayEquals(encryptedKek, dbCapsule.get().getPayload());
    }

    @Test
    @Tag("pkcs11")
    void testKeyServerPropertiesClientPKCS11Passwd() throws Exception {
        testKeyServerPropertiesClientPKCS11(false);
    }

    @Test
    @Tag("pkcs11")
    @Disabled("Requires user interaction. Needs to be run separately from other PKCS11 tests as SunPKCS11 caches "
            + "passwords ")
    void testKeyServerPropertiesClientPKCS11Prompt() throws Exception {
        if (System.console() == null) {
            //SpringBootTest sets headless to true and causes graphic dialog to fail, when running inside IDE
            System.setProperty("java.awt.headless", "false");
        }

        testKeyServerPropertiesClientPKCS11(true);
    }

    void testKeyServerPropertiesClientPKCS11(boolean interactive) throws Exception {
        String prop = getProperties(interactive);

        Properties p = new Properties();
        p.load(new StringReader(prop));

        KeyCapsuleClientImpl client = (KeyCapsuleClientImpl) KeyCapsuleClientImpl.create(p);

        KeyPair senderKeyPair = EllipticCurve.SECP384R1.generateEcKeyPair();
        ECPublicKey senderPubKey = (ECPublicKey) senderKeyPair.getPublic();

        // Storing clientKeyStore in KeyCapsulesClientImpl is a bit of hack for tests.
        // normally recipient certificate would come from LDAP, but for test-id card certs are not in LDAP
        X509Certificate cert  = (X509Certificate) client.getClientCertificate(PKCS11_CONF.keyAlias());
        assertNotNull(cert);

        // Client public key TLS encoded binary base64 encoded
        var recipientPubKey = cert.getPublicKey();
        log.info("Recipient public key class: {}", recipientPubKey.getClass());

        String transactionId;
        if (recipientPubKey instanceof ECPublicKey pubKey) {
            var capsuleClient = new EcCapsuleClientImpl(client);

            transactionId = capsuleClient.storeSenderKey(pubKey, senderPubKey);
            assertNotNull(transactionId);

            this.checkCapsuleExistsInDb(
                transactionId,
                KeyCapsuleDb.CapsuleType.SECP384R1,
                ECKeys.encodeEcPubKeyForTls(pubKey),
                ECKeys.encodeEcPubKeyForTls(senderPubKey)
            );
        } else if (recipientPubKey instanceof RSAPublicKey pubKey) {
            var capsuleClient = new RsaCapsuleClientImpl(client);
            var payload = senderPubKey.getEncoded();

            transactionId = capsuleClient.storeRsaCapsule(pubKey, payload);
            assertNotNull(transactionId);

            this.checkCapsuleExistsInDb(
                transactionId,
                KeyCapsuleDb.CapsuleType.RSA,
                RsaUtils.encodeRsaPubKey(pubKey),
                payload
            );
        } else {
            throw new RuntimeException("Unknown PKCS11 public key type received " + recipientPubKey.getClass());
        }
    }

    private String getProperties(boolean interactive) {
        String prop = "cdoc2.client.server.id=testKeyServerPropertiesClientPKCS11\n";
        prop += "cdoc2.client.server.base-url.post=" + this.baseUrl + "\n";
        prop += "cdoc2.client.server.base-url.get=" + this.baseUrl + "\n";
        prop += "cdoc2.client.ssl.trust-store.type=JKS\n";
        prop += "cdoc2.client.ssl.trust-store=" + TestData.getKeysDirectory().resolve("clienttruststore.jks") + "\n";
        prop += "cdoc2.client.ssl.trust-store-password=passwd\n";

        prop += "cdoc2.client.ssl.client-store.type=PKCS11\n";
        if (interactive) {
            prop += "cdoc2.client.ssl.client-store-password.prompt=PIN1\n";
        } else {
            prop += "cdoc2.client.ssl.client-store-password=" + Arrays.toString(PKCS11_CONF.pin()) + "\n";
        }
        return prop;
    }

    @Test
    @Tag("pkcs11")
    void testPKCS11Client() throws Exception {
        //PIN from conf file
        var protectionParameter = new KeyStore.PasswordProtection(PKCS11_CONF.pin());

        KeyStore clientKeyStore = null;
        KeyStore trustKeyStore = null;
        try {
            clientKeyStore = Pkcs11Tools.initPKCS11KeysStore(
                PKCS11_CONF.pkcs11Library(), PKCS11_CONF.slot(), protectionParameter
            );

            trustKeyStore = KeyStore.getInstance("JKS");
            trustKeyStore.load(Files.newInputStream(TestData.getKeysDirectory().resolve("clienttruststore.jks")),
                    "passwd".toCharArray());

        }  catch (GeneralSecurityException | IOException e) {
            log.error("Error initializing key stores", e);
        }

        assert clientKeyStore != null;
        log.debug("aliases: {}", Collections.list(clientKeyStore.aliases()));

        X509Certificate cert  = (X509Certificate) clientKeyStore.getCertificate(PKCS11_CONF.keyAlias());
        log.debug("Certificate issuer is {}.  This must be in server truststore "
                + "or SSL handshake will fail with cryptic error", cert.getIssuerX500Principal());

        Cdoc2KeyCapsuleApiClient mTlsClient = Cdoc2KeyCapsuleApiClient.builder()
                .withBaseUrl(baseUrl)
                .withClientKeyStore(clientKeyStore)
                .withClientKeyStoreProtectionParameter(protectionParameter)
                .withTrustKeyStore(trustKeyStore)
                .build();

        //recipient must match to client's cert pub key or GET will fail with 404
        PublicKey recipientPubKey = cert.getPublicKey();
        KeyPair senderKeyPair = EllipticCurve.SECP384R1.generateEcKeyPair();
        EllipticCurve.forPubKey(recipientPubKey);
        ECPublicKey senderPubKey = (ECPublicKey) senderKeyPair.getPublic();

        var serverClient = KeyCapsuleClientImpl.create("testPKCS11Client", mTlsClient, null);

        if (recipientPubKey instanceof ECPublicKey) {
            var capsuleClient = new EcCapsuleClientImpl(serverClient);
            var transactionId = capsuleClient.storeSenderKey(
                (ECPublicKey) recipientPubKey,
                senderPubKey
            );

            assertNotNull(transactionId);
            this.checkCapsuleExistsInDb(
                transactionId,
                KeyCapsuleDb.CapsuleType.SECP384R1,
                ECKeys.encodeEcPubKeyForTls((ECPublicKey) recipientPubKey),
                ECKeys.encodeEcPubKeyForTls(senderPubKey)
            );
        } else if (recipientPubKey instanceof RSAPublicKey) {
            var capsuleClient = new RsaCapsuleClientImpl(serverClient);
            var payload = senderPubKey.getEncoded();
            var transactionId = capsuleClient.storeRsaCapsule(
                (RSAPublicKey) recipientPubKey,
                payload
            );

            assertNotNull(transactionId);
            this.checkCapsuleExistsInDb(
                transactionId,
                KeyCapsuleDb.CapsuleType.RSA,
                RsaUtils.encodeRsaPubKey((RSAPublicKey) recipientPubKey),
                payload
            );
        } else {
            throw new RuntimeException("Unknown PKCS11 public key type received " + recipientPubKey.getClass());
        }
    }

    private void checkCapsuleExistsInDb(
        String txId,
        KeyCapsuleDb.CapsuleType expectedType,
        byte[] expectedRecipient,
        byte[] expectedPayload
    ) {
        var dbCapsuleOpt = this.capsuleRepository.findById(txId);
        assertTrue(dbCapsuleOpt.isPresent());
        var dbCapsule = dbCapsuleOpt.get();

        assertEquals(expectedType, dbCapsule.getCapsuleType());
        assertArrayEquals(expectedRecipient, dbCapsule.getRecipient());
        assertArrayEquals(expectedPayload, dbCapsule.getPayload());
    }

    @Test
    void shouldValidateCapsule() {
        var invalidCapsules = Arrays.asList(
            // empty capsule
            new Capsule(),

            // invalid recipient EC pub key
            new Capsule()
                .capsuleType(Capsule.CapsuleTypeEnum.ECC_SECP384R1)
                .recipientId(UUID.randomUUID().toString().getBytes())
                .ephemeralKeyMaterial(UUID.randomUUID().toString().getBytes()),

            // invalid RSA pub key
            new Capsule()
                .capsuleType(Capsule.CapsuleTypeEnum.RSA)
                .recipientId(UUID.randomUUID().toString().getBytes())
                .ephemeralKeyMaterial(UUID.randomUUID().toString().getBytes())
        );

        invalidCapsules.forEach(capsule -> assertThrows(
            HttpClientErrorException.BadRequest.class,
            () -> this.restTemplate.postForEntity(this.capsuleApiUrl(), capsule, Void.class)
        ));
    }

    @Test
    void shouldCreateRsaCapsule() throws Exception {
        var rsaCapsule = new Capsule()
            .capsuleType(Capsule.CapsuleTypeEnum.RSA)
            .ephemeralKeyMaterial(UUID.randomUUID().toString().getBytes());

        var rsaCerts = Arrays.asList(
            "rsa/client-rsa-2048-cert.pem",
            "rsa/client-rsa-4096-cert.pem",
            "rsa/client-rsa-8192-cert.pem",
            "rsa/client-rsa-16384-cert.pem"
        );

        for (String certFile: rsaCerts) {
            var bytes = Files.readAllBytes(TestData.getKeysDirectory().resolve(certFile).toAbsolutePath());
            var rsaCert = PemTools.loadCertificate(new ByteArrayInputStream(bytes));

            rsaCapsule.recipientId(RsaUtils.encodeRsaPubKey((RSAPublicKey) rsaCert.getPublicKey()));

            log.info("Creating RSA capsule for {}", certFile);

            var location = this.restTemplate.postForLocation(new URI(this.capsuleApiUrl()), rsaCapsule);
            assertNotNull(location);
        }
    }

    private Cdoc2KeyCapsuleApiClient createClient() throws GeneralSecurityException {
        return Cdoc2KeyCapsuleApiClient.builder()
            .withBaseUrl(this.baseUrl)
            .withTrustKeyStore(CLIENT_TRUST_STORE)
            .build();
    }

}
