package com.paystream.fraud.domain.model;

import java.time.Instant;
import java.util.List;

/** Per-user behavioral baseline used by the rules engine. */
public class UserRiskProfile {

    private final String      userId;
    private long              avgTransactionAmount;
    private int               typicalHoursStart;
    private int               typicalHoursEnd;
    private List<String>      knownDeviceIds;
    private List<String>      knownIpPrefixes;
    private int               chargebackCount30d;
    private int               transactionCount30d;
    private Instant           lastUpdatedAt;

    public UserRiskProfile(String userId, long avgTransactionAmount,
                           int typicalHoursStart, int typicalHoursEnd,
                           List<String> knownDeviceIds, List<String> knownIpPrefixes,
                           int chargebackCount30d, int transactionCount30d,
                           Instant lastUpdatedAt) {
        this.userId               = userId;
        this.avgTransactionAmount = avgTransactionAmount;
        this.typicalHoursStart    = typicalHoursStart;
        this.typicalHoursEnd      = typicalHoursEnd;
        this.knownDeviceIds       = knownDeviceIds == null ? List.of() : List.copyOf(knownDeviceIds);
        this.knownIpPrefixes      = knownIpPrefixes == null ? List.of() : List.copyOf(knownIpPrefixes);
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
