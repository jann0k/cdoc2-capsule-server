package ee.cyber.cdoc2.server;

import ee.cyber.cdoc2.CDocUserException;
import ee.cyber.cdoc2.UserErrorCode;
import ee.cyber.cdoc2.client.Cdoc2KeyCapsuleApiClient;
import ee.cyber.cdoc2.client.EcCapsuleClientImpl;
import ee.cyber.cdoc2.client.KeyCapsuleClientImpl;
import ee.cyber.cdoc2.client.RsaCapsuleClientImpl;
import ee.cyber.cdoc2.client.api.ApiException;
import ee.cyber.cdoc2.server.generated.model.Capsule;
import ee.cyber.cdoc2.crypto.Crypto;
import ee.cyber.cdoc2.crypto.ECKeys;
import ee.cyber.cdoc2.crypto.EllipticCurve;
import ee.cyber.cdoc2.crypto.PemTools;
import ee.cyber.cdoc2.crypto.Pkcs11DeviceConfiguration;
import ee.cyber.cdoc2.crypto.Pkcs11Tools;
import ee.cyber.cdoc2.crypto.RsaUtils;

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
import java.util.Base64;
import java.util.Collections;
import java.util.Optional;
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
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


@Slf4j
class GetKeyCapsuleApiTests extends KeyCapsuleIntegrationTest {

    // read hardware PKCS11 device conf from a properties file
    private static final Pkcs11DeviceConfiguration PKCS11_CONF = Pkcs11DeviceConfiguration.load();

    // rest client with client auth using keystore rsa/client-rsa-2048.p12
    @Qualifier("trustAllWithClientAuth")
    @Autowired
    private RestTemplate restTemplate;

    @Test
    void testPKCS12Client() throws Exception {
        Cdoc2KeyCapsuleApiClient client = createClientWithPkcs12();

        var capsule = new Capsule()
            .capsuleType(Capsule.CapsuleTypeEnum.ECC_SECP384R1);

        // Recipient public key TLS encoded and base64 encoded from client-certificate.pem
        File[] certs = {TestData.getKeysDirectory().resolve("ca_certs/client-certificate.pem").toFile()};
        ECPublicKey recipientKey = ECKeys.loadCertKeys(certs).get(0);
        EllipticCurve curve = EllipticCurve.forPubKey(recipientKey);
        capsule.recipientId(ECKeys.encodeEcPubKeyForTls(curve, recipientKey));

        // Sender public key
        KeyPair senderKeyPair = curve.generateEcKeyPair();
        ECPublicKey senderPubKey = (ECPublicKey) senderKeyPair.getPublic();
        capsule.ephemeralKeyMaterial(ECKeys.encodeEcPubKeyForTls(senderPubKey));

        String id = this.saveCapsule(capsule, EXPIRY_TIME).getTransactionId();

        assertNotNull(id);

        var serverCapsule = client.getCapsule(id);

        assertTrue(serverCapsule.isPresent());

        // comparing client.model.Capsule and server.model.Capsule.
        //Since these are different classes, can't do simple equals, although json is the same
        assertArrayEquals(capsule.getRecipientId(), serverCapsule.get().getRecipientId());
        assertArrayEquals(capsule.getEphemeralKeyMaterial(), serverCapsule.get().getEphemeralKeyMaterial());
        assertEquals(String.valueOf(capsule.getCapsuleType()), String.valueOf(serverCapsule.get().getCapsuleType()));

    }

