package in.gov.aicte.platform.scrutiny.ocr;

import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import net.sourceforge.tess4j.Word;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

@Service
public class TesseractOcrService {

    private static final Logger log = LoggerFactory.getLogger(TesseractOcrService.class);

    private final String dataPath;
    private final String language;
    private ITesseract tesseractInstance;
    private Boolean available = null;

    public TesseractOcrService() {
        this("", "eng");
    }

    public TesseractOcrService(
            @Value("${sage.ocr.tesseract.data-path:}") String dataPath,
            @Value("${sage.ocr.tesseract.language:eng}") String language
    ) {
        this.dataPath = dataPath;
        this.language = (language != null && !language.isBlank()) ? language : "eng";
    }

    /**
     * Testing constructor allowing injection of mock or preconfigured ITesseract instances.
     */
    public TesseractOcrService(ITesseract tesseractInstance) {
        this.dataPath = "";
        this.language = "eng";
        this.tesseractInstance = tesseractInstance;
        this.available = (tesseractInstance != null);
    }

    public synchronized void setTesseractInstance(ITesseract tesseractInstance) {
        this.tesseractInstance = tesseractInstance;
        this.available = (tesseractInstance != null);
    }

    public synchronized boolean isAvailable() {
        if (Boolean.TRUE.equals(available) && tesseractInstance != null) {
            return true;
        }

        try {
            String effectiveDataPath = resolveDataPath();
            if (effectiveDataPath == null || effectiveDataPath.isBlank()) {
                log.info("Tesseract language data path could not be resolved on host. Scanned documents will be flagged for Scrutiny Officer manual review.");
                available = false;
                return false;
            }

            File dir = new File(effectiveDataPath);
            File trainedData = new File(dir, language + ".traineddata");
            if (!dir.exists() || !dir.isDirectory() || !trainedData.exists()) {
                log.info("Tesseract datapath or {}.traineddata does not exist at: {}. Scanned documents will use manual-review fallback.", language, dir.getAbsolutePath());
                available = false;
                return false;
            }

            tesseractInstance = new Tesseract();
            tesseractInstance.setLanguage(language);
            tesseractInstance.setDatapath(dir.getAbsolutePath());
            tesseractInstance.setOcrEngineMode(1); // LSTM only
            available = true;
            log.info("Tesseract OCR (Tess4J) engine initialized successfully with datapath: {}", dir.getAbsolutePath());
        } catch (Throwable t) {
            available = false;
            log.warn("Tesseract OCR (Tess4J) native binary/data not available on host environment: {}. Scanned documents will use manual-review fallback.", t.getMessage());
        }
        return available;
    }

    public String extractText(BufferedImage image) throws Exception {
        return extractTextWithConfidence(image).text();
    }

