package yvon.backend.attachment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "taskflow.attachment")
public class AttachmentProperties {

    @Valid
    private final Minio minio = new Minio();

    @Min(1)
    private long maxFileSizeBytes = 10 * 1024 * 1024L;

    @Min(60)
    private int presignedExpirySeconds = 600;

    public Minio getMinio() { return minio; }
    public long getMaxFileSizeBytes() { return maxFileSizeBytes; }
    public void setMaxFileSizeBytes(long maxFileSizeBytes) { this.maxFileSizeBytes = maxFileSizeBytes; }
    public int getPresignedExpirySeconds() { return presignedExpirySeconds; }
    public void setPresignedExpirySeconds(int presignedExpirySeconds) { this.presignedExpirySeconds = presignedExpirySeconds; }

    public static class Minio {
        @NotBlank
        private String endpoint;
        @NotBlank
        private String accessKey;
        @NotBlank
        private String secretKey;
        @NotBlank
        private String bucket = "taskflow-attachments";

        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public String getAccessKey() { return accessKey; }
        public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }
    }
}
