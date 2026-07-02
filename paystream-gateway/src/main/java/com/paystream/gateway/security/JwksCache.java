package com.paystream.gateway.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import jakarta.annotation.PostConstruct;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fetches and caches the RSA public key from auth-service JWKS endpoint.
 * Refreshes hourly. Gateway JWT validation reads from this cache —
 * never calls auth-service per-request.
 */
@Component
@EnableScheduling
public class JwksCache {

    private static final Logger log = LoggerFactory.getLogger(JwksCache.class);

    private final WebClient webClient;
    private final String jwksUri;
    private final AtomicReference<PublicKey> cachedKey = new AtomicReference<>();

    public JwksCache(WebClient.Builder webClientBuilder,
                     @Value("${paystream.gateway.jwks-uri}") String jwksUri) {
        this.webClient = webClientBuilder.build();
        this.jwksUri   = jwksUri;
    }

    @PostConstruct
    public void init() {
        refreshKey();
    }

    @Scheduled(fixedDelay = 3_600_000) // refresh every hour
    public void refreshKey() {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> jwks = webClient.get()
                    .uri(jwksUri)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (jwks == null) {
                log.error("JWKS response was null from uri={}", jwksUri);
                return;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, String>> keys = (List<Map<String, String>>) jwks.get("keys");
            if (keys == null || keys.isEmpty()) {
                log.error("No keys in JWKS response from uri={}", jwksUri);
                return;
            }

            Map<String, String> key = keys.get(0);
            PublicKey publicKey = buildRsaPublicKey(key.get("n"), key.get("e"));
            cachedKey.set(publicKey);
            log.info("JWKS public key refreshed from uri={}", jwksUri);

        } catch (Exception e) {
            log.error("Failed to refresh JWKS key from uri={}", jwksUri, e);
        }
    }

    public PublicKey getPublicKey() {
        PublicKey key = cachedKey.get();
        if (key == null) {
            throw new IllegalStateException("JWKS public key not yet loaded");
        }
        return key;
    }

    private PublicKey buildRsaPublicKey(String n, String e) throws Exception {
        byte[] modulusBytes  = Base64.getUrlDecoder().decode(n);
        byte[] exponentBytes = Base64.getUrlDecoder().decode(e);
        BigInteger modulus  = new BigInteger(1, modulusBytes);
        BigInteger exponent = new BigInteger(1, exponentBytes);
        RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }
}
