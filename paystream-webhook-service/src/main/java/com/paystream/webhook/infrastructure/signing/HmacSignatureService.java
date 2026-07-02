package com.paystream.webhook.infrastructure.signing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * Computes HMAC-SHA256 signatures for webhook delivery.
 *
 * Signing input: "{timestamp}.{jsonBody}"
 * Header: X-PayStream-Signature: sha256={hex_signature}
 * Header: X-PayStream-Timestamp: {unix_epoch_seconds}
 *
 * Recipients verify by recomputing with the shared secret.
 * The timestamp prevents replay attacks (reject if |now - timestamp| > 5 minutes).
 */
@Component
public class HmacSignatureService {

    private static final Logger log = LoggerFactory.getLogger(HmacSignatureService.class);
    private static final String ALGORITHM = "HmacSHA256";

    /**
     * Computes sha256 hex signature.
     *
     * @param secret    Endpoint secret (hex or plain string)
     * @param timestamp Unix epoch seconds
     * @param body      Raw JSON body string
     * @return hex-encoded HMAC-SHA256 of "{timestamp}.{body}"
     */
    public String sign(String secret, long timestamp, String body) {
        try {
            String signingInput = timestamp + "." + body;
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] raw = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (Exception e) {
            log.error("HMAC signing failed: {}", e.getMessage());
            throw new IllegalStateException("Failed to compute HMAC signature", e);
        }
    }

    /** Returns the full header value: "sha256={signature}" */
    public String signatureHeader(String secret, long timestamp, String body) {
        return "sha256=" + sign(secret, timestamp, body);
    }
}
