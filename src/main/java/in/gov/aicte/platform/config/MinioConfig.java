package in.gov.aicte.platform.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

    private static final Logger log = LoggerFactory.getLogger(MinioConfig.class);

    private final MinioProperties minioProperties;

    public MinioConfig(MinioProperties minioProperties) {
        this.minioProperties = minioProperties;
    }

    @Bean
    public MinioClient minioClient() {
        MinioClient client = MinioClient.builder()
                .endpoint(minioProperties.getEndpoint())
                .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                .region(minioProperties.getRegion())
                .build();

        if (minioProperties.isAutoCreateBucket()) {
            try {
                boolean found = client.bucketExists(
                        BucketExistsArgs.builder().bucket(minioProperties.getBucket()).build()
                );
                if (!found) {
                    client.makeBucket(
                            MakeBucketArgs.builder().bucket(minioProperties.getBucket()).build()
                    );
                    log.info("Initialized MinIO bucket: {}", minioProperties.getBucket());
                } else {
                    log.info("MinIO bucket '{}' already exists", minioProperties.getBucket());
                }
            } catch (Exception e) {
                log.warn("Could not verify or create MinIO bucket '{}' on startup: {}. It will be checked on demand.",
                        minioProperties.getBucket(), e.getMessage());
            }
        }

        return client;
    }
}