    @Test
    void testKeyServerPropertiesClientPKCS12() throws Exception {
        var client = createPkcs12ServerClient(baseUrl);

        X509Certificate cert = (X509Certificate) client.getClientCertificate();

        assertNotNull(cert);

        //recipientPubKey must match with pub key in mutual TLS
        ECPublicKey recipientPubKey = (ECPublicKey) cert.getPublicKey();

        ECPublicKey senderPubKey = (ECPublicKey) EllipticCurve.SECP384R1.generateEcKeyPair().getPublic();

        log.debug("Sender pub key: {}",
            Base64.getEncoder().encodeToString(
                ECKeys.encodeEcPubKeyForTls(EllipticCurve.SECP384R1, senderPubKey)
            )
        );
        assertNotNull(client.getServerIdentifier());

        var curve = EllipticCurve.forPubKey(recipientPubKey);

        var capsule = new Capsule()
            .ephemeralKeyMaterial(ECKeys.encodeEcPubKeyForTls(senderPubKey))
            .recipientId(ECKeys.encodeEcPubKeyForTls(curve, recipientPubKey))
            .capsuleType(Capsule.CapsuleTypeEnum.ECC_SECP384R1);

        String transactionID = this.saveCapsule(capsule, EXPIRY_TIME).getTransactionId();

        assertNotNull(transactionID);

        Optional<ECPublicKey> serverSenderKey = new EcCapsuleClientImpl(client).getSenderKey(transactionID);
        assertTrue(serverSenderKey.isPresent());
        assertEquals(senderPubKey, serverSenderKey.get());
    }

    @Test
    void testKeyCapsulesClientImplRsaPKCS12() throws Exception {
        String prop = "cdoc2.client.server.id=testKeyCapsulesClientImplRsaPKCS12\n";
        prop += "cdoc2.client.server.base-url.post=" + baseUrl + "\n";
        prop += "cdoc2.client.server.base-url.get=" + baseUrl + "\n";
        prop += "cdoc2.client.ssl.trust-store.type=JKS\n";
        prop += "cdoc2.client.ssl.trust-store="
            + TestData.getKeysDirectory().resolve("clienttruststore.jks") + "\n";
        prop += "cdoc2.client.ssl.trust-store-password=passwd\n";

        prop += "cdoc2.client.ssl.client-store.type=PKCS12\n";
        prop += "cdoc2.client.ssl.client-store="
            + TestData.getKeysDirectory().resolve("rsa/client-rsa-2048.p12") + "\n";
        prop += "cdoc2.client.ssl.client-store-password=passwd\n";

        Properties p = new Properties();
        p.load(new StringReader(prop));

        KeyCapsuleClientImpl client = (KeyCapsuleClientImpl) KeyCapsuleClientImpl.create(p);

        X509Certificate cert = (X509Certificate) client.getClientCertificate();
        assertNotNull(cert);
        RSAPublicKey senderPubKey = (RSAPublicKey) cert.getPublicKey();

        RSAPublicKey rsaPublicKey = (RSAPublicKey) cert.getPublicKey();
        byte[] kek = new byte[Crypto.FMK_LEN_BYTES];
        Crypto.getSecureRandom().nextBytes(kek);
        byte[] encryptedKek = RsaUtils.rsaEncrypt(kek, rsaPublicKey);

        var capsule = new Capsule()
            .ephemeralKeyMaterial(encryptedKek)
            .recipientId(RsaUtils.encodeRsaPubKey(senderPubKey))
            .capsuleType(Capsule.CapsuleTypeEnum.RSA);

        String transactionID = this.saveCapsule(capsule, EXPIRY_TIME).getTransactionId();

        assertNotNull(transactionID);

        Optional<byte[]> serverEncKek = new RsaCapsuleClientImpl(client).getEncryptedKek(transactionID);

        assertTrue(serverEncKek.isPresent());
        assertArrayEquals(encryptedKek, serverEncKek.get());
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
        String prop = "cdoc2.client.server.id=testKeyServerPropertiesClientPKCS11\n";
        prop += "cdoc2.client.server.base-url.post=" + baseUrl + "\n";
        prop += "cdoc2.client.server.base-url.get=" + baseUrl + "\n";
        prop += "cdoc2.client.ssl.trust-store.type=JKS\n";
        prop += "cdoc2.client.ssl.trust-store=" + TestData.getKeysDirectory().resolve("clienttruststore.jks") + "\n";
        prop += "cdoc2.client.ssl.trust-store-password=passwd\n";

        prop += "cdoc2.client.ssl.client-store.type=PKCS11\n";

        if (interactive) {
            prop += "cdoc2.client.ssl.client-store-password.prompt=PIN1\n";
        } else {
            prop += "cdoc2.client.ssl.client-store-password=" + new String(PKCS11_CONF.pin()) + "\n";
        }

        Properties p = new Properties();
        p.load(new StringReader(prop));

        KeyCapsuleClientImpl client = (KeyCapsuleClientImpl) KeyCapsuleClientImpl.create(p);

        KeyPair senderKeyPair = EllipticCurve.SECP384R1.generateEcKeyPair();
        ECPublicKey senderPubKey = (ECPublicKey) senderKeyPair.getPublic();

        // Storing clientKeyStore in KeyServerPropertiesClient is a bit of hack for tests.
        // It's required to get recipient pub key
        // normally recipient certificate would come from LDAP, but for test-id card certs are not in LDAP
        X509Certificate cert  = (X509Certificate) client.getClientCertificate(PKCS11_CONF.keyAlias());
        assertNotNull(cert);
        // Client public key TLS encoded binary base64 encoded
        PublicKey recipientPubKey = cert.getPublicKey();

        if (recipientPubKey instanceof ECPublicKey pubKey) {
            var curve = EllipticCurve.forPubKey(recipientPubKey);
            var capsule = new Capsule()
                .ephemeralKeyMaterial(ECKeys.encodeEcPubKeyForTls(senderPubKey))
                .recipientId(ECKeys.encodeEcPubKeyForTls(curve, pubKey))
                .capsuleType(Capsule.CapsuleTypeEnum.ECC_SECP384R1);

            String id = this.saveCapsule(capsule, EXPIRY_TIME).getTransactionId();
            assertNotNull(id);

            Optional<ECPublicKey> payload = new EcCapsuleClientImpl(client).getSenderKey(id);
            assertTrue(payload.isPresent());
            assertEquals(senderPubKey, payload.get());
        } else if (recipientPubKey instanceof RSAPublicKey pubKey) {
            var keyMaterial = senderPubKey.getEncoded();
            var capsule = new Capsule()
                .ephemeralKeyMaterial(keyMaterial)
                .recipientId(RsaUtils.encodeRsaPubKey(pubKey))
                .capsuleType(Capsule.CapsuleTypeEnum.RSA);

            String id = this.saveCapsule(capsule, EXPIRY_TIME).getTransactionId();
            assertNotNull(id);

            Optional<byte[]> payload = new RsaCapsuleClientImpl(client).getEncryptedKek(id);
            assertTrue(payload.isPresent());
            assertArrayEquals(keyMaterial, payload.get());
        } else {
            throw new RuntimeException("Unsupported PKCS11 public key type: " + recipientPubKey.getClass());
        }
    }

