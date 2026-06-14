package com.paystream.auth.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Exposes the RSA public key in JWKS format so the API gateway can verify
 * JWT signatures without calling auth-service per-request.
 * This endpoint must remain publicly accessible.
 */
@RestController
public class JwksController {

    private final PublicKey publicKey;

    public JwksController(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        RSAPublicKey rsaPublicKey = (RSAPublicKey) publicKey;

        String n = encodeBase64Url(rsaPublicKey.getModulus());
        String e = encodeBase64Url(rsaPublicKey.getPublicExponent());

        Map<String, String> key = Map.of(
                "kty", "RSA",
                "use", "sig",
                "alg", "RS256",
                "n",   n,
                "e",   e
        );

        return Map.of("keys", List.of(key));
    }

    private String encodeBase64Url(BigInteger value) {
        byte[] bytes = value.toByteArray();
        // Strip leading zero byte that BigInteger may add for sign
        if (bytes[0] == 0) {
            byte[] trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            bytes = trimmed;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
