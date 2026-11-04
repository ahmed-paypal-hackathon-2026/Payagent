package com.payagent.deals;

import com.payagent.common.contracts.PartyRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.UUID;

// @Entity disabled to avoid duplicate mapping with mandates.Offer
// @Table(name = "offers")
public class Offer {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "deal_id", nullable = false, columnDefinition = "UUID")
    private UUID dealId;

    @Column(name = "round_number", nullable = false)
    private Integer roundNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "proposer_role", nullable = false, length = 16)
    private PartyRole proposerRole;

    @Column(name = "generated_by_agent", nullable = false)
    private Boolean generatedByAgent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "terms_json", nullable = false, columnDefinition = "jsonb")
    private String termsJson;

    @Column(name = "rationale_summary", nullable = false, length = 500)
    private String rationaleSummary;

    @Column(name = "caller_utility_score", nullable = false, length = 32)
    private String callerUtilityScore;

    @Column(name = "mandate_compliant", nullable = false)
    private Boolean mandateCompliant;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Offer() {}

    public Offer(UUID id, UUID dealId, Integer roundNumber, PartyRole proposerRole, Boolean generatedByAgent,
                 com.payagent.common.contracts.StructuredTermsDTO terms, String rationaleSummary,
                 String callerUtilityScore, Boolean mandateCompliant) {
        this.id = id;
        this.dealId = dealId;
        this.roundNumber = roundNumber;
        this.proposerRole = proposerRole;
        this.generatedByAgent = generatedByAgent;
        try {
            this.termsJson = new ObjectMapper().writeValueAsString(terms);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize offer terms", e);
        }
        this.rationaleSummary = rationaleSummary;
        this.callerUtilityScore = callerUtilityScore;
        this.mandateCompliant = mandateCompliant;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getDealId() { return dealId; }
    public Integer getRoundNumber() { return roundNumber; }
    public PartyRole getProposerRole() { return proposerRole; }
    public Boolean getGeneratedByAgent() { return generatedByAgent; }
    public String getTermsJson() { return termsJson; }
    public String getRationaleSummary() { return rationaleSummary; }
    public String getCallerUtilityScore() { return callerUtilityScore; }
    public Boolean getMandateCompliant() { return mandateCompliant; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
