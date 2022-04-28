package ee.cyber.cdoc20.crypto;

import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.custom.sec.SecP384R1Curve;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.KeyAgreement;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.PasswordCallback;
import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyManagementException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * EC keys loading, decoding and encoding. Currently, supports only secp384r1 EC keys.
 */
public final class ECKeys {
    public static final String EC_ALGORITHM_NAME = "EC";

    //https://docs.oracle.com/en/java/javase/17/security/oracle-providers.html#GUID-091BF58C-82AB-4C9C-850F-1660824D5254
    public static final String SECP_384_R_1 = "secp384r1";
    public static final String SECP_384_OID = "1.3.132.0.34";

    /**
     * Key length for secp384r1 curve in bytes
     */
    public static final int SECP_384_R_1_LEN_BYTES = 384 / 8;



    /**
     * Curve values from {@link ee.cyber.cdoc20.fbs.recipients.EllipticCurve} defined as enum and mapped to
     * known elliptic curve names and oid's
     */
    public enum EllipticCurve {
        UNKNOWN(ee.cyber.cdoc20.fbs.recipients.EllipticCurve.UNKNOWN, null, null),
        secp384r1(ee.cyber.cdoc20.fbs.recipients.EllipticCurve.secp384r1, SECP_384_R_1, SECP_384_OID);

        private final byte value;
        private final String name;
        private final String oid;


        EllipticCurve(byte value, String name, String oid) {
            this.value = value;
            this.name = name;
            this.oid = oid;
        }
        public byte getValue() {
            return value;
        }

        public String getName() {
            return name;
        }
        public String getOid() {
            return oid;
        }

        public boolean isValidKey(ECPublicKey key) throws GeneralSecurityException {
            switch (this) {
                case secp384r1:
                    return isValidSecP384R1(key);
                default:
                    throw new IllegalStateException("isValidKey not implemented for " + this);
            }
        }

        public boolean isValidKeyPair(KeyPair keyPair) throws GeneralSecurityException {
            switch (this) {
                case secp384r1:
                    return isECSecp384r1(keyPair);
                default:
                    throw new IllegalStateException("isValidKeyPair not implemented for " + this);
            }
        }

        /**Key length in bytes. For secp384r1, its 384/8=48*/
        public int getKeyLength() {
            switch (this) {
                case secp384r1:
                    return SECP_384_R_1_LEN_BYTES;
                default:
                    throw new IllegalStateException("getKeyLength not implemented for " + this);
            }
        }

        public ECPublicKey decodeFromTls(ByteBuffer encoded) throws GeneralSecurityException {
            switch (this) {
                case secp384r1:
                    // calls also isValidSecP384R1
                    return decodeSecP384R1EcPublicKeyFromTls(encoded);
                default:
                    throw new IllegalStateException("decodeFromTls not implemented for " + this);
            }
        }

        public KeyPair generateEcKeyPair() throws GeneralSecurityException {
            return ECKeys.generateEcKeyPair(this.getName());
        }


        public static EllipticCurve forName(String name) {
            if (SECP_384_R_1.equalsIgnoreCase(name)) {
                return secp384r1;
            }
            throw new IllegalArgumentException("Unknown curve name " + name);
        }

        public static EllipticCurve forOid(String oid) {
            if (SECP_384_OID.equals(oid)) {
                return secp384r1;
            }
            throw new IllegalArgumentException("Unknown curve oid " + oid);
        }

        public static EllipticCurve forValue(byte value) {
            switch (value) {
                case ee.cyber.cdoc20.fbs.recipients.EllipticCurve.secp384r1:
                    return secp384r1;
                default:
                    throw new IllegalArgumentException("Unknown curve value " + value);
            }
        }

        /**Supported curve names*/
        public static String[] names() {
            return ee.cyber.cdoc20.fbs.recipients.EllipticCurve.names;
        }

    }

    // for validating that decoded ECPoints are valid for secp384r1 curve
    private static final ECCurve SECP_384_R_1_CURVE = new SecP384R1Curve();




    private static final Logger log = LoggerFactory.getLogger(ECKeys.class);

    private ECKeys() {
    }

