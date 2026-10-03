package com.neha.commercehub.payment;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * A signature proves a message came from someone who knows a shared secret.
 * The gateway signs "orderId|paymentId" with our secret; we compute the same
 * thing ourselves and compare. Anyone without the secret cannot produce a match,
 * so a customer cannot fake a "payment succeeded" message.
 */
public final class Signatures {

    private Signatures() {
    }

    public static String hmacSha256Hex(String data, String secret) {

        try {

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));

        } catch (Exception ex) {

            throw new IllegalStateException("Could not compute signature", ex);
        }
    }

    /** Compares in constant time, so the time taken does not leak how many characters matched. */
    public static boolean matches(String data, String secret, String givenSignature) {

        if (givenSignature == null || secret == null || secret.isEmpty()) {
            return false;
        }

        byte[] expected = hmacSha256Hex(data, secret).getBytes(StandardCharsets.UTF_8);
        byte[] given = givenSignature.trim().toLowerCase().getBytes(StandardCharsets.UTF_8);

        return MessageDigest.isEqual(expected, given);
    }
}
