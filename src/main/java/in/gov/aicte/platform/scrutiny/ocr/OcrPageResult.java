package in.gov.aicte.platform.scrutiny.ocr;

/**
 * Encapsulates the output of OCR processing for an image or document page,
 * capturing the extracted text alongside the actual measured optical confidence score.
 *
 * @param text The recognized text.
 * @param confidence The measured optical confidence [0.0 - 1.0], or null if unmeasured / no words recognized.
 * @param wordCount Number of valid word tokens evaluated.
 * @param characterCount Length of recognized text in characters.
 */
public record OcrPageResult(
        String text,
        Double confidence,
        int wordCount,
        int characterCount
) {
    public static OcrPageResult empty() {
        return new OcrPageResult("", null, 0, 0);
    }
}
