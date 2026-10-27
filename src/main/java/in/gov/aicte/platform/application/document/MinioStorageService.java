package in.gov.aicte.platform.application.document;

import in.gov.aicte.platform.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class MinioStorageService {

    private static final Logger log = LoggerFactory.getLogger(MinioStorageService.class);

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    public MinioStorageService(MinioClient minioClient, MinioProperties minioProperties) {
        this.minioClient = minioClient;
        this.minioProperties = minioProperties;
    }

    public record StoredObjectResult(
            String storageKey,
            String bucket,
            String sha256Checksum,
            long byteSize,
            String contentType
    ) {}

    public synchronized void ensureBucketExists() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(minioProperties.getBucket()).build()
            );
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(minioProperties.getBucket()).build()
                );
                log.info("Created MinIO bucket: {}", minioProperties.getBucket());
            }
        } catch (Exception e) {
            log.error("Failed to verify/create MinIO bucket: {}", e.getMessage());
            throw new RuntimeException("Storage service initialization error: unable to ensure bucket exists.", e);
        }
    }

    public StoredObjectResult uploadDocument(
            Long applicationId,
            String requirementCode,
            int version,
            String originalFilename,
            String contentType,
            byte[] bytes
    ) {
        ensureBucketExists();

        String sha256Checksum = calculateSha256(bytes);
        String extension = extractExtension(originalFilename);
        String storageKey = String.format(
                "applications/%s/%s/v%d_%s%s",
                applicationId,
                sanitizeRequirementCode(requirementCode),
                version,
                UUID.randomUUID(),
                extension
        );

        String effectiveContentType = (contentType != null && !contentType.isBlank())
                ? contentType
                : "application/octet-stream";

        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(storageKey)
                            .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                            .contentType(effectiveContentType)
                            .build()
            );
            log.info("Uploaded document to MinIO bucket '{}' with key '{}', size: {} bytes",
                    minioProperties.getBucket(), storageKey, bytes.length);

            return new StoredObjectResult(
                    storageKey,
                    minioProperties.getBucket(),
                    sha256Checksum,
                    bytes.length,
                    effectiveContentType
            );
        } catch (Exception e) {
            log.error("Failed to upload object to MinIO: {}", e.getMessage());
            throw new RuntimeException("Document upload to object storage failed.", e);
        }
    }

    public InputStream downloadDocument(String storageKey) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(storageKey)
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to retrieve object '{}' from MinIO: {}", storageKey, e.getMessage());
            throw new RuntimeException("Document retrieval from object storage failed.", e);
        }
    }

    public void deleteDocument(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(storageKey)
                            .build()
            );
            log.info("Removed object '{}' from MinIO", storageKey);
        } catch (Exception e) {
            log.warn("Failed to delete object '{}' from MinIO: {}", storageKey, e.getMessage());
        }
    }

    private String calculateSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return ".bin";
        }
        String ext = filename.substring(filename.lastIndexOf('.')).toLowerCase();
        if (ext.matches("^\\.[a-z0-9]{1,10}$")) {
            return ext;
        }
        return ".bin";
    }

    private String sanitizeRequirementCode(String code) {
        if (code == null) return "doc";
        return code.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
