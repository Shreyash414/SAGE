package in.gov.aicte.platform.scrutiny.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "scrutiny_findings")
public class ScrutinyFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id", nullable = false)
    private ScrutinyAssessment assessment;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "requirement_code", length = 64)
    private String requirementCode;

    @Column(name = "rule_code", nullable = false, length = 64)
    private String ruleCode;

    @Column(name = "rule_name", nullable = false)
    private String ruleName;

    @Column(name = "rule_reference", nullable = false, length = 128)
    private String ruleReference;

    @Column(name = "finding_type", nullable = false, length = 64)
    private String findingType;

    @Column(name = "outcome", nullable = false, length = 32)
    private String outcome = "NOT_EVALUATED";

    @Column(name = "severity", nullable = false, length = 32)
    private String severity = "BLOCKING";

    @Column(name = "expected_condition", nullable = false, columnDefinition = "TEXT")
    private String expectedCondition;

    @Column(name = "actual_value", columnDefinition = "TEXT")
    private String actualValue;

    @Column(name = "explanation", nullable = false, columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "recommended_action", columnDefinition = "TEXT")
    private String recommendedAction;

    @Column(name = "officer_decision", nullable = false, length = 32)
    private String officerDecision = "PENDING";

    @Column(name = "officer_remarks", columnDefinition = "TEXT")
    private String officerRemarks;

    @Column(name = "reviewed_by", length = 128)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @jakarta.persistence.Transient
    private String documentName;

    public ScrutinyFinding() {
    }

    public ScrutinyFinding(ScrutinyAssessment assessment, Long applicationId, Long documentId,
                           String requirementCode, String ruleCode, String ruleName,
                           String ruleReference, String findingType, String outcome,
                           String severity, String expectedCondition, String actualValue,
                           String explanation, String recommendedAction) {
        this(assessment, applicationId, documentId, requirementCode, ruleCode, ruleName,
                ruleReference, findingType, outcome, severity, expectedCondition, actualValue,
                explanation, recommendedAction, null);
    }

    public ScrutinyFinding(ScrutinyAssessment assessment, Long applicationId, Long documentId,
                           String requirementCode, String ruleCode, String ruleName,
                           String ruleReference, String findingType, String outcome,
                           String severity, String expectedCondition, String actualValue,
                           String explanation, String recommendedAction, String documentName) {
        this.assessment = assessment;
        this.applicationId = applicationId;
        this.documentId = documentId;
        this.requirementCode = requirementCode;
        this.ruleCode = ruleCode;
        this.ruleName = ruleName;
        this.ruleReference = ruleReference;
        this.findingType = findingType;
        this.outcome = outcome;
        this.severity = severity;
        this.expectedCondition = expectedCondition;
        this.actualValue = actualValue;
        this.explanation = explanation;
        this.recommendedAction = recommendedAction;
        this.documentName = documentName;
    }

    public String getDocumentName() {
        return documentName;
    }

    public void setDocumentName(String documentName) {
        this.documentName = documentName;
    }

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ScrutinyAssessment getAssessment() {
        return assessment;
    }

    public void setAssessment(ScrutinyAssessment assessment) {
        this.assessment = assessment;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public String getRequirementCode() {
        return requirementCode;
    }

    public void setRequirementCode(String requirementCode) {
        this.requirementCode = requirementCode;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getRuleReference() {
        return ruleReference;
    }

    public void setRuleReference(String ruleReference) {
        this.ruleReference = ruleReference;
    }

    public String getFindingType() {
        return findingType;
    }

    public void setFindingType(String findingType) {
        this.findingType = findingType;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getExpectedCondition() {
        return expectedCondition;
    }

    public void setExpectedCondition(String expectedCondition) {
        this.expectedCondition = expectedCondition;
    }

    public String getActualValue() {
        return actualValue;
    }

    public void setActualValue(String actualValue) {
        this.actualValue = actualValue;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public String getRecommendedAction() {
        return recommendedAction;
    }

    public void setRecommendedAction(String recommendedAction) {
        this.recommendedAction = recommendedAction;
    }

    public String getOfficerDecision() {
        return officerDecision;
    }

    public void setOfficerDecision(String officerDecision) {
        this.officerDecision = officerDecision;
    }

    public String getOfficerRemarks() {
        return officerRemarks;
    }

    public void setOfficerRemarks(String officerRemarks) {
        this.officerRemarks = officerRemarks;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(String reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
