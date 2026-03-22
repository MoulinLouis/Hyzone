package org.hyvote.plugins.votifier.crypto;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HmacUtilTest {

    private static final String PAYLOAD =
        "{\"serviceName\":\"TestSite\",\"username\":\"PlayerOne\",\"address\":\"127.0.0.1\",\"timestamp\":1710000000}";
    private static final String TOKEN = "super-secret-token";
    private static final String EXPECTED_SIGNATURE = "AxfRgF6qhz4iBKTFjYO6XQ6YGKv65NrmuHTWT/JJPJk=";

    @Test
    void computeSignatureMatchesKnownReferenceValue() throws Exception {
        byte[] expected = Base64.getDecoder().decode(EXPECTED_SIGNATURE);

        assertArrayEquals(expected, HmacUtil.computeSignature(PAYLOAD, TOKEN));
    }

    @Test
    void verifySignatureAcceptsMatchingPayloadAndToken() {
        assertTrue(HmacUtil.verifySignature(PAYLOAD, EXPECTED_SIGNATURE, TOKEN));
    }

    @Test
    void verifySignatureRejectsWrongSignature() {
        assertFalse(HmacUtil.verifySignature(PAYLOAD, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", TOKEN));
    }

    @Test
    void verifySignatureRejectsMalformedBase64() {
        assertFalse(HmacUtil.verifySignature(PAYLOAD, "not-base64!!!", TOKEN));
    }

    @Test
    void verifySignatureReturnsFalseOnAnyInternalError() {
        assertFalse(HmacUtil.verifySignature(PAYLOAD, EXPECTED_SIGNATURE, null));
    }
}
