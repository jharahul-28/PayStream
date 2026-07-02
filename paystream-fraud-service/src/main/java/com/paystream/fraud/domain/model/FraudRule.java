package com.paystream.fraud.domain.model;

/** Pure domain value — represents a configurable fraud detection rule. */
public record FraudRule(
        String  id,
        String  ruleCode,
        String  ruleName,
        String  flagName,
        int     weight,
        boolean enabled,
        String  version
) {}
