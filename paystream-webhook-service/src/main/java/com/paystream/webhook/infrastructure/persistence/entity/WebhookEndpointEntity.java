package com.paystream.webhook.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "webhook_endpoints")
public class WebhookEndpointEntity {

    @Id
    @Column(nullable = false, length = 26)
    private String id;

    @Column(name = "merchant_id", nullable = false, length = 26)
    private String merchantId;

    @Column(nullable = false, length = 2000)
    private String url;

    @Column(nullable = false, length = 255)
    private String secret;

    // Stored as PostgreSQL TEXT[] — handled via columnDefinition
    @Column(nullable = false, columnDefinition = "text[]")
    private String[] events;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WebhookEndpointEntity() {}

    public WebhookEndpointEntity(String id, String merchantId, String url,
                                  String secret, String[] events) {
        this.id         = id;
        this.merchantId = merchantId;
        this.url        = url;
        this.secret     = secret;
        this.events     = events;
        this.active     = true;
        this.createdAt  = Instant.now();
    }

    public String   getId()         { return id; }
    public String   getMerchantId() { return merchantId; }
    public String   getUrl()        { return url; }
    public String   getSecret()     { return secret; }
    public String[] getEvents()     { return events; }
    public boolean  isActive()      { return active; }

    public void deactivate() { this.active = false; }
}
