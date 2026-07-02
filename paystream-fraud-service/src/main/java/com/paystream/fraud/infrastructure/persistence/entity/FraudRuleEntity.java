package com.paystream.fraud.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "fraud_rules")
public class FraudRuleEntity {

    @Id
    @Column(length = 26, nullable = false, updatable = false)
    private String id;

    @Column(name = "rule_code", length = 50, nullable = false, unique = true)
    private String ruleCode;

    @Column(name = "rule_name", length = 255, nullable = false)
    private String ruleName;

    @Column(name = "flag_name", length = 50, nullable = false)
    private String flagName;

    @Column(name = "weight", nullable = false)
    private int weight;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "version", length = 20, nullable = false)
    private String version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FraudRuleEntity() {}

    public String  getId()        { return id; }
    public String  getRuleCode()  { return ruleCode; }
    public String  getRuleName()  { return ruleName; }
    public String  getFlagName()  { return flagName; }
    public int     getWeight()    { return weight; }
    public boolean isEnabled()    { return enabled; }
    public String  getVersion()   { return version; }
    public Instant getUpdatedAt() { return updatedAt; }
}