    /**
     * Executes Tess4J OCR and aggregates word-level optical recognition confidence scores.
     *
     * Confidence aggregation methodology:
     * 1. Extract individual word bounding tokens and recognition confidences via RIL_WORD.
     * 2. Filter out whitespace/empty tokens and negative sentinel confidence values (-1.0f).
     * 3. Compute the arithmetic mean of word confidence percentages across all valid tokens.
     * 4. Normalize to [0.0 - 1.0] scale rounded to 4 decimal places.
     * 5. If no valid words are recognized or confidence extraction fails, return null
     *    to prevent fabrication of artificial fallback confidence scores.
     */
    public OcrPageResult extractTextWithConfidence(BufferedImage image) throws Exception {
        if (!isAvailable()) {
            throw new IllegalStateException("Tesseract OCR is not available on this host.");
        }
        if (image == null) {
            return OcrPageResult.empty();
        }
        try {
            String text = tesseractInstance.doOCR(image);
            String trimmedText = text != null ? text.trim() : "";

            List<Word> words = null;
            try {
                words = tesseractInstance.getWords(image, ITessAPI.TessPageIteratorLevel.RIL_WORD);
            } catch (Throwable t) {
                log.warn("Tess4J getWords confidence extraction failed on image: {}. Retaining text without fabricated confidence.", t.getMessage());
            }

            if (words == null || words.isEmpty()) {
                return new OcrPageResult(trimmedText, null, 0, trimmedText.length());
            }

            double sumConfidence = 0.0;
            int validWordCount = 0;
            for (Word w : words) {
                if (w != null && w.getText() != null && !w.getText().isBlank()) {
                    float conf = w.getConfidence();
                    if (conf >= 0.0f) {
                        sumConfidence += conf;
                        validWordCount++;
                    }
                }
            }

            Double aggregatedConfidence = null;
            if (validWordCount > 0) {
                double meanPercent = sumConfidence / validWordCount;
                aggregatedConfidence = Math.round((meanPercent / 100.0) * 10000.0) / 10000.0;
            }

            return new OcrPageResult(trimmedText, aggregatedConfidence, validWordCount, trimmedText.length());
        } catch (TesseractException te) {
            log.error("Tess4J OCR execution failed on image: {}", te.getMessage());
            throw te;
        } catch (Throwable t) {
            log.error("Unexpected error during Tesseract execution: {}", t.getMessage());
            throw new Exception("Tesseract processing error: " + t.getMessage(), t);
        }
    }

    private String resolveDataPath() {
        if (dataPath != null && !dataPath.isBlank()) {
            File f = new File(dataPath);
            if (f.exists() && new File(f, language + ".traineddata").exists()) return f.getAbsolutePath();
        }
        String envTessdata = System.getenv("TESSDATA_PREFIX");
        if (envTessdata != null && !envTessdata.isBlank()) {
            File f = new File(envTessdata);
            if (f.exists() && new File(f, language + ".traineddata").exists()) return f.getAbsolutePath();
        }

        // Check classpath resource for packaged app/jar execution
        try {
            java.net.URL resource = getClass().getClassLoader().getResource("tessdata/" + language + ".traineddata");
            if (resource != null) {
                if ("file".equalsIgnoreCase(resource.getProtocol())) {
                    File file = new File(resource.toURI());
                    if (file.exists()) {
                        return file.getParentFile().getAbsolutePath();
                    }
                } else {
                    File tempTessDir = new File(System.getProperty("java.io.tmpdir"), "sage_tessdata");
                    tempTessDir.mkdirs();
                    File target = new File(tempTessDir, language + ".traineddata");
                    if (!target.exists() || target.length() == 0) {
                        try (java.io.InputStream in = resource.openStream();
                             java.io.FileOutputStream out = new java.io.FileOutputStream(target)) {
                            in.transferTo(out);
                        }
                    }
                    return tempTessDir.getAbsolutePath();
                }
            }
        } catch (Throwable ignored) {
        }

        String userDir = System.getProperty("user.dir");
        // Check standard platform and workspace locations
        String[] candidatePaths = {
                userDir + File.separator + "src" + File.separator + "main" + File.separator + "resources" + File.separator + "tessdata",
                userDir + File.separator + "tessdata",
                userDir + File.separator + "target" + File.separator + "classes" + File.separator + "tessdata",
                "src/main/resources/tessdata",
                "tessdata",
                "target/classes/tessdata",
                "C:\\Users\\alone\\Downloads\\tessdata",
                "C:\\Program Files\\Tesseract-OCR\\tessdata",
                "C:\\Program Files (x86)\\Tesseract-OCR\\tessdata",
                "/usr/share/tesseract-ocr/4.00/tessdata",
                "/usr/share/tesseract-ocr/5/tessdata",
                "/usr/share/tessdata"
        };
        for (String path : candidatePaths) {
            File f = new File(path);
            if (f.exists() && new File(f, language + ".traineddata").exists()) {
                return f.getAbsolutePath();
            }
        }
        return null;
    }
}
