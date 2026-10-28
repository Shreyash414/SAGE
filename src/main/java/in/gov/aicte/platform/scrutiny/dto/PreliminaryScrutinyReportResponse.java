package in.gov.aicte.platform.scrutiny.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

public record PreliminaryScrutinyReportResponse(
        Long applicationId,
        String applicationReference,
        String institutionName,
        String applicationType,
        String academicYear,
        String rulesVersion,
        int cycleNumber,
        String assessmentStatus,
        String summaryOutcome,
        Instant processedAt,
        int totalDocumentsAnalyzed,
        int totalChecks,
        int totalGroupedChecks,
        int passedChecks,
        int passedStatutoryChecks,
        int passedTechnicalChecks,
        int potentialDeficiencies,
        int groupedPotentialDeficiencies,
        int manualReviewRequired,
        int processingErrors,
        boolean canCompleteScrutiny,
        List<String> completionBlockingReasons,
        ScrutinyAssessmentResponse assessment,
        List<ScrutinyFindingResponse> findings,
        List<ScrutinyFindingResponse> allUnderlyingFindings,
        List<ScrutinyDeficiencyResponse> activeDeficiencies,
        List<RescrutinyComparisonResponse> comparisons,
        List<ScrutinyDocumentExtractionResponse> extractions
) {
    public PreliminaryScrutinyReportResponse(
            Long applicationId,
            String applicationReference,
            String institutionName,
            String applicationType,
            String academicYear,
            String rulesVersion,
            int cycleNumber,
            String assessmentStatus,
            String summaryOutcome,
            Instant processedAt,
            int totalDocumentsAnalyzed,
            int totalChecks,
            int passedChecks,
            int passedStatutoryChecks,
            int passedTechnicalChecks,
            int potentialDeficiencies,
            int manualReviewRequired,
            int processingErrors,
            boolean canCompleteScrutiny,
            List<String> completionBlockingReasons,
            ScrutinyAssessmentResponse assessment,
            List<ScrutinyFindingResponse> findings,
            List<ScrutinyDeficiencyResponse> activeDeficiencies,
            List<RescrutinyComparisonResponse> comparisons,
            List<ScrutinyDocumentExtractionResponse> extractions
    ) {
        this(
                applicationId, applicationReference, institutionName, applicationType, academicYear,
                rulesVersion, cycleNumber, assessmentStatus, summaryOutcome, processedAt,
                totalDocumentsAnalyzed, totalChecks,
                findings != null ? findings.size() : totalChecks,
                passedChecks, passedStatutoryChecks, passedTechnicalChecks,
                potentialDeficiencies,
                (int) (findings != null ? findings.stream().filter(f -> "POTENTIAL_DEFICIENCY".equalsIgnoreCase(f.outcome())).count() : potentialDeficiencies),
                manualReviewRequired, processingErrors,
                canCompleteScrutiny, completionBlockingReasons, assessment, findings,
                findings != null ? findings : Collections.emptyList(),
                activeDeficiencies, comparisons, extractions
        );
    }

    public PreliminaryScrutinyReportResponse(
            Long applicationId,
            String applicationReference,
            String institutionName,
            String applicationType,
            String academicYear,
            String rulesVersion,
            int cycleNumber,
            String assessmentStatus,
            String summaryOutcome,
            Instant processedAt,
            int totalDocumentsAnalyzed,
            int totalChecks,
            int passedChecks,
            int potentialDeficiencies,
            int manualReviewRequired,
            int processingErrors,
            boolean canCompleteScrutiny,
            List<String> completionBlockingReasons,
            ScrutinyAssessmentResponse assessment,
            List<ScrutinyFindingResponse> findings,
            List<ScrutinyDeficiencyResponse> activeDeficiencies,
            List<RescrutinyComparisonResponse> comparisons,
            List<ScrutinyDocumentExtractionResponse> extractions
    ) {
        this(
                applicationId, applicationReference, institutionName, applicationType, academicYear,
                rulesVersion, cycleNumber, assessmentStatus, summaryOutcome, processedAt,
                totalDocumentsAnalyzed, totalChecks,
                passedChecks,
                (int) (findings != null ? findings.stream().filter(f -> "PASS".equalsIgnoreCase(f.outcome()) && !"TECHNICAL_VALIDATION".equalsIgnoreCase(f.findingType()) && !"TECHNICAL_FORMAT".equalsIgnoreCase(f.findingType())).count() : 0),
                (int) (findings != null ? findings.stream().filter(f -> "PASS".equalsIgnoreCase(f.outcome()) && ("TECHNICAL_VALIDATION".equalsIgnoreCase(f.findingType()) || "TECHNICAL_FORMAT".equalsIgnoreCase(f.findingType()))).count() : 0),
                potentialDeficiencies, manualReviewRequired, processingErrors,
                canCompleteScrutiny, completionBlockingReasons, assessment, findings, activeDeficiencies, comparisons, extractions
        );
    }

    @JsonProperty("deficiencyChecks")
    public int deficiencyChecks() {
        return potentialDeficiencies;
    }

    @JsonProperty("reviewChecks")
    public int reviewChecks() {
        return manualReviewRequired;
    }
}