    @Test
    @Tag("pkcs11")
    void testPKCS11Client() throws Exception {

        //PIN1 for 37101010021 test id-kaart
        var protectionParameter = new KeyStore.PasswordProtection(PKCS11_CONF.pin());

        //Or ask pin interactively
        //KeyStore.ProtectionParameter protectionParameter = getKeyStoreCallbackProtectionParameter("PIN1");

        KeyStore clientKeyStore = null;
        KeyStore trustKeyStore = null;
        try {
            clientKeyStore = Pkcs11Tools.initPKCS11KeysStore(
                PKCS11_CONF.pkcs11Library(),
                PKCS11_CONF.slot(),
                protectionParameter
            );

            trustKeyStore = KeyStore.getInstance("JKS");
            trustKeyStore.load(Files.newInputStream(TestData.getKeysDirectory().resolve("clienttruststore.jks")),
                "passwd".toCharArray());

        }  catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
            log.error("Error initializing key stores", e);
        }

        assertNotNull(clientKeyStore);
        log.debug("aliases: {}", Collections.list(clientKeyStore.aliases()));


        X509Certificate cert  = (X509Certificate) clientKeyStore.getCertificate(PKCS11_CONF.keyAlias());
        log.debug("Certificate issuer is {}.  This must be in server truststore "
            + "or SSL handshake will fail with cryptic error", cert.getIssuerDN());

