package in.gov.aicte.platform.application.catalogue;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ApplicationTypeCatalogue {

    private final Map<String, ApplicationTypeDefinition> catalogueMap = new LinkedHashMap<>();

    public ApplicationTypeCatalogue() {
        initializeCatalogue();
    }

    public List<ApplicationTypeDefinition> getAllTypes() {
        return List.copyOf(catalogueMap.values());
    }

    public Optional<ApplicationTypeDefinition> findByCode(String code) {
        if (code == null) return Optional.empty();
        return Optional.ofNullable(catalogueMap.get(code.toUpperCase()));
    }

    private void initializeCatalogue() {
        // Standard shared document requirements
        DocumentRequirementDefinition docLand = new DocumentRequirementDefinition(
                "DOC_LAND_DOCUMENTS",
                "Land Ownership / Lease Deed & Land Use Certificate",
                "Registered Sale Deed/Gift Deed/Lease Deed and Land Use Certificate issued by the Competent Authority as per AICTE norms.",
                "APH 2024-27 Clause 1.4",
                true,
                List.of("application/pdf"),
                15 * 1024 * 1024L
        );

        DocumentRequirementDefinition docBuildingPlan = new DocumentRequirementDefinition(
                "DOC_BUILDING_PLAN",
                "Approved Master Building Plan",
                "Master building plan approved by the Competent Authority of the concerned Town Planning / Municipal Department.",
                "APH 2024-27 Clause 1.5.1",
                true,
                List.of("application/pdf"),
                15 * 1024 * 1024L
        );

        DocumentRequirementDefinition docOccupancy = new DocumentRequirementDefinition(
                "DOC_OCCUPANCY_CERT",
                "Building Completion / Occupancy Certificate",
                "Occupancy certificate / Building completion certificate issued by the Competent Local Authority.",
                "APH 2024-27 Clause 1.5.3",
                true,
                List.of("application/pdf"),
                10 * 1024 * 1024L
        );

        DocumentRequirementDefinition docFireSafety = new DocumentRequirementDefinition(
                "DOC_FIRE_SAFETY",
                "Fire & Life Safety Certificate",
                "Valid Fire Safety Certificate issued by the Competent Fire Service Authority of the State/UT.",
                "APH 2024-27 Clause 1.6",
                true,
                List.of("application/pdf"),
                10 * 1024 * 1024L
        );

        DocumentRequirementDefinition docStructural = new DocumentRequirementDefinition(
                "DOC_STRUCTURAL_STABILITY",
                "Structural Stability Certificate",
                "Structural Stability Certificate from a Registered Structural Engineer with valid registration credentials.",
                "APH 2024-27 Clause 1.7",
                true,
                List.of("application/pdf"),
                10 * 1024 * 1024L
        );

        DocumentRequirementDefinition docTrustDeed = new DocumentRequirementDefinition(
                "DOC_TRUST_DEED",
                "Trust / Society / Company Registration Deed",
                "Registered Trust Deed / Memorandum of Association and Registration Certificate under Societies Act or Section 8 Company deed.",
                "APH 2024-27 Clause 1.3",
                true,
                List.of("application/pdf"),
                10 * 1024 * 1024L
        );

        DocumentRequirementDefinition docAffiliationNoc = new DocumentRequirementDefinition(
                "DOC_AFFILIATING_NOC",
                "Affiliating University / State Board NOC / Consent",
                "Consent of Affiliation / NOC issued by the concerned Affiliating University or State Board of Technical Education.",
                "APH 2024-27 Clause 1.8",
                true,
                List.of("application/pdf"),
                10 * 1024 * 1024L
        );

        DocumentRequirementDefinition docCorpus = new DocumentRequirementDefinition(
                "DOC_CORPUS_FUND",
                "Corpus Fund / Joint FDR Proof",
                "Fixed Deposit Receipt (FDR) / Bank Guarantee created in the joint name of the Trust and Regional Officer, AICTE.",
                "APH 2024-27 Clause 1.9",
                true,
                List.of("application/pdf"),
                10 * 1024 * 1024L
        );

        // Core shared sections
        List<FormSectionDefinition> baseSections = List.of(
                new FormSectionDefinition(
                        "institution_info",
                        "Institution & Legal Entity Profile",
                        "Basic institutional identification and registered Trust/Society/Company details.",
                        List.of(
                                new FormFieldDefinition("institutionName", "Institution Name", "text", true, "e.g. National Institute of Technology", null, "Full registered legal name of the institution"),
                                new FormFieldDefinition("trustName", "Trust / Society / Company Name", "text", true, "e.g. Apex Education Trust", null, "Sponsoring entity name"),
                                new FormFieldDefinition("trustRegistrationNumber", "Trust / Society Registration Number", "text", true, "e.g. REG/2012/1048", null, "Official registration reference"),
                                new FormFieldDefinition("panNumber", "Trust / Institution PAN", "text", true, "e.g. AAATA1234F", null, "10-digit Permanent Account Number"),
                                new FormFieldDefinition("headOfInstitutionName", "Head of Institution / Director Name", "text", true, "e.g. Dr. Rajesh Sharma", null, "Designated Principal or Director"),
                                new FormFieldDefinition("contactEmail", "Primary Official Email", "text", true, "e.g. director@institute.edu.in", null, "All statutory communication will be sent here"),
                                new FormFieldDefinition("contactPhone", "Official Mobile / Telephone", "text", true, "e.g. 9876543210", null, "10-digit mobile number")
                        )
                ),
                new FormSectionDefinition(
                        "location_infrastructure",
                        "Campus Location & Land Details",
                        "Location classification, land parcel size and built-up area conforming to AICTE norms.",
                        List.of(
                                new FormFieldDefinition("campusCategory", "Location Category", "select", true, null, List.of("Urban", "Rural", "Metro / Mega City", "Hilly Area"), "Location categorization as per APH norms"),
                                new FormFieldDefinition("landAreaAcres", "Total Land Area (in Acres)", "number", true, "e.g. 5.5", null, "Minimum land requirement must be satisfied"),
                                new FormFieldDefinition("totalBuiltUpAreaSqM", "Total Built-up Area (in Sq. Meters)", "number", true, "e.g. 12500", null, "Total instructional, administrative, and amenities area"),
                                new FormFieldDefinition("instructionalAreaSqM", "Instructional Area (in Sq. Meters)", "number", true, "e.g. 7500", null, "Classrooms, tutorial rooms, laboratories, workshops"),
                                new FormFieldDefinition("administrativeAreaSqM", "Administrative Area (in Sq. Meters)", "number", true, "e.g. 2000", null, "Offices, principal room, faculty rooms, meeting rooms")
                        )
                ),
                new FormSectionDefinition(
                        "statutory_declarations",
                        "Statutory Compliance Declarations",
                        "Mandatory compliance certifications required under AICTE regulations.",
                        List.of(
                                new FormFieldDefinition("antiRaggingDeclared", "Anti-Ragging Committee & Squad Established", "checkbox", true, null, null, "Certified in compliance with AICTE Anti-Ragging Regulations"),
                                new FormFieldDefinition("grievanceRedressalDeclared", "Student Grievance Redressal Committee (SGRC) Constituted", "checkbox", true, null, null, "Certified with OMBUDSPERSON appointment"),
                                new FormFieldDefinition("internalComplaintsDeclared", "Internal Complaint Committee (ICC) Constituted", "checkbox", true, null, null, "Under Section 4 of Sexual Harassment of Women at Workplace Act, 2013"),
                                new FormFieldDefinition("barrierFreeDeclared", "Barrier-Free Built Environment for Divyangjan", "checkbox", true, null, null, "Ramps, lifts, and accessible facilities provisioned"),
                                new FormFieldDefinition("fireSafetyDeclared", "Fire Fighting & Safety Installations Functional", "checkbox", true, null, null, "Certified that all emergency equipment is operational")
                        )
                )
        );

        // 1. Setting up New Technical Institution
        registerType(new ApplicationTypeDefinition(
                "NEW_INSTITUTION",
                "Setting up New Technical Institution",
                "Chapter I — New Institutions",
                "APH 2024-27 Chapter I",
                "Application for establishing a new Technical Institution offering Diploma, UG Degree, PG Degree, or PG Diploma programmes.",
                List.of("Diploma", "Undergraduate Degree", "Postgraduate Degree", "Postgraduate Diploma"),
                baseSections,
                List.of(docTrustDeed, docLand, docBuildingPlan, docOccupancy, docFireSafety, docStructural, docAffiliationNoc, docCorpus)
        ));

        // 2. Extension of Approval (EoA)
        DocumentRequirementDefinition docFacultyRoll = new DocumentRequirementDefinition(
                "DOC_FACULTY_ROLL",
                "Faculty Cadre Ratio & Pay Scale Compliance",
                "Faculty roster certified by the Affiliating University along with 7th CPC / State pay scale compliance affidavit.",
                "APH 2024-27 Clause 2.1",
                true,
                List.of("application/pdf"),
                10 * 1024 * 1024L
        );
        DocumentRequirementDefinition docPreviousEoa = new DocumentRequirementDefinition(
                "DOC_PREVIOUS_EOA",
                "Previous AICTE Approval / EoA Letter",
                "Copy of the previous academic year's AICTE Extension of Approval (EoA) letter.",
                "APH 2024-27 Clause 2.1.2",
                true,
                List.of("application/pdf"),
                5 * 1024 * 1024L
        );
        DocumentRequirementDefinition docAuditedAccounts = new DocumentRequirementDefinition(
                "DOC_AUDITED_ACCOUNTS",
                "Audited Financial Statements",
                "Audited Balance Sheet, Income & Expenditure account, and audit report of the previous fiscal year signed by a Chartered Accountant.",
                "APH 2024-27 Clause 2.2",
                true,
                List.of("application/pdf"),
                15 * 1024 * 1024L
        );

        registerType(new ApplicationTypeDefinition(
                "EXTENSION_OF_APPROVAL",
                "Extension of Approval (EoA)",
                "Chapter II — Existing Institutions",
                "APH 2024-27 Chapter II Clause 2.1",
                "Annual continuation of approval for existing AICTE-approved technical institutions.",
                List.of("Diploma", "Undergraduate Degree", "Postgraduate Degree", "Postgraduate Diploma"),
                baseSections,
                List.of(docPreviousEoa, docFacultyRoll, docFireSafety, docOccupancy, docAuditedAccounts)
        ));

        // 3. Introduction of New Programme(s)
        DocumentRequirementDefinition docUnivNoc = new DocumentRequirementDefinition(
                "DOC_UNIVERSITY_NOC",
                "Affiliating University / State Board NOC for New Programme",
                "Formal NOC issued by the Affiliating University / State Board approving the introduction of the new programme.",
                "APH 2024-27 Clause 2.4",
                true,
                List.of("application/pdf"),
                10 * 1024 * 1024L
        );
        DocumentRequirementDefinition docInfraReadiness = new DocumentRequirementDefinition(
                "DOC_INFRASTRUCTURE_READINESS",
                "Infrastructure & Laboratory Readiness Report",
                "Certified detailed project report demonstrating laboratory equipment, software, and instructional spaces for the new programme.",
                "APH 2024-27 Clause 2.4.3",
                true,
                List.of("application/pdf"),
                15 * 1024 * 1024L
        );

        registerType(new ApplicationTypeDefinition(
                "NEW_PROGRAMME",
                "Introduction of New Programme(s)",
                "Chapter II — Existing Institutions",
                "APH 2024-27 Chapter II Clause 2.4",
                "Introduction of new technical programmes (e.g. Engineering, Management, Architecture, Pharmacy, MCA) in existing institutions.",
                List.of("Diploma", "Undergraduate Degree", "Postgraduate Degree"),
                baseSections,
                List.of(docPreviousEoa, docUnivNoc, docInfraReadiness, docFacultyRoll, docBuildingPlan)
        ));

        // 4. Introduction of New Course(s) / Division(s)
        registerType(new ApplicationTypeDefinition(
                "ADD_COURSE",
                "Introduction of New Course(s) / Division(s)",
                "Chapter II — Existing Institutions",
                "APH 2024-27 Chapter II Clause 2.5",
                "Introduction of new emerging technical courses or additional divisions in existing approved programmes.",
                List.of("Diploma", "Undergraduate Degree", "Postgraduate Degree"),
                baseSections,
                List.of(docPreviousEoa, docUnivNoc, docInfraReadiness, docFacultyRoll)
        ));

        // 5. Change in Intake / Closure of Course
        DocumentRequirementDefinition docIntakeResolution = new DocumentRequirementDefinition(
                "DOC_GOVERNING_BODY_INTAKE_RES",
                "Governing Body Resolution for Intake Change / Closure",
                "Resolution passed by the Governing Body / Trust for increase/decrease in intake or closure of specific courses.",
                "APH 2024-27 Clause 2.6",
                true,
                List.of("application/pdf"),
                5 * 1024 * 1024L
        );

        registerType(new ApplicationTypeDefinition(
                "CHANGE_INTAKE",
                "Increase / Reduction in Intake or Course Closure",
                "Chapter II — Existing Institutions",
                "APH 2024-27 Chapter II Clause 2.6",
                "Increase in sanctioned intake, reduction in intake, or phased closure of specific courses in existing institutions.",
                List.of("Diploma", "Undergraduate Degree", "Postgraduate Degree"),
                baseSections,
                List.of(docPreviousEoa, docIntakeResolution, docUnivNoc, docFacultyRoll)
        ));

        // 6. Closure of the Institution
        DocumentRequirementDefinition docGovNocClosure = new DocumentRequirementDefinition(
                "DOC_GOV_NOC_CLOSURE",
                "State Government NOC for Institution Closure",
                "NOC issued by the State Government / UT Administration approving the progressive or complete closure of the institution.",
                "APH 2024-27 Clause 2.12.1",
                true,
                List.of("application/pdf"),
                10 * 1024 * 1024L
        );
        DocumentRequirementDefinition docStudentTransferPlan = new DocumentRequirementDefinition(
                "DOC_STUDENT_TRANSFER_PLAN",
                "Student Migration & Academic Completion Plan",
                "Detailed undertaking and plan approved by the Affiliating University for transfer or completion of all enrolled students.",
                "APH 2024-27 Clause 2.12.3",
                true,
                List.of("application/pdf"),
                10 * 1024 * 1024L
        );

        registerType(new ApplicationTypeDefinition(
                "CLOSURE_INSTITUTION",
                "Closure of Institution (Progressive / Complete)",
                "Chapter II — Existing Institutions",
                "APH 2024-27 Chapter II Clause 2.12",
                "Progressive closure or complete closure of an approved technical institution.",
                List.of("Institution Level"),
                baseSections,
                List.of(docPreviousEoa, docGovNocClosure, docUnivNoc, docStudentTransferPlan)
        ));

        // 7. Institution Change (Site / Name)
        DocumentRequirementDefinition docSiteResolution = new DocumentRequirementDefinition(
                "DOC_SITE_CHANGE_RESOLUTION",
                "Governing Body Resolution for Change of Site / Name",
                "Resolution passed by the sponsoring Trust/Society approving change of site or change of institutional name.",
                "APH 2024-27 Clause 2.14",
                true,
                List.of("application/pdf"),
                5 * 1024 * 1024L
        );

        registerType(new ApplicationTypeDefinition(
                "INSTITUTION_CHANGE",
                "Change of Site / Location or Change of Name",
                "Chapter II — Existing Institutions",
                "APH 2024-27 Chapter II Clause 2.14",
                "Change of campus site/location or change of name of the institution, affiliating university, or trust/society.",
                List.of("Institution Level"),
                baseSections,
                List.of(docPreviousEoa, docSiteResolution, docLand, docBuildingPlan, docOccupancy, docFireSafety, docAffiliationNoc)
        ));

        // 8. Merger of Institutions
        DocumentRequirementDefinition docMergerResolution = new DocumentRequirementDefinition(
                "DOC_MERGER_RESOLUTION",
                "Joint Governing Body Resolution for Merger",
                "Joint resolution of the Governing Bodies of both institutions approving the merger into a single entity.",
                "APH 2024-27 Clause 2.16",
                true,
                List.of("application/pdf"),
                10 * 1024 * 1024L
        );

        registerType(new ApplicationTypeDefinition(
                "MERGER_INSTITUTIONS",
                "Merger of Technical Institutions",
                "Chapter II — Existing Institutions",
                "APH 2024-27 Chapter II Clause 2.16",
                "Merger of institutions under the same Trust/Society operating in the same campus or contiguous land parcel.",
                List.of("Institution Level"),
                baseSections,
                List.of(docPreviousEoa, docMergerResolution, docLand, docBuildingPlan, docFireSafety, docUnivNoc)
        ));

        // 9. Restoration of Intake / Revocation of Penalty
        DocumentRequirementDefinition docPenaltyCompliance = new DocumentRequirementDefinition(
                "DOC_PENALTY_COMPLIANCE",
                "Deficiency Rectification & Compliance Report",
                "Certified compliance report detailing removal of all deficiencies cited in the punitive order or penalty letter.",
                "APH 2024-27 Clause 2.17",
                true,
                List.of("application/pdf"),
                15 * 1024 * 1024L
        );

        registerType(new ApplicationTypeDefinition(
                "RESTORATION_INTAKE",
                "Restoration of Intake / Revocation of Penalty",
                "Chapter II — Existing Institutions",
                "APH 2024-27 Chapter II Clause 2.17",
                "Restoration of reduced intake or revocation of 'No Admission' / punitive status following verified rectification.",
                List.of("Diploma", "Undergraduate Degree", "Postgraduate Degree"),
                baseSections,
                List.of(docPreviousEoa, docPenaltyCompliance, docFacultyRoll, docFireSafety, docOccupancy)
        ));

        // 10. Supernumerary Seats (FN / OCI / PIO / Gulf / TFW)
        DocumentRequirementDefinition docHostelInfra = new DocumentRequirementDefinition(
                "DOC_HOSTEL_INFRASTRUCTURE",
                "Hostel & Dedicated International Amenities Verification",
                "Architectural report and photographs verifying dedicated hostel facilities and dining arrangements for international/supernumerary students.",
                "APH 2024-27 Clause 2.18",
                true,
                List.of("application/pdf"),
                10 * 1024 * 1024L
        );
        DocumentRequirementDefinition docNbaAccreditation = new DocumentRequirementDefinition(
                "DOC_NBA_ACCREDITATION",
                "NBA Accreditation Certificate (Valid)",
                "Valid NBA accreditation certificate for courses eligible for FN/OCI/PIO supernumerary quota.",
                "APH 2024-27 Clause 2.18.3",
                true,
                List.of("application/pdf"),
                10 * 1024 * 1024L
        );

        registerType(new ApplicationTypeDefinition(
                "SUPERNUMERARY_SEATS",
                "FN / OCI / PIO / Children of Gulf Workers / TFW Seats",
                "Chapter II — Existing Institutions",
                "APH 2024-27 Chapter II Clause 2.18",
                "Approval for supernumerary seats for Foreign Nationals, OCI, PIO, Children of Indian Workers in Gulf Countries, and Tuition Fee Waiver scheme.",
                List.of("Undergraduate Degree", "Postgraduate Degree"),
                baseSections,
                List.of(docPreviousEoa, docNbaAccreditation, docHostelInfra, docFacultyRoll)
        ));

        // 11. Collaborative / ODL / Online Programmes
        DocumentRequirementDefinition docMouCollab = new DocumentRequirementDefinition(
                "DOC_MOU_COLLABORATION",
                "Bilateral MoU with Partner University / UGC-DEB Approval",
                "Executed Memorandum of Understanding with Foreign / Partner Institute, or UGC-DEB approval letter for ODL / Online programmes.",
                "APH 2024-27 Chapter III Clause 3.1",
                true,
                List.of("application/pdf"),
                15 * 1024 * 1024L
        );
        DocumentRequirementDefinition docNaacCert = new DocumentRequirementDefinition(
                "DOC_NAAC_CERTIFICATE",
                "Valid NAAC / NBA Accreditation Certificate",
                "NAAC score certificate with minimum 3.26 CGPA or qualifying NBA accreditation as stipulated for ODL/Online offerings.",
                "APH 2024-27 Chapter III Clause 3.2",
                true,
                List.of("application/pdf"),
                10 * 1024 * 1024L
        );

        registerType(new ApplicationTypeDefinition(
                "COLLABORATIVE_ODL_ONLINE",
                "Collaborative / Twinning / ODL / Online Programmes",
                "Chapter III — Collaboration & Open Distance Learning",
                "APH 2024-27 Chapter III",
                "Twinning programmes, dual degree, joint degree collaborations, and Open & Distance Learning (ODL) / Online technical courses.",
                List.of("Undergraduate Degree", "Postgraduate Degree"),
                baseSections,
                List.of(docPreviousEoa, docMouCollab, docNaacCert, docUnivNoc)
        ));

        // 12. Regulatory & Status Change
        DocumentRequirementDefinition docMinorityCert = new DocumentRequirementDefinition(
                "DOC_MINORITY_STATUS_CERT",
                "Minority Status Certificate / Statutory Conversion Proof",
                "Certificate issued by National Commission for Minority Educational Institutions (NCMEI) or competent State Authority, or resolution for co-ed conversion.",
                "APH 2024-27 Clause 2.20",
                true,
                List.of("application/pdf"),
                10 * 1024 * 1024L
        );

        registerType(new ApplicationTypeDefinition(
                "REGULATORY_CHANGE",
                "Minority Status / Women to Co-ed Conversion / Type Change",
                "Chapter II — Existing Institutions",
                "APH 2024-27 Chapter II Clause 2.20",
                "Change in minority status, conversion of Women's Institution into Co-ed institution and vice-versa, or change in institution classification.",
                List.of("Institution Level"),
                baseSections,
                List.of(docPreviousEoa, docMinorityCert, docAffiliationNoc, docLand)
        ));
    }

    private void registerType(ApplicationTypeDefinition def) {
        catalogueMap.put(def.code().toUpperCase(), def);
    }
}
