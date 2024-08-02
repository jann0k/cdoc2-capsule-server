package ee.cyber.cdoc2.server;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Properties;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * Input test data utility class.
 */
@Slf4j
public final class TestData {
    private TestData() {
        // utility class
    }

    @SneakyThrows
    public static Path getKeysDirectory() {
        Properties prop = new Properties();
        //generated during maven generate-test-resources phase, see pom.xml
        String windowsPathEscape = new String(TestData.class.getClassLoader()
                .getResourceAsStream("test.properties").readAllBytes());
        prop.load(new StringReader(windowsPathEscape.replace("\\", "\\\\")));
        String keysProperty = prop.getProperty("cdoc2.keys.dir");
        log.debug("Value for property cdoc2.keys.dir is {}", keysProperty);
        Path keysPath = Path.of(keysProperty).normalize();
        log.debug("Loading keys/certs from {}", keysPath);
        return keysPath;
    }

    @SneakyThrows
    public static KeyStore loadKeyStore(String keyStoreType, Path keyStoreFile, String keyStorePassword) {
        log.debug("loadKeyStore({}, {})", keyStoreType, keyStoreFile);
        try {
            var keyStore = KeyStore.getInstance(keyStoreType);
            keyStore.load(Files.newInputStream(keyStoreFile), keyStorePassword.toCharArray());

            keyStore.aliases().asIterator().forEachRemaining(a -> log.debug("Alias in keystore: {}", a));
            return keyStore;
        } catch (GeneralSecurityException | IOException e) {
            log.error("Error initializing key stores", e);
            throw e;
        }
    }
}