        Cdoc2KeyCapsuleApiClient client = Cdoc2KeyCapsuleApiClient.builder()
                .withBaseUrl(baseUrl)
                .withClientKeyStore(clientKeyStore)
                .withClientKeyStoreProtectionParameter(protectionParameter)
                .withTrustKeyStore(trustKeyStore)
                .build();

        Capsule capsule = new Capsule();

        KeyPair senderKeyPair = EllipticCurve.SECP384R1.generateEcKeyPair();
        ECPublicKey senderPubKey = (ECPublicKey) senderKeyPair.getPublic();

        PublicKey pubKey = cert.getPublicKey();

        if (pubKey instanceof ECPublicKey publicKey) {
            capsule.capsuleType(Capsule.CapsuleTypeEnum.ECC_SECP384R1);
            // Client public key TLS encoded and base64 encoded from id-kaart
            //recipient must match to client's cert pub key or GET will fail with 404
            capsule.recipientId(ECKeys.encodeEcPubKeyForTls(publicKey));
            capsule.ephemeralKeyMaterial(ECKeys.encodeEcPubKeyForTls(senderPubKey));
        } else if (pubKey instanceof RSAPublicKey publicKey) {
            capsule.capsuleType(Capsule.CapsuleTypeEnum.RSA);
            capsule.recipientId(RsaUtils.encodeRsaPubKey(publicKey));
            capsule.ephemeralKeyMaterial(senderPubKey.getEncoded());
        }

        String id = this.saveCapsule(capsule, EXPIRY_TIME).getTransactionId();

        assertNotNull(id);

        var serverCapsule = client.getCapsule(id);
        assertTrue(serverCapsule.isPresent());

