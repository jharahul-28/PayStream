# ADR-004: AI Never Influences Fraud Decisions

**Status:** Accepted
**Date:** 2024-01-15

---

## Context

PayStream uses Spring AI (OpenAI GPT) for fraud analysis. AI can provide rich narrative explanations
and pattern-flagging, but AI outputs are probabilistic, non-deterministic, and subject to hallucination.

In financial systems, the decision to ALLOW or BLOCK a payment is an irreversible financial action.
Delegating this decision to a probabilistic model creates regulatory risk, auditability problems,
and unpredictable customer impact.

We considered three models:
1. **AI owns the decision** — too risky; unexplainable, non-deterministic
2. **AI as one signal among several** — hard to audit; AI can shift outcomes unpredictably
3. **Deterministic rules own the decision; AI provides context only** — chosen

---

## Decision

**Stage 1 (synchronous, < 5ms)**: Deterministic rule engine evaluates weighted rules.
The output is ALLOW / BLOCK / REVIEW. This decision is **FINAL**.

**Stage 2 (asynchronous, Kafka)**: AI enrichment updates `ai_narrative`, `ai_risk_score`, and `ai_confidence`
in `fraud_checks` for dashboard/analyst use.

**AI NEVER**:
- Changes a payment's status
- Overrides a Stage 1 BLOCK to ALLOW
- Overrides a Stage 1 ALLOW to BLOCK
- Causes any irreversible financial action

This boundary is enforced architecturally: Stage 2 runs after the payment has already been
COMPLETED or FAILED. There is no code path where AI output reaches `PaymentApplicationService`.

---

## Consequences

**Positive:**
- Explainable decisions: every BLOCK can be traced to specific rules and weights
- Regulatory compliance: deterministic audit trail
- AI failure (circuit open) = graceful degradation (narrative not populated, decision unchanged)
- Rules can be updated in DB without code deploy (rule weight/enabled flag)

**Negative:**
- Rules require manual tuning; AI score disagreement logged but not acted upon
- AI value is limited to analyst dashboards, not real-time protection improvement

**Monitoring:**
- Log WARN when `|aiScore - ruleScore| > 30` — signals rule-AI disagreement for analyst review
- Metric `paystream.fraud.ai.duration.ms` for AI response latency
- Circuit breaker on AI client with automatic retry on next poll cycle
