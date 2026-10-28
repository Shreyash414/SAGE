package in.gov.aicte.platform.scrutiny.ocr;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for extracting text and metadata from uploaded scrutiny documents.
 *
 * Employs Apache PDFBox for high-fidelity extraction of embedded digital text streams.
 * The legacy Tess4J / Tesseract OCR engine has been retired and removed.
 * Scanned PDFs or raster images lacking extractable digital text return an explicit
 * UNAVAILABLE status without fabricating artificial OCR text or confidence scores,
 * ensuring strict regulatory safety and manual officer verification.
 */
@Service
public class DocumentTextExtractionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentTextExtractionService.class);

    private static final int SEARCHABLE_TEXT_THRESHOLD_CHARS = 40;

    public DocumentTextExtractionService() {
    }

    /**
     * Indicates whether an automated optical character recognition engine is active.
     * With the retirement of Tesseract/Tess4J, this explicitly returns false until
     * a next-generation neural/vision model is integrated.
     */
    public boolean isOcrAvailable() {
        return false;
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

            // Apache PDFBox text extraction (Searchable PDF with embedded character stream)
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String searchableText = stripper.getText(document);
            String trimmedText = (searchableText != null) ? searchableText.trim() : "";

            if (trimmedText.length() >= SEARCHABLE_TEXT_THRESHOLD_CHARS) {
                log.info("Extracted searchable digital text from PDF via Apache PDFBox (chars: {}, pages: {})", trimmedText.length(), totalPages);
                // For native digital text, optical character recognition is not performed.
                // Never fabricate an artificial OCR confidence score for embedded digital text!
                return ExtractedDocumentData.embeddedText(trimmedText, totalPages);
            }

            // Check if pages contain raster images (scanned PDF)
            boolean hasRasterImages = containsImages(document);

            if (hasRasterImages) {
                log.info("PDF has minimal or no searchable digital text, but contains raster images (scanned PDF). Flagging for manual review: {}", fileName);
                ExtractedDocumentData data = ExtractedDocumentData.unavailable(
                        "Scanned or non-searchable PDF detected (" + trimmedText.length() + " digital chars found). "
                                + "Legacy OCR engine is retired. Document flagged for Scrutiny Officer manual review."
                );
                data.setPageCount(totalPages);
                return data;
            }

            if (trimmedText.isEmpty()) {
                log.info("PDF document contains no digital text and no raster images (blank document): {}", fileName);
                ExtractedDocumentData data = ExtractedDocumentData.empty("PDF document contains no text or visual content (blank document).");
                data.setPageCount(totalPages);
                return data;
            }

            // Minimal text found (< 40 chars) and no images
            log.info("PDF document contains only minimal digital text ({} chars) and no images: {}", trimmedText.length(), fileName);
            ExtractedDocumentData data = new ExtractedDocumentData(
                    trimmedText,
                    "PDFBOX_SEARCHABLE_TEXT",
                    null,
                    "UNCERTAIN",
                    trimmedText.length(),
                    totalPages,
                    "INSUFFICIENT_SEARCHABLE_TEXT"
            );
            data.setErrorMessage("Document has fewer than " + SEARCHABLE_TEXT_THRESHOLD_CHARS + " characters of searchable text and no scanned images.");
            return data;

        } catch (Exception e) {
            log.error("Failed to parse PDF document {}: {}", fileName, e.getMessage());
            return ExtractedDocumentData.failed("Corrupted or unreadable PDF document: " + e.getMessage());
        }
    }

    private boolean containsImages(PDDocument document) {
        try {
            for (PDPage page : document.getPages()) {
                if (page.getResources() != null) {
                    for (COSName name : page.getResources().getXObjectNames()) {
                        if (page.getResources().isImageXObject(name)) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Error checking PDF page resources for images: {}", e.getMessage());
        }
        return false;
    }

    private ExtractedDocumentData extractFromImage(byte[] imageBytes, String fileName) {
        log.info("Raster image uploaded ({}). Legacy OCR engine is retired; flagging for manual review.", fileName);
        return ExtractedDocumentData.unavailable(
                "Image document uploaded (" + fileName + "). Legacy OCR engine is retired. Document flagged for Scrutiny Officer manual review."
        );
    }
}