        // comparing client.model.Capsule and server.model.Capsule.
        // Since these are different classes, can't do simple equals
        assertArrayEquals(capsule.getRecipientId(), serverCapsule.get().getRecipientId());
        assertArrayEquals(capsule.getEphemeralKeyMaterial(), serverCapsule.get().getEphemeralKeyMaterial());
        assertEquals(String.valueOf(capsule.getCapsuleType()), String.valueOf(serverCapsule.get().getCapsuleType()));
    }

    @Test
    void shouldGetRsaCapsule() throws Exception {
        var recipientCert = PemTools.loadCertificate(
            new ByteArrayInputStream(
                Files.readAllBytes(TestData.getKeysDirectory().resolve("rsa/client-rsa-2048-cert.pem")
                    .toAbsolutePath())
            )
        );

        var rsaCapsule = new Capsule()
            .capsuleType(Capsule.CapsuleTypeEnum.RSA)
            .ephemeralKeyMaterial(UUID.randomUUID().toString().getBytes())
            .recipientId(RsaUtils.encodeRsaPubKey((RSAPublicKey) recipientCert.getPublicKey()));

        String txId = this.saveCapsule(rsaCapsule, EXPIRY_TIME).getTransactionId();

        var response = this.restTemplate.getForEntity(
            new URI(this.capsuleApiUrl() + "/" + txId),
            Capsule.class
        );

        assertNotNull(response);
        assertNotNull(response.getBody());

        assertEquals(rsaCapsule.getCapsuleType(), response.getBody().getCapsuleType());
        assertArrayEquals(rsaCapsule.getRecipientId(), response.getBody().getRecipientId());
        assertArrayEquals(rsaCapsule.getEphemeralKeyMaterial(), response.getBody().getEphemeralKeyMaterial());
    }

    @Test
    void shouldGetHttp400() throws Exception {
        // constraint errors should be converted to HTTP 400, see GlobalExceptionHandler

        String txId = "KC123"; // too short tx
        URI requestUri = new URI(this.capsuleApiUrl() + "/" + txId);

        HttpClientErrorException ex = assertThrows(
            HttpClientErrorException.class,
            () -> this.restTemplate.getForEntity(requestUri, Capsule.class)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void shouldGetHttp404() throws Exception {
        String txId = "KC12345678901234567890"; // certificate provided and passes validation, but capsule not found
        URI requestUri = new URI(this.capsuleApiUrl() + "/" + txId);
        HttpClientErrorException ex = assertThrows(
            HttpClientErrorException.class,
            () -> this.restTemplate.getForEntity(requestUri, Capsule.class)
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void shouldThrowUserExceptions() throws Exception {
        // unknown serverId should throw exception
        var client = createPkcs12ServerClient(baseUrl);
        String nonExistingUuid = UUID.randomUUID().toString();

        CDocUserException notFoundException = assertThrows(
            CDocUserException.class,
            () -> client.getForId(nonExistingUuid)
        );
        assertEquals(UserErrorCode.SERVER_NOT_FOUND, notFoundException.getErrorCode());

        // test network error
        var misconfiguredClient = createPkcs12ServerClient("https://foo");
        String uuid = UUID.randomUUID().toString();

        CDocUserException networkException = assertThrows(
            CDocUserException.class,
            () -> misconfiguredClient.getCapsule(uuid)
        );
        assertEquals(UserErrorCode.NETWORK_ERROR, networkException.getErrorCode());
    }

    @Test
    void shouldFailWithNotFoundError() throws Exception {
        String txId = "KC12345678901234567890"; // certificate provided and passes validation, but capsule not found

        Cdoc2KeyCapsuleApiClient client = createClientWithPkcs12();

        assertThrows(
            ApiException.class,
            () -> client.getCapsule(txId)
        );
    }

    private static KeyCapsuleClientImpl createPkcs12ServerClient(String serverBaseUrl) throws Exception {
        String prop = "cdoc2.client.server.id=testKeyServerPropertiesClientPKCS12\n";
        prop += "cdoc2.client.server.base-url.post=" + serverBaseUrl + "\n";
        prop += "cdoc2.client.server.base-url.get=" + serverBaseUrl + "\n";
        prop += "cdoc2.client.ssl.trust-store.type=JKS\n";
        prop += "cdoc2.client.ssl.trust-store=" + TestData.getKeysDirectory().resolve("clienttruststore.jks") + "\n";
        prop += "cdoc2.client.ssl.trust-store-password=passwd\n";

        prop += "cdoc2.client.ssl.client-store.type=PKCS12\n";
        prop += "cdoc2.client.ssl.client-store=" + TestData.getKeysDirectory().resolve("cdoc2client.p12") + "\n";
        prop += "cdoc2.client.ssl.client-store-password=passwd\n";

        Properties p = new Properties();
        p.load(new StringReader(prop));

        return (KeyCapsuleClientImpl) KeyCapsuleClientImpl.create(p);
    }

    private Cdoc2KeyCapsuleApiClient createClientWithPkcs12() throws GeneralSecurityException {
        KeyStore clientKeyStore = null;
        KeyStore trustKeyStore = null;
        try {
            clientKeyStore = KeyStore.getInstance("PKCS12");
            clientKeyStore.load(Files.newInputStream(TestData.getKeysDirectory().resolve("cdoc2client.p12")),
                "passwd".toCharArray());
            clientKeyStore.aliases().asIterator().forEachRemaining(a -> log.debug("client KS alias: {}", a));

            trustKeyStore = KeyStore.getInstance("JKS");
            trustKeyStore.load(Files.newInputStream(TestData.getKeysDirectory().resolve("clienttruststore.jks")),
                "passwd".toCharArray());
            trustKeyStore.aliases().asIterator().forEachRemaining(a -> log.debug("client trust KS alias: {}", a));
        }  catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
            log.error("Error initializing key stores", e);
        }

        return Cdoc2KeyCapsuleApiClient.builder()
            .withBaseUrl(baseUrl)
            .withClientKeyStore(clientKeyStore)
            .withClientKeyStorePassword("passwd".toCharArray())
            .withTrustKeyStore(trustKeyStore)
            .build();
    }

}
