package ee.cyber.cdoc2.server.scenarios;

/**
 * Test scenario identifiers
 */
public final class ScenarioIdentifiers {
    private ScenarioIdentifiers() {
    }

    public static final String POS_PUT_01 = "ECC-PUT_CAPSULE-POS-01-ONCE";
    public static final String POS_PUT_02 = "RSA-PUT_CAPSULE-POS-01-ONCE";
    public static final String POS_PUT_03 = "ECC-PUT_CAPSULE-POS-02-REPEATEDLY";
    public static final String POS_PUT_04 = "RSA-PUT_CAPSULE-POS-02-REPEATEDLY";
    public static final String POS_PUT_06 = "RSA-PUT-CAPSULE-POS-03-RANDOM_CONTENT";

    public static final String POS_GET_01 = "RSA-GET_CAPSULE-POS-01-CORRECT_REQUEST";
    public static final String POS_GET_02 = "ECC-GET_CAPSULE-POS-01-CORRECT_REQUEST";

    public static final String NEG_GET_02 = "GET_CAPSULE-NEG-02-RANDOM_UUID_TRANSACTION_ID";
    public static final String NEG_GET_03 = "GET_CAPSULE-NEG-03-TOO_SHORT_TRANSACTION_ID";
    public static final String NEG_GET_04 = "GET_CAPSULE-NEG-04-EMPTY_STRING_TRANSACTION_ID";
    public static final String NEG_GET_05 = "GET_CAPSULE-NEG-05-MISSING_TRANSACTION_ID";
    public static final String NEG_GET_06 = "GET_CAPSULE-NEG-06"
        + "-MISSING_TRANSACTION_ID_AND_URI_SLASH";
    public static final String NEG_GET_07 = "GET_CAPSULE-NEG-07"
        + "-TOO_LONG_RANDOM_STRING_TRANSACTION_ID";
    public static final String NEG_GET_08 = "ECC-GET_CAPSULE-NEG-08-PUBLIC_KEY_NOT_MATCHING";
    public static final String NEG_GET_09 = "RSA-GET_CAPSULE-NEG-09-PUBLIC_KEY_NOT_MATCHING";

    public static final String NEG_PUT_01 = "RSA-PUT_CAPSULE-NEG-01-CAPSULE_TOO_BIG";
}
