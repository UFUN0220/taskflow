package yvon.backend.attachment;

import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.BucketExistsArgs;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "taskflow.attachment.enabled", havingValue = "true")
public class MinioObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(MinioObjectStorage.class);
    private final MinioClient client;
    private final AttachmentProperties properties;
    private volatile boolean bucketReady;

    public MinioObjectStorage(MinioClient client, AttachmentProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public void put(String objectKey, byte[] content, String contentType) throws Exception {
        ensureBucket();
        try (InputStream input = new ByteArrayInputStream(content)) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(properties.getMinio().getBucket())
                    .object(objectKey)
                    .stream(input, content.length, -1)
                    .contentType(contentType)
                    .build());
        }
    }

    public InputStream get(String objectKey) throws Exception {
        ensureBucket();
        return client.getObject(GetObjectArgs.builder()
                .bucket(properties.getMinio().getBucket())
                .object(objectKey)
                .build());
    }

    public String presignedGet(String objectKey) throws Exception {
        ensureBucket();
        return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.GET)
                .bucket(properties.getMinio().getBucket())
                .object(objectKey)
                .expiry(properties.getPresignedExpirySeconds(), TimeUnit.SECONDS)
                .build());
    }

    public void remove(String objectKey) throws Exception {
        ensureBucket();
        client.removeObject(RemoveObjectArgs.builder()
                .bucket(properties.getMinio().getBucket())
                .object(objectKey)
                .build());
    }

    private void ensureBucket() throws Exception {
        if (bucketReady) return;
        synchronized (this) {
            if (bucketReady) return;
            String bucket = properties.getMinio().getBucket();
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created attachment bucket {}", bucket);
            }
            bucketReady = true;
        }
    }
}
