package in.gov.aicte.platform.scrutiny.ocr;

import in.gov.aicte.platform.application.document.ApplicationDocument;
import in.gov.aicte.platform.scrutiny.dto.FieldSourceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DocumentFieldExtractionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentFieldExtractionService.class);

    // Multi-format date patterns (Indian standard DD/MM/YYYY, ISO YYYY-MM-DD, DD Month YYYY)
    private static final Pattern DATE_PATTERN_1 = Pattern.compile("\\b(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})\\b");
    private static final Pattern DATE_PATTERN_2 = Pattern.compile("\\b(\\d{4})[/-](\\d{1,2})[/-](\\d{1,2})\\b");
    private static final Pattern DATE_PATTERN_TEXT = Pattern.compile(
            "\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)[,\\s]+(\\d{4})\\b",
            Pattern.CASE_INSENSITIVE
    );

    // Expiry contextual regex
    private static final Pattern EXPIRY_CONTEXT_PATTERN = Pattern.compile(
            "(?:valid\\s*(?:up\\s*to|upto|till|until|through)|expires?\\s*(?:on)?|expiry\\s*date|validity\\s*(?:period|date|till)?)[^\\d\\r\\n]{0,25}(\\d{1,2}[/-]\\d{1,2}[/-]\\d{4}|\\d{4}[/-]\\d{1,2}[/-]\\d{1,2}|\\d{1,2}(?:st|nd|rd|th)?\\s+[A-Za-z]+[,\\s]+\\d{4})",
            Pattern.CASE_INSENSITIVE
    );

    // Issue date contextual regex
    private static final Pattern ISSUE_CONTEXT_PATTERN = Pattern.compile(
            "(?:dated?|date\\s*of\\s*issue|issued\\s*on|order\\s*date)[^\\d\\r\\n]{0,25}(\\d{1,2}[/-]\\d{1,2}[/-]\\d{4}|\\d{4}[/-]\\d{1,2}[/-]\\d{1,2}|\\d{1,2}(?:st|nd|rd|th)?\\s+[A-Za-z]+[,\\s]+\\d{4})",
            Pattern.CASE_INSENSITIVE
    );

    // Certificate / NOC number patterns
    private static final Pattern CERT_NUMBER_PATTERN = Pattern.compile(
            "(?:(?:mock\\s*)?certificate(?:\\s*no\\.?)?|noc\\s*(?:no\\.?)?|ref\\.?\\s*no\\.?|letter\\s*no\\.?|dispatch\\s*no\\.?|f\\.?\\s*no\\.?|registration\\s*no\\.?|sanction\\s*no\\.?|order\\s*no\\.?|deed\\s*no\\.?|fdr\\s*no\\.?|survey\\s*no\\.?|udin)[^\\w\\r\\n]{0,5}([A-Z0-9/\\-_]{4,32})",
            Pattern.CASE_INSENSITIVE
    );

    // PAN Number
    private static final Pattern PAN_PATTERN = Pattern.compile("\\b([A-Z]{5}[0-9]{4}[A-Z])\\b");

    // PIN Code
    private static final Pattern PIN_CODE_PATTERN = Pattern.compile("\\b([1-9][0-9]{5})\\b");

    // Land Area pattern
    private static final Pattern LAND_AREA_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:acres?|hectares?|sq\\.?\\s*(?:meters|metres|m))", Pattern.CASE_INSENSITIVE);

    // Unrelated academic lab/coursework keywords
    private static final Pattern UNRELATED_ACADEMIC_PATTERN = Pattern.compile(
            "\\b(design\\s*and\\s*analysis\\s*of\\s*algorithms|algorithm(?:s)?\\s*lab|lab\\s*manual|practical\\s*file|index\\s*of\\s*experiments|time\\s*complexity|space\\s*complexity|quick\\s*sort|merge\\s*sort|binary\\s*search|dijkstra|knapsack|bubble\\s*sort|dynamic\\s*programming|greedy\\s*method|graph\\s*traversal|breadth\\s*first|depth\\s*first|data\\s*structures\\s*lab|operating\\s*systems\\s*lab|computer\\s*networks\\s*lab|submitted\\s*by\\s*:\\s*[A-Za-z]+|roll\\s*no\\s*:|enrollment\\s*no\\s*:)\\b",
            Pattern.CASE_INSENSITIVE
    );

    public ExtractedDocumentData extractStructuredFields(ExtractedDocumentData extractedData, String requirementCode) {
        return extractStructuredFields(extractedData, requirementCode, null);
    }

    public ExtractedDocumentData extractStructuredFields(
            ExtractedDocumentData extractedData,
            String requirementCode,
            ApplicationDocument doc
    ) {
        if (extractedData == null) {
            return new ExtractedDocumentData();
        }

        Map<String, Object> fields = new LinkedHashMap<>();
        Map<String, FieldSourceInfo> fieldSources = new LinkedHashMap<>();

        // 1. Ingest File Metadata if ApplicationDocument is present
        if (doc != null) {
            if (doc.getOriginalFileName() != null) {
                fields.put("originalFileName", doc.getOriginalFileName());
                fieldSources.put("originalFileName", FieldSourceInfo.fileMeta(
                        "originalFileName", "Original File Name", doc.getOriginalFileName(),
                        "Multipart upload header filename", doc.getOriginalFileName()
                ));
            }
            if (doc.getFileSize() != null) {
                fields.put("fileSize", doc.getFileSize() + " bytes");
                fieldSources.put("fileSize", FieldSourceInfo.fileMeta(
                        "fileSize", "File Size", doc.getFileSize() + " bytes",
                        "Binary byte size in MinIO storage", doc.getFileSize() + " bytes"
                ));
            }
            if (doc.getFileHash() != null) {
                fields.put("fileHash", doc.getFileHash());
                fieldSources.put("fileHash", FieldSourceInfo.fileMeta(
                        "fileHash", "File SHA-256 Digest", doc.getFileHash(),
                        "Cryptographic SHA-256 digest of document bytes", doc.getFileHash()
                ));
            }
            if (doc.getStoragePath() != null) {
                fields.put("storagePath", doc.getStoragePath());
                fieldSources.put("storagePath", FieldSourceInfo.fileMeta(
                        "storagePath", "MinIO Storage Object Key", doc.getStoragePath(),
                        "Object storage path in S3/MinIO bucket", doc.getStoragePath()
                ));
            }
            if (doc.getContentType() != null) {
                fields.put("contentType", doc.getContentType());
                fieldSources.put("contentType", FieldSourceInfo.fileMeta(
                        "contentType", "MIME Content Type", doc.getContentType(),
                        "HTTP Content-Type detected on document ingestion", doc.getContentType()
                ));
            }
            fields.put("documentVersion", "v" + doc.getVersion());
            fieldSources.put("documentVersion", FieldSourceInfo.appMeta(
                    "documentVersion", "Document Version", "v" + doc.getVersion(),
                    "Statutory submission document version sequence", "v" + doc.getVersion()
            ));
        }

        if (extractedData.getPageCount() != null) {
            fieldSources.put("pageCount", FieldSourceInfo.fileMeta(
                    "pageCount", "Page Count", extractedData.getPageCount() + (extractedData.getPageCount() == 1 ? " page" : " pages"),
                    "PDF page tree inspection", extractedData.getPageCount() + (extractedData.getPageCount() == 1 ? " page" : " pages")
            ));
        }

        String rawText = extractedData.getRawText();
        if (rawText != null && !rawText.isBlank()) {
            List<LocalDate> allDates = extractAllDates(rawText);
            extractedData.setDatesFound(allDates);

            // 1. Extract Expiry Date - only with explicit contextual pattern!
            LocalDate expiryDate = extractExpiryDate(rawText);
            if (expiryDate != null) {
                fields.put("expiryDate", expiryDate.toString());
                fieldSources.put("expiryDate", FieldSourceInfo.content(
                        "expiryDate", "Expiry Date", expiryDate.toString(),
                        "Contextual expiry regex pattern (valid up to / expires on / expiry date)",
                        "Valid upto " + expiryDate.toString()
                ));
            }

            // 2. Extract Issue Date
            LocalDate issueDate = extractIssueDate(rawText);
            if (issueDate != null) {
                fields.put("issueDate", issueDate.toString());
                fieldSources.put("issueDate", FieldSourceInfo.content(
                        "issueDate", "Issue Date", issueDate.toString(),
                        "Contextual issue date regex pattern (dated / date of issue / issued on)",
                        "Dated " + issueDate.toString()
                ));
            } else if (expiryDate == null && allDates.size() == 1 && containsRequirementContentSignals(rawText, requirementCode)) {
                fields.put("issueDate", allDates.get(0).toString());
                fieldSources.put("issueDate", FieldSourceInfo.content(
                        "issueDate", "Issue Date", allDates.get(0).toString(),
                        "Single calendar date matched in document with valid statutory domain signals",
                        allDates.get(0).toString()
                ));
            }

            // 3. Extract Certificate / Reference Number
            Matcher certMatcher = CERT_NUMBER_PATTERN.matcher(rawText);
            if (certMatcher.find()) {
                String certVal = certMatcher.group(1).trim();
                fields.put("certificateNumber", certVal);
                fieldSources.put("certificateNumber", FieldSourceInfo.content(
                        "certificateNumber", "Certificate / Reference Number", certVal,
                        "Statutory certificate/NOC/reference number pattern",
                        certMatcher.group(0).trim()
                ));
            }

            // 4. Extract PAN number
            Matcher panMatcher = PAN_PATTERN.matcher(rawText);
            if (panMatcher.find()) {
                String panVal = panMatcher.group(1).trim();
                fields.put("panNumber", panVal);
                fieldSources.put("panNumber", FieldSourceInfo.content(
                        "panNumber", "PAN Number", panVal,
                        "Income Tax PAN pattern [A-Z]{5}[0-9]{4}[A-Z]",
                        panVal
                ));
            }

            // 5. Extract PIN Code
            Matcher pinMatcher = PIN_CODE_PATTERN.matcher(rawText);
            if (pinMatcher.find()) {
                String pinVal = pinMatcher.group(1).trim();
                fields.put("pinCode", pinVal);
                fieldSources.put("pinCode", FieldSourceInfo.content(
                        "pinCode", "PIN Code", pinVal,
                        "Indian Postal PIN code pattern [1-9][0-9]{5}",
                        pinVal
                ));
            }

            // 6. Extract Land Area if applicable
            Matcher landMatcher = LAND_AREA_PATTERN.matcher(rawText);
            if (landMatcher.find()) {
                String landVal = landMatcher.group(0).trim();
                fields.put("landAreaMatched", landVal);
                fieldSources.put("landAreaMatched", FieldSourceInfo.content(
                        "landAreaMatched", "Land Area", landVal,
                        "Land area boundary pattern (acres / hectares / sq.m)",
                        landVal
                ));
            }

            // 7. Extract Institution Name heuristics
            String institutionName = extractInstitutionName(rawText);
            if (institutionName != null && !institutionName.isBlank()) {
                fields.put("institutionName", institutionName);
                fieldSources.put("institutionName", FieldSourceInfo.content(
                        "institutionName", "Institution Name", institutionName,
                        "Institution header and entity naming pattern",
                        institutionName
                ));
            }

            // 8. Extract Trust Name heuristics
            String trustName = extractTrustName(rawText);
            if (trustName != null && !trustName.isBlank()) {
                fields.put("trustName", trustName);
                fieldSources.put("trustName", FieldSourceInfo.content(
                        "trustName", "Trust / Society Name", trustName,
                        "Sponsoring Trust / Society entity pattern",
                        trustName
                ));
            }

            // 9. Extract Issuing Authority heuristics
            String authority = extractIssuingAuthority(rawText, requirementCode);
            if (authority != null && !authority.isBlank()) {
                fields.put("issuingAuthority", authority);
                fieldSources.put("issuingAuthority", FieldSourceInfo.content(
                        "issuingAuthority", "Issuing Authority", authority,
                        "Statutory authority catalog matching for " + requirementCode,
                        authority
                ));
            }

            // 10. Record category plausibility
            boolean academicLab = containsUnrelatedAcademicContent(rawText);
            boolean domainSignalsPresent = containsRequirementContentSignals(rawText, requirementCode);
            boolean plausible = domainSignalsPresent && !academicLab;
            fields.put("categoryPlausible", plausible);
            fieldSources.put("categoryPlausible", FieldSourceInfo.derived(
                    "categoryPlausible", "Category Plausibility", plausible,
                    "Statutory requirement domain signal verification & academic lab manual filter",
                    academicLab ? "Unrelated academic lab manual / coursework text detected" : (domainSignalsPresent ? "Document contains statutory domain terminology" : "Lacks required statutory terminology")
            ));

            if (academicLab) {
                fields.put("contentWarning", "Unrelated academic/lab manual content detected");
                fieldSources.put("contentWarning", FieldSourceInfo.derived(
                        "contentWarning", "Content Warning", "Unrelated academic/lab manual content detected",
                        "Unrelated coursework / lab manual keyword scanner",
                        "Detected laboratory/coursework patterns (e.g. algorithms, lab manual, experiment index)"
                ));
            }
        } else {
            fields.put("categoryPlausible", false);
            fieldSources.put("categoryPlausible", FieldSourceInfo.derived(
                    "categoryPlausible", "Category Plausibility", false,
                    "Document legibility validation",
                    "No readable text extracted"
            ));
        }

        // Attach field sources map for full provenance traceability
        fields.put("_fieldSources", fieldSources);
        extractedData.setFields(fields);
        return extractedData;
    }

    public boolean containsUnrelatedAcademicContent(String text) {
        if (text == null || text.isBlank()) return false;
        Matcher m = UNRELATED_ACADEMIC_PATTERN.matcher(text);
        return m.find();
    }

    public boolean containsRequirementContentSignals(String text, String requirementCode) {
        if (text == null || text.isBlank()) return false;
        String lower = text.toLowerCase(Locale.ENGLISH);

        return switch (requirementCode != null ? requirementCode.toUpperCase(Locale.ENGLISH) : "") {
            case "DOC_FIRE_SAFETY" ->
                    lower.contains("fire") || lower.contains("safety") || lower.contains("noc") ||
                    lower.contains("prevention") || lower.contains("life safety") || lower.contains("emergency") ||
                    lower.contains("extinguish") || lower.contains("cfo") || lower.contains("disaster management");

            case "DOC_OCCUPANCY_CERT", "DOC_BUILDING_PLAN" ->
                    lower.contains("occupancy") || lower.contains("completion") || lower.contains("building plan") ||
                    lower.contains("sanction") || lower.contains("municipal") || lower.contains("town planning") ||
                    lower.contains("floor area") || lower.contains("plinth") || lower.contains("corporation") ||
                    lower.contains("gram panchayat") || lower.contains("urban development") || lower.contains("architect");

            case "DOC_STRUCTURAL_STABILITY" ->
                    lower.contains("structural stability") || lower.contains("structural engineer") ||
                    lower.contains("chartered engineer") || lower.contains("stability certificate") ||
                    lower.contains("safe for occupancy") || lower.contains("load bearing") ||
                    lower.contains("seismic") || lower.contains("earthquake") || lower.contains("soundness");

            case "DOC_LAND_DOCUMENTS" ->
                    lower.contains("land") || lower.contains("sale deed") || lower.contains("lease deed") ||
                    lower.contains("sub-registrar") || lower.contains("survey no") || lower.contains("khasra") ||
                    lower.contains("patta") || lower.contains("mouza") || lower.contains("registration") ||
                    lower.contains("acres") || lower.contains("hectares") || lower.contains("schedule of property");

            case "DOC_TRUST_DEED" ->
                    lower.contains("trust deed") || lower.contains("society") || lower.contains("trustees") ||
                    lower.contains("memorandum of association") || lower.contains("societies registration") ||
                    lower.contains("charitable") || lower.contains("section 8") || lower.contains("deed of trust");

            case "DOC_AFFILIATING_NOC" ->
                    lower.contains("affiliation") || lower.contains("affiliating") || lower.contains("university") ||
                    lower.contains("board of technical") || lower.contains("consent of affiliation") || lower.contains("registrar");

            case "DOC_CORPUS_FUND" ->
                    lower.contains("fixed deposit") || lower.contains("fdr") || lower.contains("bank guarantee") ||
                    lower.contains("term deposit") || lower.contains("joint name") || lower.contains("regional officer") || lower.contains("aicte");

            case "DOC_PREVIOUS_EOA" ->
                    lower.contains("extension of approval") || lower.contains("eoa") || lower.contains("all india council") ||
                    lower.contains("aicte") || lower.contains("approval process handbook");

            case "DOC_FACULTY_ROLL" ->
                    lower.contains("faculty") || lower.contains("professor") || lower.contains("cadre") ||
                    lower.contains("designation") || lower.contains("qualification") || lower.contains("pay scale");

            case "DOC_AUDITED_ACCOUNTS" ->
                    lower.contains("balance sheet") || lower.contains("audited") || lower.contains("chartered accountant") ||
                    lower.contains("profit and loss") || lower.contains("audit report") || lower.contains("udis");

            default -> true; // For uncatalogued document types, do not strictly block
        };
    }

    public String getCategoryMismatchExplanation(String text, String requirementCode, String docName) {
        if (text == null || text.isBlank()) {
            return "No readable text content was extracted to verify document category.";
        }
        if (containsUnrelatedAcademicContent(text)) {
            return "Uploaded document '" + docName + "' contains academic lab manual / coursework text (e.g. algorithm experiments, student submissions) rather than statutory certificate content.";
        }
        if (!containsRequirementContentSignals(text, requirementCode)) {
            return "Uploaded document '" + docName + "' lacks domain-specific statutory terminology or authority certifications required for " + requirementCode + ".";
        }
        return null;
    }

    private LocalDate extractExpiryDate(String text) {
        Matcher matcher = EXPIRY_CONTEXT_PATTERN.matcher(text);
        if (matcher.find()) {
            String dateStr = matcher.group(1);
            LocalDate parsed = parseDateString(dateStr);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private LocalDate extractIssueDate(String text) {
        Matcher matcher = ISSUE_CONTEXT_PATTERN.matcher(text);
        if (matcher.find()) {
            String dateStr = matcher.group(1);
            LocalDate parsed = parseDateString(dateStr);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private List<LocalDate> extractAllDates(String text) {
        List<LocalDate> dates = new ArrayList<>();

        // Standard numeric dates DD/MM/YYYY
        Matcher m1 = DATE_PATTERN_1.matcher(text);
        while (m1.find()) {
            int d = Integer.parseInt(m1.group(1));
            int m = Integer.parseInt(m1.group(2));
            int y = Integer.parseInt(m1.group(3));
            if (isValidDate(d, m, y)) {
                try {
                    dates.add(LocalDate.of(y, m, d));
                } catch (Exception ignored) {
                }
            }
        }

        // Textual dates "15 August 2026"
        Matcher mText = DATE_PATTERN_TEXT.matcher(text);
        while (mText.find()) {
            LocalDate parsed = parseDateString(mText.group(0));
            if (parsed != null && !dates.contains(parsed)) {
                dates.add(parsed);
            }
        }

        return dates;
    }

    private LocalDate parseDateString(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        String clean = dateStr.replaceAll("(st|nd|rd|th)", "").trim();

        String[] patterns = {
                "d/M/yyyy", "d-M-yyyy", "d.M.yyyy",
                "yyyy-M-d", "yyyy/M/d",
                "d MMMM yyyy", "d MMM yyyy",
                "MMMM d, yyyy", "MMM d, yyyy"
        };

        for (String p : patterns) {
            try {
                DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                        .parseCaseInsensitive()
                        .appendPattern(p)
                        .toFormatter(Locale.ENGLISH);
                return LocalDate.parse(clean, formatter);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private boolean isValidDate(int d, int m, int y) {
        return (d >= 1 && d <= 31 && m >= 1 && m <= 12 && y >= 1970 && y <= 2099);
    }

    private String extractInstitutionName(String text) {
        Pattern p = Pattern.compile(
                "(?:name\\s*of\\s*(?:the)?\\s*institution|institution\\s*name|college\\s*name|issued\\s*to|in\\s*respect\\s*of)[^\\w\\r\\n]{0,5}([A-Z][A-Za-z0-9\\s&.,'()-]{5,80}(?:Institute|College|Academy|University|Campus|School)[A-Za-z0-9\\s&.,'()-]{0,40})",
                Pattern.CASE_INSENSITIVE
        );
        Matcher m = p.matcher(text);
        if (m.find()) {
            return m.group(1).replaceAll("\\s+", " ").trim();
        }

        // Direct matching of typical institution naming
        Pattern pDirect = Pattern.compile("\\b([A-Z][A-Za-z0-9\\s&.'()-]{3,40}(?:Institute of Technology|Engineering College|Technical Campus|Polytechnic|Institute of Management))\\b");
        Matcher mDirect = pDirect.matcher(text);
        if (mDirect.find()) {
            return mDirect.group(1).replaceAll("\\s+", " ").trim();
        }
        return null;
    }

    private String extractTrustName(String text) {
        Pattern p = Pattern.compile("\\b([A-Z][A-Za-z0-9\\s&.'()-]{3,50}(?:Trust|Educational Society|Education Society|Foundation|Samiti|Sanstha|Pratisthan))\\b");
        Matcher m = p.matcher(text);
        if (m.find()) {
            return m.group(1).replaceAll("\\s+", " ").trim();
        }
        return null;
    }

    private String extractIssuingAuthority(String text, String requirementCode) {
        if ("DOC_FIRE_SAFETY".equalsIgnoreCase(requirementCode)) {
            Pattern p = Pattern.compile("(?:Delhi Fire Service|Fire & Emergency Services|Department of Fire Services|Chief Fire Officer|State Disaster Response and Fire Services|Fire Prevention Wing|Directorate of Fire and Emergency)", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(text);
            if (m.find()) return m.group(0).trim();
            return null;
        }
        if ("DOC_OCCUPANCY_CERT".equalsIgnoreCase(requirementCode) || "DOC_BUILDING_PLAN".equalsIgnoreCase(requirementCode)) {
            Pattern p = Pattern.compile("(?:Municipal Corporation|Development Authority|Town and Country Planning|Gram Panchayat|Urban Development Authority|Public Works Department|Noida Authority|Greater Noida Authority|DDA|BBMP|CIDCO|MMRDA|Competent Authority)", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(text);
            if (m.find()) return m.group(0).trim();
            return null;
        }
        if ("DOC_STRUCTURAL_STABILITY".equalsIgnoreCase(requirementCode)) {
            Pattern p = Pattern.compile("(?:Chartered Engineer|Registered Structural Engineer|Consulting Structural Engineer|Institution of Engineers|Empanelled Structural Engineer)", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(text);
            if (m.find()) return m.group(0).trim();
            return null;
        }
        if ("DOC_LAND_DOCUMENTS".equalsIgnoreCase(requirementCode)) {
            Pattern p = Pattern.compile("(?:Sub-Registrar|District Magistrate|District Collector|Revenue Department|Tahsildar|Tehsildar|Town and Country Planning|Competent Authority)", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(text);
            if (m.find()) return m.group(0).trim();
            return null;
        }
        if ("DOC_TRUST_DEED".equalsIgnoreCase(requirementCode)) {
            Pattern p = Pattern.compile("(?:Registrar of Societies|Charity Commissioner|Sub-Registrar|Registrar of Companies|Ministry of Corporate Affairs)", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(text);
            if (m.find()) return m.group(0).trim();
            return null;
        }
        if ("DOC_AFFILIATING_NOC".equalsIgnoreCase(requirementCode) || "DOC_UNIVERSITY_NOC".equalsIgnoreCase(requirementCode)) {
            Pattern p = Pattern.compile("(?:Affiliating University|State Board of Technical Education|Directorate of Technical Education|University of|Board of Technical|Registrar)", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(text);
            if (m.find()) return m.group(0).trim();
            return null;
        }
        if ("DOC_CORPUS_FUND".equalsIgnoreCase(requirementCode)) {
            Pattern p = Pattern.compile("(?:State Bank of India|Punjab National Bank|Canara Bank|Bank of Baroda|Union Bank|HDFC Bank|ICICI Bank|Regional Officer, AICTE|All India Council for Technical Education)", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(text);
            if (m.find()) return m.group(0).trim();
            return null;
        }
        if ("DOC_PREVIOUS_EOA".equalsIgnoreCase(requirementCode)) {
            Pattern p = Pattern.compile("(?:All India Council for Technical Education|AICTE|Approval Process Handbook)", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(text);
            if (m.find()) return m.group(0).trim();
            return null;
        }
        if ("DOC_AUDITED_ACCOUNTS".equalsIgnoreCase(requirementCode)) {
            Pattern p = Pattern.compile("(?:Chartered Accountant|ICAI|Statutory Auditor|Auditor)", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(text);
            if (m.find()) return m.group(0).trim();
            return null;
        }
        if ("DOC_FACULTY_ROLL".equalsIgnoreCase(requirementCode)) {
            Pattern p = Pattern.compile("(?:Affiliating University|Registrar|Dean of Academic Affairs|Director of Technical Education)", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(text);
            if (m.find()) return m.group(0).trim();
            return null;
        }
        return null;
    }
}
