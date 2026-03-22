package org.hyvote.plugins.votifier.vote;

import org.hyvote.plugins.votifier.VoteSiteTokenConfig;
import org.hyvote.plugins.votifier.crypto.HmacUtil;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V2VoteParserTest {

    private static final String SERVICE = "TestSite";
    private static final String USERNAME = "PlayerOne";
    private static final String ADDRESS = "127.0.0.1";
    private static final String TOKEN = "super-secret-token";

    @Test
    void parseAcceptsValidSignedPayload() throws Exception {
        Vote vote = V2VoteParser.parse(signedWrapper(buildInnerPayload(1_710_000_000L, null), TOKEN), config());

        assertEquals(SERVICE, vote.serviceName());
        assertEquals(USERNAME, vote.username());
        assertEquals(ADDRESS, vote.address());
        assertEquals(1_710_000_000_000L, vote.timestamp());
    }

    @Test
    void parseRejectsMissingPayloadField() {
        VoteParseException ex = assertThrows(VoteParseException.class,
            () -> V2VoteParser.parse("{\"signature\":\"abc\"}", config()));

        assertTrue(ex.getMessage().contains("payload"));
    }

    @Test
    void parseRejectsMissingSignatureField() {
        VoteParseException ex = assertThrows(VoteParseException.class,
            () -> V2VoteParser.parse("{\"payload\":\"{}\"}", config()));

        assertTrue(ex.getMessage().contains("signature"));
    }

    @Test
    void parseRejectsMissingServiceName() {
        String inner = "{\"username\":\"" + USERNAME + "\",\"address\":\"" + ADDRESS + "\",\"timestamp\":1710000000}";

        VoteParseException ex = assertThrows(VoteParseException.class,
            () -> V2VoteParser.parse(signedWrapper(inner, TOKEN), config()));

        assertTrue(ex.getMessage().contains("serviceName"));
    }

    @Test
    void parseRejectsMissingUsername() {
        String inner = "{\"serviceName\":\"" + SERVICE + "\",\"address\":\"" + ADDRESS + "\",\"timestamp\":1710000000}";

        VoteParseException ex = assertThrows(VoteParseException.class,
            () -> V2VoteParser.parse(signedWrapper(inner, TOKEN), config()));

        assertTrue(ex.getMessage().contains("username"));
    }

    @Test
    void parseRejectsInvalidOuterJson() {
        assertThrows(VoteParseException.class, () -> V2VoteParser.parse("{", config()));
    }

    @Test
    void parseRejectsInvalidInnerJson() {
        String brokenInner = "{\"serviceName\":\"" + SERVICE + "\",\"username\":";
        String wrapper = "{\"payload\":\"" + escapeJson(brokenInner) + "\",\"signature\":\"abc\"}";

        assertThrows(VoteParseException.class, () -> V2VoteParser.parse(wrapper, config()));
    }

    @Test
    void parseRejectsWrongSignature() {
        String wrapper = "{\"payload\":\"" + escapeJson(buildInnerPayload(1_710_000_000L, null)) +
            "\",\"signature\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=\"}";

        assertThrows(V2SignatureException.class, () -> V2VoteParser.parse(wrapper, config()));
    }

    @Test
    void parseRejectsMissingTokenConfiguration() {
        assertThrows(V2SignatureException.class,
            () -> V2VoteParser.parse(signedWrapper(buildInnerPayload(1_710_000_000L, null), TOKEN), VoteSiteTokenConfig.defaults()));
    }

    @Test
    void parseVerifiesMatchingChallengeWhenProvided() throws Exception {
        Vote vote = V2VoteParser.parse(signedWrapper(buildInnerPayload(1_710_000_000L, "challenge-123"), TOKEN),
            config(), "challenge-123");

        assertEquals(USERNAME, vote.username());
    }

    @Test
    void parseRejectsChallengeMismatch() {
        assertThrows(V2ChallengeException.class,
            () -> V2VoteParser.parse(signedWrapper(buildInnerPayload(1_710_000_000L, "actual"), TOKEN),
                config(), "expected"));
    }

    @Test
    void parseRejectsMissingChallengeWhenExpected() {
        assertThrows(V2ChallengeException.class,
            () -> V2VoteParser.parse(signedWrapper(buildInnerPayload(1_710_000_000L, null), TOKEN),
                config(), "expected"));
    }

    @Test
    void parseConvertsSecondsToMillisButLeavesMillisUntouched() throws Exception {
        Vote secondsVote = V2VoteParser.parse(signedWrapper(buildInnerPayload(1_710_000_000L, null), TOKEN), config());
        Vote millisVote = V2VoteParser.parse(signedWrapper(buildInnerPayload(1_710_000_000_123L, null), TOKEN), config());

        assertEquals(1_710_000_000_000L, secondsVote.timestamp());
        assertEquals(1_710_000_000_123L, millisVote.timestamp());
    }

    @Test
    void parseFallsBackToCurrentTimeForInvalidTimestamp() throws Exception {
        long before = System.currentTimeMillis();
        Vote vote = V2VoteParser.parse(signedWrapper(buildInnerPayload(0L, null), TOKEN), config());
        long after = System.currentTimeMillis();

        assertTrue(vote.timestamp() >= before);
        assertTrue(vote.timestamp() <= after);
    }

    private VoteSiteTokenConfig config() {
        return new VoteSiteTokenConfig(Map.of(SERVICE.toLowerCase(), TOKEN));
    }

    private String buildInnerPayload(long timestamp, String challenge) {
        StringBuilder json = new StringBuilder()
            .append("{\"serviceName\":\"").append(SERVICE)
            .append("\",\"username\":\"").append(USERNAME)
            .append("\",\"address\":\"").append(ADDRESS)
            .append("\",\"timestamp\":").append(timestamp);
        if (challenge != null) {
            json.append(",\"challenge\":\"").append(challenge).append("\"");
        }
        return json.append('}').toString();
    }

    private String signedWrapper(String innerPayload, String token) throws Exception {
        String signature = Base64.getEncoder().encodeToString(HmacUtil.computeSignature(innerPayload, token));
        return "{\"payload\":\"" + escapeJson(innerPayload) + "\",\"signature\":\"" + signature + "\"}";
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
