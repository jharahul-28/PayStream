package com.paystream.auth.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalised JWT configuration bound from {@code application.yml}.
 * Private key path and public key path must point to PEM files on the classpath or filesystem.
 * TTL values have safe defaults but should always be overridden via environment variables.
 */
@ConfigurationProperties(prefix = "paystream.security.jwt")
public class JwtProperties {

    /** Classpath or file-system path to the RSA private key (PEM, PKCS#8). */
    private String privateKeyPath = "keys/private.pem";

    /** Classpath or file-system path to the RSA public key (PEM). */
    private String publicKeyPath = "keys/public.pem";

    /** Access token TTL in seconds (default 15 minutes). */
    private long accessTokenTtlSeconds = 900L;

    /** Refresh token TTL in seconds (default 7 days). */
    private long refreshTokenTtlSeconds = 604_800L;

    public String getPrivateKeyPath()              { return privateKeyPath; }
    public void setPrivateKeyPath(String v)        { this.privateKeyPath = v; }

    public String getPublicKeyPath()               { return publicKeyPath; }
    public void setPublicKeyPath(String v)         { this.publicKeyPath = v; }

    public long getAccessTokenTtlSeconds()         { return accessTokenTtlSeconds; }
    public void setAccessTokenTtlSeconds(long v)   { this.accessTokenTtlSeconds = v; }

    public long getRefreshTokenTtlSeconds()        { return refreshTokenTtlSeconds; }
    public void setRefreshTokenTtlSeconds(long v)  { this.refreshTokenTtlSeconds = v; }
}
