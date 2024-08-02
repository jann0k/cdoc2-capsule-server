package ee.cyber.cdoc2.server.scenarios;

import ee.cyber.cdoc2.server.TestDataGenerator;
import ee.cyber.cdoc2.server.conf.TestConfig;
import ee.cyber.cdoc2.server.dto.GeneratedCapsule;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.bodyLength;
import static io.gatling.javaapi.core.CoreDsl.bodyString;
import static io.gatling.javaapi.core.CoreDsl.doIfOrElse;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.header;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * Base class for key capsule test scenarios
 */
@RequiredArgsConstructor
@SuppressWarnings("unchecked") // compiler warning come from usage of Gatling API
@Slf4j
public abstract class KeyCapsuleScenarios {
    private static final Random RANDOM = new Random();

    // context path of the API (
    protected static final String API_ENDPOINT = "/key-capsules";

    protected final TestConfig testConf;
    protected final TestDataGenerator testData;

    // holds latest sent data for each user (used in verifying that the same data was received back)
    protected final ConcurrentHashMap<Long, GeneratedCapsule> sentData = new ConcurrentHashMap<>();
    // holds received urls (with transaction ids) for created capsules
    protected final Set<String> createdCapsuleUrls = ConcurrentHashMap.newKeySet();

    // key capsule transactionId min length
    protected static final int TX_ID_MIN_LENGTH = 18;
    // key capsule transactionId max length
    protected static final int TX_ID_MAX_LENGTH = 34;

    // key capsule key material max length in bytes
    protected static final int KEY_MATERIAL_MAX_LENGTH = 2100;

    // Gatling session variables
    protected static final String LOCATION = "Location";

    public ScenarioBuilder getRandomKeyCapsule(String scenarioName) {
        return scenario(scenarioName).exec(this.getRandomKeyCapsule);
    }

    /**
     * Sends the capsule and verifies successful response.
     */
    protected ChainBuilder sendKeyCapsuleCheckSuccess(Function<Long, GeneratedCapsule> capsuleGenerator,
            String requestName) {
        return exec(
            http(requestName)
                .post(this.testConf.getPutServerBaseUrl() + API_ENDPOINT)
                .body(StringBody(session -> {
                    var userId = session.userId();
                    var capsule = capsuleGenerator.apply(userId);
                    this.sentData.put(userId, capsule);
                    return TestDataGenerator.toJson(capsule.request());
                })).asJson()
                .check(
                    status().is(HttpResponseStatus.CREATED.code()),
                    header(LOCATION).exists().saveAs(LOCATION),
                    header(LOCATION).transformWithSession((location, session) -> {
                        // save the created capsule url
                        this.createdCapsuleUrls.add(location);
                        return location;
                    })
                )
            ).exitHereIfFailed();
    }

    /**
     * Sends the capsule and verifies that an error response was received.
     */
    protected ChainBuilder sendKeyCapsuleCheckError(Function<Long, GeneratedCapsule> capsuleGenerator,
            String requestName, HttpResponseStatus errorResponse) {
        return exec(
            http(requestName)
                .post(this.testConf.getPutServerBaseUrl() + API_ENDPOINT)
                .body(StringBody(session -> {
                    var userId = session.userId();
                    var capsule = capsuleGenerator.apply(userId);
                    return TestDataGenerator.toJson(capsule.request());
                })).asJson()
                .check(
                    status().not(HttpResponseStatus.OK.code()),
                    status().not(HttpResponseStatus.CREATED.code()),
                    status().is(errorResponse.code()),
                    header(LOCATION).notExists() // location header must not exist
                )
            ).exitHereIfFailed();
    }

    protected ChainBuilder checkLatestKeyCapsule(String testCaseName) {
        return exec(
            http(testCaseName)
                .get(session -> this.testConf.getGetServerBaseUrl() + session.getString(LOCATION))
                .check(
                    status().is(HttpResponseStatus.OK.code()),
                    // check that the same data we sent is returned
                    bodyString().is(session -> TestDataGenerator.toJson(
                        this.getSentData(session.userId()).request()
                    ))
                )
        ).exitHereIfFailed();
    }

    protected ChainBuilder checkKeyCapsuleMismatch(String testCaseName) {
        return exec(
            http(testCaseName)
                .get(session -> this.testConf.getGetServerBaseUrl() + session.getString(LOCATION))
                .check(
                    status().is(HttpResponseStatus.NOT_FOUND.code()),
                    bodyLength().is(0)
                )
        ).exitHereIfFailed();
    }

    // sends a request with the given transaction id and checks it to be handled as invalid input
    protected ChainBuilder checkInvalidTransactionId(String testId, String transactionId,
            HttpResponseStatus expectedResponse) {
        return exec(
            http(testId + " - with invalid txId '" + transactionId + "'")
                .get(this.testConf.getGetServerBaseUrl() + API_ENDPOINT + '/' + transactionId)
                .check(
                    status().is(expectedResponse.code()),
                    bodyLength().is(0)
                )
        );
    }

    protected ChainBuilder checkEmptyTransactionId(
        String testId,
        String transactionId,
        HttpResponseStatus expectedResponse
    ) {
        return exec(
            http(testId + " - with empty txId '" + transactionId + "'")
                .get(this.testConf.getGetServerBaseUrl() + API_ENDPOINT + '/' + transactionId)
                .check(
                    status().is(expectedResponse.code())
                )
        );
    }

    protected ChainBuilder checkMissingTransactionIdAndUriSlash(
        String testId,
        HttpResponseStatus expectedResponse
    ) {
        return exec(
            http(testId + " - with missing txId")
                .get(this.testConf.getGetServerBaseUrl() + API_ENDPOINT)
                .check(
                    status().is(expectedResponse.code())
                )
        );
    }

    protected GeneratedCapsule getSentData(Long userId) {
        return Optional.ofNullable(this.sentData.get(userId))
            .orElseThrow(() -> new RuntimeException("No sent data for user " + userId));
    }

    private ChainBuilder getRandomKeyCapsule =
        doIfOrElse(session -> !this.createdCapsuleUrls.isEmpty()).then(
            exec(
                http("Get random previously created key capsule")
                    .get(session -> this.getRandomSavedCapsuleUrl())
                    .check(
                        status().in(
                            HttpResponseStatus.OK.code(), // client key matches recipient pub key
                            HttpResponseStatus.NOT_FOUND.code() // client key mismatch
                        )
                    )
            )
            .exitHereIfFailed()
        ).orElse(
            exec(session -> {
                log.error("Failed to get random capsule: no capsules created yet");
                return session;
            })
        );

    private String getRandomSavedCapsuleUrl() {
        if (this.createdCapsuleUrls.isEmpty()) {
            throw new IllegalArgumentException("No capsule urls saved yet");
        }
        var urls = this.createdCapsuleUrls.toArray(new String[0]);
        return urls[RANDOM.nextInt(urls.length)];
    }
}
