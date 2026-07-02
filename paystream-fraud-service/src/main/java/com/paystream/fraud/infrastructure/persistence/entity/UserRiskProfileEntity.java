package com.paystream.fraud.infrastructure.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "user_risk_profiles")
public class UserRiskProfileEntity {

    @Id
    @Column(name = "user_id", length = 26, nullable = false, updatable = false)
    private String userId;

    @Column(name = "avg_transaction_amount", nullable = false)
    private long avgTransactionAmount;

    @Column(name = "typical_hours_start", nullable = false)
    private int typicalHoursStart;

    @Column(name = "typical_hours_end", nullable = false)
    private int typicalHoursEnd;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "known_device_ids", columnDefinition = "text[]", nullable = false)
    private List<String> knownDeviceIds;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "known_ip_prefixes", columnDefinition = "text[]", nullable = false)
    private List<String> knownIpPrefixes;

    @Column(name = "chargeback_count_30d", nullable = false)
    private int chargebackCount30d;

    @Column(name = "transaction_count_30d", nullable = false)
    private int transactionCount30d;

    @Column(name = "last_updated_at", nullable = false)
    private Instant lastUpdatedAt;

    protected UserRiskProfileEntity() {}

    public UserRiskProfileEntity(String userId, long avgTransactionAmount,
                                  int typicalHoursStart, int typicalHoursEnd,
                                  List<String> knownDeviceIds, List<String> knownIpPrefixes,
                                  int chargebackCount30d, int transactionCount30d,
                                  Instant lastUpdatedAt) {
        this.userId               = userId;
        this.avgTransactionAmount = avgTransactionAmount;
        this.typicalHoursStart    = typicalHoursStart;
        this.typicalHoursEnd      = typicalHoursEnd;
        this.knownDeviceIds       = knownDeviceIds;
        this.knownIpPrefixes      = knownIpPrefixes;
        this.chargebackCount30d   = chargebackCount30d;
        this.transactionCount30d  = transactionCount30d;
        this.lastUpdatedAt        = lastUpdatedAt;
    }

    public String      getUserId()               { return userId; }
    public long        getAvgTransactionAmount()  { return avgTransactionAmount; }
    public int         getTypicalHoursStart()     { return typicalHoursStart; }
    public int         getTypicalHoursEnd()       { return typicalHoursEnd; }
    public List<String> getKnownDeviceIds()       { return knownDeviceIds; }
    public List<String> getKnownIpPrefixes()      { return knownIpPrefixes; }
    public int         getChargebackCount30d()    { return chargebackCount30d; }
    public int         getTransactionCount30d()   { return transactionCount30d; }
    public Instant     getLastUpdatedAt()         { return lastUpdatedAt; }
}
