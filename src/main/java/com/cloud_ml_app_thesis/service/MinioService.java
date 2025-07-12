package com.cloud_ml_app_thesis.service;

import com.cloud_ml_app_thesis.config.BucketResolver;
import com.cloud_ml_app_thesis.entity.User;
import com.cloud_ml_app_thesis.enumeration.BucketTypeEnum;
import com.cloud_ml_app_thesis.enumeration.DatasetFunctionalTypeEnum;
import com.cloud_ml_app_thesis.exception.FileProcessingException;
import com.cloud_ml_app_thesis.exception.MinioFileUploadException;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import weka.core.SerializationHelper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinioService {

    private static final Logger logger = LoggerFactory.getLogger(MinioService.class);

    private final MinioClient minioClient;

    private final BucketResolver bucketResolver;

    @Value("#{'${minio.bucket.datasets},${minio.bucket.models},${minio.bucket.predictions},${minio.bucket.algorithms},${minio.bucket.results},${minio.bucket.metrics},${minio.bucket.parameters}'.split(',')}")
    private List<String> allBuckets;

    public void uploadObjectToBucket(Object object, String bucketName, String objectName) throws IOException {
        //Ensure that the bucket that is going to get the object is still being offered by the application
        // in order to prevent forgotten hard-coded name in code.
        if(!isKnownBucket(bucketName)){
            logger.error("Error: " + "Invalid bucket name: " + bucketName);
            throw new IllegalArgumentException("Invalid bucket name: " + bucketName);
        }

        InputStream streamToUpload = null;
        String contentType = null;
        long size = 0;
        if(object instanceof MultipartFile file){
            streamToUpload = file.getInputStream();
            contentType = file.getContentType();
            size = file.getSize();
        } else if (object instanceof byte[] model) {
            streamToUpload = new ByteArrayInputStream(model);
            contentType = "application/octet-stream";
            size = model.length;
        }

        PutObjectArgs args = PutObjectArgs.builder()
                .bucket(bucketName)
                .object(objectName)
                .stream(streamToUpload, size, -1)
                .contentType(contentType)
                .build();

        try {
            minioClient.putObject(args);
        } catch (ErrorResponseException | InsufficientDataException | InternalException | InvalidKeyException |
                 InvalidResponseException | NoSuchAlgorithmException | ServerException | XmlParserException e) {
            throw new RuntimeException(e);
        }

    }

    public Path downloadObjectToTempFile(String bucketName, String objectKey) {
        log.info("📥 Downloading [{}]/[{}] from MinIO...", bucketName, objectKey);

        try (InputStream inputStream = loadObjectAsInputStream(bucketName, objectKey)) {
            String safeName = objectKey.replace("/", "_");
            Path tempFile = Files.createTempFile("minio-", "-" + safeName);
            Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);

            if (!Files.exists(tempFile)) {
                throw new FileProcessingException("❌ File does not exist after copy: " + tempFile);
            }
            if (!Files.isRegularFile(tempFile)) {
                throw new FileProcessingException("❌ File is not regular file: " + tempFile);
            }

            log.info("✅ Temp file saved at: {}", tempFile.toAbsolutePath());
            return tempFile;
        } catch (IOException e) {
            throw new FileProcessingException("Failed to download file from MinIO: " + objectKey, e);
        }
    }


    //Overload this method for tag name
    public void uploadToMinio(InputStream inputStream, String bucketName, String objectName, long size, String contentType) {
        if (!isKnownBucket(bucketName)) {
            logger.error("Error: Invalid bucket name: {}", bucketName);
            throw new IllegalArgumentException("Invalid bucket name: " + bucketName);
        }

        if (inputStream == null) {
            logger.error("❌ InputStream is null for object: {}", objectName);
            throw new IllegalArgumentException("stream must not be null");
        }

        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, size, -1)
                            .contentType(contentType)
                            .build()
            );
            logger.info("✅ Uploaded object [{}] to bucket [{}] ({} bytes)", objectName, bucketName, size);
        } catch (Exception e) {
            logger.error("❌ Failed to upload object [{}] to bucket [{}]: {}", objectName, bucketName, e.getMessage(), e);
            throw new FileProcessingException("MinIO upload failed for: " + objectName, e);
        }
    }
    private boolean isKnownBucket(String bucketName){
        return allBuckets.contains(bucketName);
    }


    public byte[] downloadObjectAsBytes(String bucket, String key) {
        try (InputStream in = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(key)
                        .build())) {
            return in.readAllBytes();
        } catch (Exception e) {
            throw new FileProcessingException("❌ Failed to download object from MinIO: " + key, e);
        }
    }

    public InputStream loadObjectAsInputStream(String bucketName, String fileReference) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(fileReference)
                            .build()
            );
        } catch (Exception e) {
            throw new FileProcessingException("Error fetching file from MinIO: " + e.getMessage(), e);
        }
    }
    public Object loadObject(String bucketName, String fileReference) {
        try {
            return SerializationHelper.read(minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(fileReference)
                            .build()
            ));
        } catch (Exception e) {
            throw new FileProcessingException("Error fetching file from MinIO: " + e.getMessage(), e);
        }
    }

    public String extractMinioKey(String minioUrl) {
        if (minioUrl == null || minioUrl.isBlank()) {
            throw new IllegalArgumentException("MinIO URL cannot be null or empty");
        }

        try {
            // Remove protocol and host
            String path = new java.net.URL(minioUrl).getPath(); // /bucketName/objectKey
            String[] parts = path.split("/", 3); // ["", "bucketName", "objectKey"]
            if (parts.length < 3) {
                throw new IllegalArgumentException("Invalid MinIO URL format: " + minioUrl);
            }
            return parts[2]; // objectKey
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract key from MinIO URL: " + minioUrl, e);
        }
    }

}
