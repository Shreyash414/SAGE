package in.gov.aicte.platform.scrutiny.ocr;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

@Service
public class DocumentTextExtractionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentTextExtractionService.class);

    private static final int MAX_PAGES_TO_SCAN = 5;
    private static final int SEARCHABLE_TEXT_THRESHOLD_CHARS = 40;

    private final TesseractOcrService tesseractOcrService;

    public DocumentTextExtractionService(TesseractOcrService tesseractOcrService) {
        this.tesseractOcrService = tesseractOcrService;
    }

    public boolean isOcrAvailable() {
        return tesseractOcrService != null && tesseractOcrService.isAvailable();
    }

    public ExtractedDocumentData extractText(byte[] documentBytes, String fileName, String contentType) {
        if (documentBytes == null || documentBytes.length == 0) {
            return ExtractedDocumentData.empty("Document byte payload is empty (0 bytes).");
        }

        String ct = (contentType != null) ? contentType.toLowerCase() : "";
        String fn = (fileName != null) ? fileName.toLowerCase() : "";

        if (ct.contains("pdf") || fn.endsWith(".pdf")) {
            return extractFromPdf(documentBytes, fileName);
        } else if (ct.contains("image") || fn.endsWith(".png") || fn.endsWith(".jpg") || fn.endsWith(".jpeg") || fn.endsWith(".tiff")) {
            return extractFromImage(documentBytes, fileName);
        } else {
            return ExtractedDocumentData.unavailable("Unsupported document format for automated text extraction: " + contentType);
        }
    }

    private ExtractedDocumentData extractFromPdf(byte[] pdfBytes, String fileName) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            int totalPages = document.getNumberOfPages();
            log.debug("Processing PDF document: {} (pages: {})", fileName, totalPages);

            if (totalPages == 0) {
                return ExtractedDocumentData.empty("PDF document contains 0 pages.");
            }

            // 1. Try Apache PDFBox text extraction (Searchable PDF with embedded character stream)
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String searchableText = stripper.getText(document);
            String trimmedText = (searchableText != null) ? searchableText.trim() : "";

            if (trimmedText.length() >= SEARCHABLE_TEXT_THRESHOLD_CHARS) {
                log.info("Extracted searchable text from PDF via Apache PDFBox (chars: {}, pages: {})", trimmedText.length(), totalPages);
                // For native digital text, optical character recognition is not performed.
                // Never fabricate an artificial OCR confidence score for embedded digital text!
                return ExtractedDocumentData.embeddedText(trimmedText, totalPages);
            }

            // 2. Scanned PDF fallback -> Render pages to images and execute Tesseract OCR
            log.info("PDF has minimal searchable text (scanned PDF or blank detected). Attempting page rendering for OCR: {}", fileName);

            if (!tesseractOcrService.isAvailable()) {
                if (trimmedText.isEmpty()) {
                    ExtractedDocumentData data = ExtractedDocumentData.unavailable(
                            "Scanned or non-searchable PDF detected. Native Tesseract OCR engine is not configured on the host system. Document flagged for Scrutiny Officer manual review."
                    );
                    data.setPageCount(totalPages);
                    return data;
                } else {
                    // Minimal text found (< 40 chars)
                    ExtractedDocumentData data = new ExtractedDocumentData(
                            trimmedText,
                            "PDFBOX_SEARCHABLE_TEXT",
                            null,
                            "UNCERTAIN",
                            trimmedText.length(),
                            totalPages,
                            "INSUFFICIENT_SEARCHABLE_TEXT"
                    );
                    data.setErrorMessage("Document has fewer than " + SEARCHABLE_TEXT_THRESHOLD_CHARS + " characters of searchable text and OCR is unavailable.");
                    return data;
                }
            }

            PDFRenderer renderer = new PDFRenderer(document);
            StringBuilder ocrTextAccumulator = new StringBuilder();
            int pagesToProcess = Math.min(totalPages, MAX_PAGES_TO_SCAN);
            double totalWordConfidenceSum = 0.0;
            int totalWordsCount = 0;

            for (int pageIndex = 0; pageIndex < pagesToProcess; pageIndex++) {
                try {
                    // Render page at 200 DPI for optimal OCR balance between accuracy and RAM
                    BufferedImage pageImage = renderer.renderImageWithDPI(pageIndex, 200);
                    OcrPageResult pageResult = tesseractOcrService.extractTextWithConfidence(pageImage);
                    if (pageResult.text() != null && !pageResult.text().isBlank()) {
                        ocrTextAccumulator.append(pageResult.text()).append("\n--- PAGE ").append(pageIndex + 1).append(" ---\n");
                    }
                    if (pageResult.confidence() != null && pageResult.wordCount() > 0) {
                        totalWordConfidenceSum += (pageResult.confidence() * 100.0) * pageResult.wordCount();
                        totalWordsCount += pageResult.wordCount();
                    }
                } catch (Exception e) {
                    log.warn("OCR failed on page {} of {}: {}", pageIndex + 1, fileName, e.getMessage());
                }
            }

            String finalOcrText = ocrTextAccumulator.toString().trim();
            if (!finalOcrText.isBlank()) {
                Double aggregateConfidence = null;
                if (totalWordsCount > 0) {
                    double meanPercent = totalWordConfidenceSum / totalWordsCount;
                    aggregateConfidence = Math.round((meanPercent / 100.0) * 10000.0) / 10000.0;
                }
                return ExtractedDocumentData.ocr(
                        finalOcrText,
                        aggregateConfidence,
                        totalPages,
                        finalOcrText.length(),
                        "TESSERACT_OCR_SCANNED"
                );
            } else {
                if (trimmedText.isEmpty()) {
                    ExtractedDocumentData data = ExtractedDocumentData.empty(
                            "Scanned PDF page rendering and OCR produced no recognized characters (blank or unreadable document)."
                    );
                    data.setExtractionMethod("TESSERACT_OCR_SCANNED");
                    data.setPageCount(totalPages);
                    return data;
                } else {
                    ExtractedDocumentData data = new ExtractedDocumentData(
                            trimmedText,
                            "PDFBOX_SEARCHABLE_TEXT",
                            null,
                            "UNCERTAIN",
                            trimmedText.length(),
                            totalPages,
                            "INSUFFICIENT_SEARCHABLE_TEXT"
                    );
                    data.setErrorMessage("Document contains only minimal text (" + trimmedText.length() + " chars) and OCR detected no additional characters.");
                    return data;
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse PDF document {}: {}", fileName, e.getMessage());
            return ExtractedDocumentData.failed("Corrupted or unreadable PDF document: " + e.getMessage());
        }
    }

    private ExtractedDocumentData extractFromImage(byte[] imageBytes, String fileName) {
        if (!tesseractOcrService.isAvailable()) {
            return ExtractedDocumentData.unavailable(
                    "Image document uploaded. Tesseract OCR is not available on host. Document flagged for manual review."
            );
        }

        try (ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes)) {
            BufferedImage image = ImageIO.read(bais);
            if (image == null) {
                return ExtractedDocumentData.failed("Unable to decode image stream for " + fileName);
            }
            OcrPageResult pageResult = tesseractOcrService.extractTextWithConfidence(image);
            String ocrText = pageResult.text();
            if (ocrText != null && !ocrText.isBlank()) {
                return ExtractedDocumentData.ocr(
                        ocrText.trim(),
                        pageResult.confidence(),
                        1,
                        ocrText.trim().length(),
                        "TESSERACT_OCR_IMAGE"
                );
            } else {
                ExtractedDocumentData data = ExtractedDocumentData.empty("Tesseract produced empty output for image: " + fileName);
                data.setExtractionMethod("TESSERACT_OCR_IMAGE");
                return data;
            }
        } catch (Exception e) {
            log.error("Image OCR failed for {}: {}", fileName, e.getMessage());
            return ExtractedDocumentData.failed("Image processing error: " + e.getMessage());
        }
    }
}
