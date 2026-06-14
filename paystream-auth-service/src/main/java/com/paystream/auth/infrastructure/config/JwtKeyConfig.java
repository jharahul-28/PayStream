package com.paystream.auth.infrastructure.config;

import com.paystream.auth.security.jwt.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

import java.io.InputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Loads RSA key pair from PEM files at startup.
 * Paths are resolved via Spring's {@link ResourceLoader} — they can be classpath
 * resources (dev) or external file:// paths (production, mounted as Kubernetes secrets).
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtKeyConfig {

    private final JwtProperties  properties;
    private final ResourceLoader resourceLoader;

    public JwtKeyConfig(JwtProperties properties, ResourceLoader resourceLoader) {
        this.properties     = properties;
        this.resourceLoader = resourceLoader;
    }

    @Bean
    public PrivateKey jwtPrivateKey() throws Exception {
        byte[] keyBytes = loadPemBytes(properties.getPrivateKeyPath());
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    @Bean
    public PublicKey jwtPublicKey() throws Exception {
        byte[] keyBytes = loadPemBytes(properties.getPublicKeyPath());
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    // -------------------------------------------------------------------------

    private byte[] loadPemBytes(String resourcePath) throws Exception {
        String fullPath = resourcePath.startsWith("classpath:") || resourcePath.startsWith("file:")
                ? resourcePath
                : "classpath:" + resourcePath;

        try (InputStream is = resourceLoader.getResource(fullPath).getInputStream()) {
            String pem = new String(is.readAllBytes())
                    .replaceAll("-----[A-Z ]+-----", "")
                    .replaceAll("\\s+", "");
            return Base64.getDecoder().decode(pem);
        }
    }
}