    public static KeyPair generateEcKeyPair(String ecCurveName) throws GeneralSecurityException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(EC_ALGORITHM_NAME);
        keyPairGenerator.initialize(new ECGenParameterSpec(ecCurveName));
        return keyPairGenerator.generateKeyPair();
    }

    public static byte[] encodeEcPubKeyForTls(EllipticCurve curve, ECPublicKey ecPublicKey) {
        int keyLength = curve.getKeyLength();
        return encodeEcPubKeyForTls(ecPublicKey, keyLength);
    }
    /**
     * Encode EcPublicKey in TLS 1.3 format https://datatracker.ietf.org/doc/html/rfc8446#section-4.2.8.2
     * @param ecPublicKey EC public key
     * @return ecPublicKey encoded in TLS 1.3 EC pub key format
     */
    public static byte[] encodeEcPubKeyForTls(ECPublicKey ecPublicKey) throws GeneralSecurityException {
        EllipticCurve curve = EllipticCurve.forOid(ECKeys.getCurveOid(ecPublicKey));
        int keyLength = curve.getKeyLength();
        return encodeEcPubKeyForTls(ecPublicKey, keyLength);
    }

    @SuppressWarnings("checkstyle:LineLength")
    private static byte[] encodeEcPubKeyForTls(ECPublicKey ecPublicKey, int keyLength) {
        byte[] xBytes = toUnsignedByteArray(ecPublicKey.getW().getAffineX(), keyLength);
        byte[] yBytes = toUnsignedByteArray(ecPublicKey.getW().getAffineY(), keyLength);

        //CHECKSTYLE:OFF
        //EC pubKey in TLS 1.3 format
        //https://datatracker.ietf.org/doc/html/rfc8446#section-4.2.8.2
        //https://github.com/bcgit/bc-java/blob/526b5846653100fc521c1a68c02dbe9df3347a29/core/src/main/java/org/bouncycastle/math/ec/ECCurve.java#L410
        //CHECKSTYLE:ON
        byte[] tlsPubKey = new byte[1 + xBytes.length + yBytes.length];
        tlsPubKey[0] = 0x04; //uncompressed

        System.arraycopy(xBytes, 0, tlsPubKey, 1, xBytes.length);
        System.arraycopy(yBytes, 0, tlsPubKey,  1 + xBytes.length, yBytes.length);

        return tlsPubKey;
    }

    private static ECPublicKey decodeSecP384R1EcPublicKeyFromTls(ByteBuffer encoded) throws GeneralSecurityException {
        return decodeSecP384R1EcPublicKeyFromTls(
                Arrays.copyOfRange(encoded.array(), encoded.position(), encoded.limit()));
    }

    /**
     * Decode EcPublicKey from TLS 1.3 format https://datatracker.ietf.org/doc/html/rfc8446#section-4.2.8.2
     * @param encoded EC public key octets encoded as in TLS 1.3 format. Expects key to be part of secp384r1 curve
     * @return decoded ECPublicKey
     * @throws GeneralSecurityException
     */
    private static ECPublicKey decodeSecP384R1EcPublicKeyFromTls(byte[] encoded)
            throws GeneralSecurityException {

        String encodedHex = HexFormat.of().formatHex(encoded);
        final int expectedLength = SECP_384_R_1_LEN_BYTES;
        if (encoded.length != (2 * expectedLength + 1)) {

            log.error("Invalid pubKey len {}, expected {}, encoded: {}", encoded.length, (2 * expectedLength + 1),
                    encodedHex);
            throw new IllegalArgumentException("Incorrect length for uncompressed encoding");
        }

        if (encoded[0] != 0x04) {
            log.error("Illegal EC pub key encoding. Encoded: {}", encodedHex);
            throw new IllegalArgumentException("Invalid encoding");
        }

        BigInteger x = new BigInteger(1, Arrays.copyOfRange(encoded, 1, expectedLength + 1));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(encoded, expectedLength + 1, encoded.length));

        ECPoint pubPoint = new ECPoint(x, y);
        AlgorithmParameters params = AlgorithmParameters.getInstance(EC_ALGORITHM_NAME);
        params.init(new ECGenParameterSpec(SECP_384_R_1));

        ECParameterSpec ecParameters = params.getParameterSpec(ECParameterSpec.class);
        ECPublicKeySpec pubECSpec = new ECPublicKeySpec(pubPoint, ecParameters);
        ECPublicKey ecPublicKey = (ECPublicKey) KeyFactory.getInstance(EC_ALGORITHM_NAME).generatePublic(pubECSpec);
        if (!isValidSecP384R1(ecPublicKey)) {
            throw new InvalidKeyException("Not valid secp384r1 EC public key " + encodedHex);
        }
        return ecPublicKey;
    }

    private static byte[] toUnsignedByteArray(BigInteger bigInteger, int len) {
        //https://stackoverflow.com/questions/4407779/biginteger-to-byte
        byte[] array = bigInteger.toByteArray();
        if ((array[0] == 0) && (array.length == len + 1)) {
            return Arrays.copyOfRange(array, 1, array.length);
        } else if (array.length < len) {
            byte[] padded = new byte[len];
            System.arraycopy(array, 0, padded, len - array.length, array.length);
            return padded;
        } else {
            if (array.length != len) {
                log.warn("Expected EC key to be {} bytes, but was {}. bigInteger: {}",
                        len, array.length, bigInteger.toString(16));
            }
            return array;
        }
    }

    /**
     * Load OpenSSL generated EC private key
     * openssl ecparam -name secp384r1 -genkey -noout -out key.pem
     * <code>
     * -----BEGIN EC PRIVATE KEY-----
     * MIGkAgEBBDBh1UAT832Nh2ZXvdc5JbNv3BcEZSYk90esUkSPFmg2XEuoA7avS/kd
     * 4HtHGRbRRbagBwYFK4EEACKhZANiAASERl1rD+bm2aoiuGicY8obRkcs+jt8ks4j
     * C1jD/f/EQ8KdFYrJ+KwnM6R8rIXqDnUnLJFiF3OzDpu8TUjVOvdXgzQL+n67QiLd
     * yerTE6f5ujIXoXNkZB8O2kX/3vADuDA=
     * -----END EC PRIVATE KEY-----
     * </code>
     * @param openSslPem OpenSSL generated EC private key in PEM
     * @return EC private key loaded from openSslPem
     */
    public static ECPrivateKey loadECPrivateKey(String openSslPem)
            throws GeneralSecurityException, IOException {

        KeyPair keyPair = loadFromPem(openSslPem);
        if (!isECSecp384r1(keyPair)) {
            throw new IllegalArgumentException("Not EC key pair");
        }

        return (ECPrivateKey)keyPair.getPrivate();
    }

    /**
     * openssl ec -in key.pem -pubout -out public.pem
     * <code>
     * -----BEGIN PUBLIC KEY-----
     * MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEhEZdaw/m5tmqIrhonGPKG0ZHLPo7fJLO
     * IwtYw/3/xEPCnRWKyfisJzOkfKyF6g51JyyRYhdzsw6bvE1I1Tr3V4M0C/p+u0Ii
     * 3cnq0xOn+boyF6FzZGQfDtpF/97wA7gw
     * -----END PUBLIC KEY-----
     * <code/>
     *
     * ASN.1:
     * <pre>
         SEQUENCE (2 elem)
           SEQUENCE (2 elem)
               OBJECT IDENTIFIER 1.2.840.10045.2.1 ecPublicKey (ANSI X9.62 public key type)
               OBJECT IDENTIFIER 1.3.132.0.34 secp384r1 (SECG (Certicom) named elliptic curve)
           BIT STRING (776 bit) 0000010001111001011000011010011100101001101001111001000111111000011010…
     * </pre>
     * @param openSslPem
     * @return
     */
    public static ECPublicKey loadECPublicKey(String openSslPem)
            throws GeneralSecurityException {
        Pattern pattern = Pattern.compile("(?s)-----BEGIN PUBLIC KEY-----.*-----END PUBLIC KEY-----");
        Matcher matcher = pattern.matcher(openSslPem);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Public key not found");
        }
        String pubKeyPem = matcher.group()
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        //SEQUENCE (2 elem)
        //  SEQUENCE (2 elem)
        //      OBJECT IDENTIFIER 1.2.840.10045.2.1 ecPublicKey (ANSI X9.62 public key type)
        //      OBJECT IDENTIFIER 1.3.132.0.34 secp384r1 (SECG (Certicom) named elliptic curve)
        //  BIT STRING (776 bit) 0000010001111001011000011010011100101001101001111001000111111000011010…
        X509EncodedKeySpec x509EncodedKeySpec = new X509EncodedKeySpec(Base64.getDecoder().decode(pubKeyPem));
        ECPublicKey ecPublicKey = (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(x509EncodedKeySpec);
        if (!isValidSecP384R1(ecPublicKey)) {
            throw new IllegalArgumentException("Not valid secp384r1 public key");
        }
        return ecPublicKey;
    }

    public static String getCurveOid(ECKey key)
            throws NoSuchAlgorithmException, InvalidParameterSpecException, NoSuchProviderException {

        AlgorithmParameters params = AlgorithmParameters.getInstance("EC", "SunEC");
        params.init(key.getParams());

        // JavaDoc NamedParameterSpec::getName() : Returns the standard name that determines the algorithm parameters.
        // and https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html#parameterspec-names
        // lists "secp384r1" as standard name.
        // But in practice SunEC and BC both return "1.3.132.0.34"
        return params.getParameterSpec(ECGenParameterSpec.class).getName();
    }

    public static boolean isEcSecp384r1Curve(ECKey key)
            throws GeneralSecurityException {

    //https://docs.oracle.com/en/java/javase/17/security/oracle-providers.html#GUID-091BF58C-82AB-4C9C-850F-1660824D5254
    // Table 4-28 Recommended Curves Provided by the SunEC Provider
        final String[] secp384r1Names = {SECP_384_OID, SECP_384_R_1, "NIST P-384"};
        String oid = getCurveOid(key);
        return Arrays.asList(secp384r1Names).contains(oid);
    }

    /**
     * Load EC key pair from OpenSSL generated PEM file:
     * openssl ecparam -name secp384r1 -genkey -noout -out key.pem
     * Example key PEM:
     * <pre>
     * -----BEGIN EC PRIVATE KEY-----
     * MIGkAgEBBDBh1UAT832Nh2ZXvdc5JbNv3BcEZSYk90esUkSPFmg2XEuoA7avS/kd
     * 4HtHGRbRRbagBwYFK4EEACKhZANiAASERl1rD+bm2aoiuGicY8obRkcs+jt8ks4j
     * C1jD/f/EQ8KdFYrJ+KwnM6R8rIXqDnUnLJFiF3OzDpu8TUjVOvdXgzQL+n67QiLd
     * yerTE6f5ujIXoXNkZB8O2kX/3vADuDA=
     * -----END EC PRIVATE KEY-----
     * </pre>
     * Decoded PEM has ASN.1 structure:
     * <pre>
     SEQUENCE (4 elem)
     INTEGER 1
     OCTET STRING (48 byte) 61D54013F37D8D876657BDD73925B36FDC1704652624F747AC52448F1668365C4BA803…
     [0] (1 elem)
     OBJECT IDENTIFIER 1.3.132.0.34 secp384r1 (SECG (Certicom) named elliptic curve)
     [1] (1 elem)
     BIT STRING (776 bit) 0000010010000100010001100101110101101011000011111110011011100110110110…
     </pre>
     *
     * @param pem OpenSSL generated EC private key in PEM
     * @return EC KeyPair decoded from PEM
     */
    public static KeyPair loadFromPem(String pem)
            throws GeneralSecurityException, IOException {

        Object parsed = new PEMParser(new StringReader(pem)).readObject();
        KeyPair pair = new JcaPEMKeyConverter().getKeyPair((org.bouncycastle.openssl.PEMKeyPair)parsed);
        if (!isECSecp384r1(pair)) {
            throw new IllegalArgumentException("Not EC keypair with secp384r1 curve");
        }
        return pair;
    }


    /**
     * Load KeyPair using automatically generated SunPKCS11 configuration and the default callback to get the pin.
     * Not thread-safe
     *
     * Common openSC library locations:
     * <ul>
     *   <li>For Windows, it could be C:\Windows\SysWOW64\opensc-pkcs11.dll,
     *   <li>For Linux, it could be /usr/lib/x86_64-linux-gnu/opensc-pkcs11.so,
     *   <li>For OSX, it could be /usr/local/lib/opensc-pkcs11.so
     * </ul>
     * @param openScLibPath OpenSC library location, defaults above if null
     * @param slot Slot, default 0
     * @see <a href="https://docs.oracle.com/en/java/javase/17/security/pkcs11-reference-guide1.html">
     *     SunPKCS11 documentation Table 5-1</a>
     */
    public static KeyPair loadFromPKCS11Interactively(String openScLibPath, Integer slot)
            throws GeneralSecurityException, IOException {

        String pinPrompt;
        if (slot == null) {
            pinPrompt = "PIN1:";
        } else {
            pinPrompt = "PIN" + (slot + 1) + ":";
        }

        KeyStore.CallbackHandlerProtection cbHandlerProtection = new KeyStore.CallbackHandlerProtection(callbacks -> {

            for (Callback cp: callbacks) {
                if (cp instanceof PasswordCallback) {
                    // prompt the user for sensitive information
                    PasswordCallback pc = (PasswordCallback)cp;

                    java.io.Console console = System.console();
                    if (console != null) {
                        char[] pin = console.readPassword(pinPrompt);
                        pc.setPassword(pin);
                    } else { //running from IDE, console is null
                        JPasswordField pf = new JPasswordField();
                        int okCxl = JOptionPane.showConfirmDialog(null, pf, pinPrompt,
                                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

                        if (okCxl == JOptionPane.OK_OPTION) {
                            String password = new String(pf.getPassword());
                            pc.setPassword(password.toCharArray());
                        }
                    }
                }
            }
        });
        return loadFromPKCS11Interactively(openScLibPath, slot, cbHandlerProtection);
    }

    /**
     * Load KeyPair using automatically generated SunPKCS11 configuration and callback to get the pin. Not thread-safe
     *
     * Common openSC library locations:
     * <ul>
     *   <li>For Windows, it could be C:\Windows\SysWOW64\opensc-pkcs11.dll,
     *   <li>For Linux, it could be /usr/lib/x86_64-linux-gnu/opensc-pkcs11.so,
     *   <li>For OSX, it could be /usr/local/lib/opensc-pkcs11.so
     * </ul>
     * @param openScLibPath OpenSC library location, defaults above if null
     * @param slot Slot, default 0
     * @param cbHandlerProtection the CallbackHandlerProtection used to get the pin interactively
     * @see <a href="https://docs.oracle.com/en/java/javase/17/security/pkcs11-reference-guide1.html">
     *     SunPKCS11 documentation Table 5-1</a>
     */
    public static KeyPair loadFromPKCS11Interactively(String openScLibPath, Integer slot,
                                                      KeyStore.CallbackHandlerProtection cbHandlerProtection)
            throws IOException, GeneralSecurityException {
        Path confPath = Crypto.createSunPkcsConfigurationFile(null, openScLibPath, slot);
        return loadFromPKCS11Interactively(confPath, cbHandlerProtection);
    }

    /**
     * Load KeyPair using automatically generated SunPKCS11 configuration and callback to get the pin. Not thread-safe
     *
     * Common openSC library locations:
     * <ul>
     *   <li>For Windows, it could be C:\Windows\SysWOW64\opensc-pkcs11.dll,
     *   <li>For Linux, it could be /usr/lib/x86_64-linux-gnu/opensc-pkcs11.so,
     *   <li>For OSX, it could be /usr/local/lib/opensc-pkcs11.so
     * </ul>
     * @param openScLibPath OpenSC library location, defaults above if null
     * @param slot Slot, default 0
     * @see <a href="https://docs.oracle.com/en/java/javase/17/security/pkcs11-reference-guide1.html">
     *     SunPKCS11 documentation Table 5-1</a>
     */
    public static KeyPair loadFromPKCS11(String openScLibPath, Integer slot, char[] pin)
            throws IOException, GeneralSecurityException {

        Path confPath = Crypto.createSunPkcsConfigurationFile(null, openScLibPath, slot);
        AbstractMap.SimpleEntry<PrivateKey, X509Certificate> pair =
                loadFromPKCS11(confPath, pin, null);
        return new KeyPair(pair.getValue().getPublicKey(), pair.getKey());
    }


    /**
     * Load KeyPair using SunPKCS11 configuration and CallbackHandlerProtection. Not thread-safe
     * @param sunPkcs11ConfPath SunPKCS11 configuration location
     * @param cbHandlerProtection the CallbackHandlerProtection used to get the pin interactively
     * @return the KeyPair loaded from PKCS11 device
     * @see <a href="https://docs.oracle.com/en/java/javase/17/security/pkcs11-reference-guide1.html">
     *     SunPKCS11 documentation Table 5-1</a>
     */
    public static KeyPair loadFromPKCS11Interactively(Path sunPkcs11ConfPath,
                                                      KeyStore.CallbackHandlerProtection cbHandlerProtection)
            throws IOException, GeneralSecurityException {

        AbstractMap.SimpleEntry<PrivateKey, X509Certificate> pair =
                loadFromPKCS11(sunPkcs11ConfPath, null, cbHandlerProtection);
        return new KeyPair(pair.getValue().getPublicKey(), pair.getKey());
    }

    /**
     * Load PrivateKey and Certificate using SunPKCS11 configuration and pin or CallbackHandlerProtection.
     * Not thread-safe
     * @param sunPkcs11ConfPath SunPKCS11 configuration location
     * @param pin pin for reading key from PKCS11
     * @param cbHandlerProtection the CallbackHandlerProtection used to get if pin was provided
     * @return the KeyPair and X509Certificate loaded from PKCS11 device
     * @see <a href="https://docs.oracle.com/en/java/javase/17/security/pkcs11-reference-guide1.html">
     *     SunPKCS11 documentation Table 5-1</a>
     */
    public static AbstractMap.SimpleEntry<PrivateKey, X509Certificate> loadFromPKCS11(
            Path sunPkcs11ConfPath,
            char[] pin,
            KeyStore.CallbackHandlerProtection cbHandlerProtection) throws IOException, GeneralSecurityException {

        if (Crypto.getPkcs11ProviderName() == null) {
            if (!Crypto.initSunPkcs11(sunPkcs11ConfPath)) {
                log.error("Failed to init SunPKCS11 from {}", sunPkcs11ConfPath);
                throw new KeyStoreException("Failed to init SunPKCS11");
            }
        }

        if (Crypto.getPkcs11ProviderName() == null) {
            throw new KeyStoreException("SunPKCS11 not configured or smartcard missing");
        }

        Provider sun = Security.getProvider(Crypto.getPkcs11ProviderName());
        log.debug("{} provider isConfigured={}", sun.getName(), sun.isConfigured());
        log.debug("PKCS11 {}", KeyStore.getInstance("PKCS11", Crypto.getPkcs11ProviderName()).getProvider());
        log.debug("ECDH {}", KeyAgreement.getInstance("ECDH", Crypto.getPkcs11ProviderName()).getProvider());

        KeyStore ks;
        if (cbHandlerProtection == null) {
            if (pin == null) {
                log.warn("PIN not provided");
            }
            ks = KeyStore.getInstance("PKCS11", Crypto.getPkcs11ProviderName());
            ks.load(null, pin);
        } else {
            KeyStore.Builder builder =
                    KeyStore.Builder.newInstance("PKCS11", Crypto.getConfiguredPKCS11Provider(), cbHandlerProtection);
            ks = builder.getKeyStore();
        }

        final List<String> entryNames = new LinkedList<>();
        ks.aliases().asIterator().forEachRemaining(alias -> {
            try {
                log.debug("{} key={} cert={}", alias, ks.isKeyEntry(alias), ks.isCertificateEntry(alias));
                entryNames.add(alias);
            } catch (KeyStoreException e) {
                log.error("KeyStoreException", e);
            }
        });

        if (entryNames.size() != 1) {
            if (entryNames.isEmpty()) {
                log.error("No keys found for {}", Crypto.getPkcs11ProviderName());
            } else {
                log.error("Multiple keys found for {}:{}", Crypto.getPkcs11ProviderName(), entryNames);
            }
            throw new KeyManagementException("No keys or multiple keys found");
        }

        String keyAlias = entryNames.get(0);

        log.info("Loading key \"{}\"", keyAlias);
        KeyStore.PrivateKeyEntry privateKeyEntry =
                (KeyStore.PrivateKeyEntry) ks.getEntry(keyAlias, cbHandlerProtection);
        if (privateKeyEntry == null) {
            log.error("Entry not found {}", keyAlias);
            throw new KeyStoreException("Key not found for " + keyAlias);
        }


        PrivateKey key = privateKeyEntry.getPrivateKey();
        X509Certificate cert = (X509Certificate) privateKeyEntry.getCertificate();

        log.debug("key class: {}", key.getClass());
        log.debug("key: {}", key);
        log.debug("cert: {} ", cert.getSubjectX500Principal().getName());

        return new AbstractMap.SimpleEntry<>(key, cert);
    }

    public static boolean isECSecp384r1(KeyPair keyPair) throws GeneralSecurityException {
        if (!EC_ALGORITHM_NAME.equals(keyPair.getPrivate().getAlgorithm())) {
            log.debug("Not EC key pair. Algorithm is {} (expected EC)", keyPair.getPrivate().getAlgorithm());
            return false;
        }

        if (!EC_ALGORITHM_NAME.equals(keyPair.getPublic().getAlgorithm())) {
            log.debug("Not EC key pair. Algorithm is {} (expected EC)", keyPair.getPublic().getAlgorithm());
            return false;
        }

        ECPublicKey ecPublicKey = (ECPublicKey)keyPair.getPublic();
        if (keyPair.getPrivate() instanceof ECKey) {
            return  isValidSecP384R1(ecPublicKey) && isEcSecp384r1Curve((ECKey) keyPair.getPrivate());
        } else {
            return isValidSecP384R1(ecPublicKey)
                    && Crypto.isECPKCS11Key(keyPair.getPrivate()); //can't get curve for PKCS11 keys
        }
    }

    public static boolean isValidSecP384R1(ECPublicKey ecPublicKey) throws GeneralSecurityException {

        if (!isEcSecp384r1Curve(ecPublicKey)) {
            log.debug("EC pub key curve OID {} is not secp384r1", getCurveOid(ecPublicKey));
            return false;
        }

        // https://neilmadden.blog/2017/05/17/so-how-do-you-validate-nist-ecdh-public-keys/
        // Instead of implementing public key validation, rely on BC validation
        // https://github.com/bcgit/bc-java/blob/master/core/src/main/java/org/bouncycastle/math/ec/ECPoint.java
        org.bouncycastle.math.ec.ECPoint ecPoint = SECP_384_R_1_CURVE.createPoint(ecPublicKey.getW().getAffineX(),
                ecPublicKey.getW().getAffineY());

        boolean onCurve = ecPoint.isValid();
        if (!onCurve) {
            log.debug("EC pub key is not on secp384r1 curve");
        }
        return onCurve;
    }

    /**
     * Read file contents into String
     * @param file file to read
     * @return file contents as String
     * @throws IOException
     */
    public static String readAll(File file) throws IOException {

        return Files.readString(file.toPath());
    }


    /**
     * Load EC key pair from OpenSSL generated PEM file:
     * openssl ecparam -name secp384r1 -genkey -noout -out key.pem
     * Example key PEM:
     * <pre>
     * -----BEGIN EC PRIVATE KEY-----
     * MIGkAgEBBDBh1UAT832Nh2ZXvdc5JbNv3BcEZSYk90esUkSPFmg2XEuoA7avS/kd
     * 4HtHGRbRRbagBwYFK4EEACKhZANiAASERl1rD+bm2aoiuGicY8obRkcs+jt8ks4j
     * C1jD/f/EQ8KdFYrJ+KwnM6R8rIXqDnUnLJFiF3OzDpu8TUjVOvdXgzQL+n67QiLd
     * yerTE6f5ujIXoXNkZB8O2kX/3vADuDA=
     * -----END EC PRIVATE KEY-----
     * </pre>
     * @param pemFile OpenSSL generated EC private key in PEM
     * @return EC KeyPair decoded from PEM
     */
    public static KeyPair loadFromPem(File pemFile)
            throws GeneralSecurityException, IOException {

        return loadFromPem(readAll(pemFile));
    }

    public static ECPublicKey loadECPubKey(File pubPemFile)
            throws GeneralSecurityException, IOException {

        return loadECPublicKey(readAll(pubPemFile));
    }

    public static List<ECPublicKey> loadECPubKeys(File[] pubPemFiles)
            throws GeneralSecurityException, IOException {

        List<ECPublicKey> list = new LinkedList<>();

        if (pubPemFiles != null) {
            for (File f : pubPemFiles) {
                list.add(loadECPubKey(f));
            }
        }
        return list;
    }

    /**
     * Load EC public keys from certificate files
     * @param certDerFiles x509 certificates
     * @return ECPublicKeys loaded from certificates
     * @throws CertificateException if cert file format is invalid
     * @throws IOException if error happens when reading certDerFiles
     */
    public static List<ECPublicKey> loadCertKeys(File[] certDerFiles) throws CertificateException, IOException {

        List<ECPublicKey> list = new LinkedList<>();
        if (certDerFiles != null) {
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            for (File f : certDerFiles) {
                InputStream in = Files.newInputStream(f.toPath());
                X509Certificate cert = (X509Certificate) certFactory.generateCertificate(in);
                ECPublicKey ecPublicKey = (ECPublicKey) cert.getPublicKey();
                list.add(ecPublicKey);
            }
        }

        return list;
    }

}
