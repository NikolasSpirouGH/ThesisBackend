package com.cloud_ml_app_thesis.unit_tests.service.minio;

import com.cloud_ml_app_thesis.config.BucketResolver;
import com.cloud_ml_app_thesis.exception.FileProcessingException;
import com.cloud_ml_app_thesis.service.MinioService;
import io.minio.*;
import io.minio.errors.*;
import io.minio.messages.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MinioServiceCopyTest {

    @Mock
    private MinioClient minioClient;

    @Mock
    private BucketResolver bucketResolver;

    private MinioService minioService;

    private static final String SOURCE_BUCKET = "datasets";
    private static final String SOURCE_KEY = "path/to/source-file.csv";
    private static final String TARGET_BUCKET = "datasets";
    private static final String TARGET_KEY = "path/to/target-file.csv";

    @BeforeEach
    void setUp() {
        minioService = new MinioService(minioClient, bucketResolver);
        // Set the allBuckets field via reflection since it's @Value injected
        ReflectionTestUtils.setField(minioService, "allBuckets",
            List.of("datasets", "models", "predictions", "algorithms", "results", "metrics", "parameters"));
    }

    @Nested
    @DisplayName("Copy Object Tests")
    class CopyObjectTests {

        @Test
        @DisplayName("Should copy object successfully within same bucket")
        void copyObject_SameBucket_Success() throws Exception {
            // Given
            ObjectWriteResponse mockResponse = mock(ObjectWriteResponse.class);
            when(minioClient.copyObject(any(CopyObjectArgs.class))).thenReturn(mockResponse);

            // When
            String result = minioService.copyObject(SOURCE_BUCKET, SOURCE_KEY, SOURCE_BUCKET, "new-path/file.csv");

            // Then
            assertEquals("new-path/file.csv", result);
            verify(minioClient).copyObject(any(CopyObjectArgs.class));
        }

        @Test
        @DisplayName("Should copy object successfully across buckets")
        void copyObject_DifferentBuckets_Success() throws Exception {
            // Given
            ObjectWriteResponse mockResponse = mock(ObjectWriteResponse.class);
            when(minioClient.copyObject(any(CopyObjectArgs.class))).thenReturn(mockResponse);

            // When
            String result = minioService.copyObject(SOURCE_BUCKET, SOURCE_KEY, "models", TARGET_KEY);

            // Then
            assertEquals(TARGET_KEY, result);

            ArgumentCaptor<CopyObjectArgs> captor = ArgumentCaptor.forClass(CopyObjectArgs.class);
            verify(minioClient).copyObject(captor.capture());

            CopyObjectArgs args = captor.getValue();
            assertEquals("models", args.bucket());
            assertEquals(TARGET_KEY, args.object());
        }

        @Test
        @DisplayName("Should throw FileProcessingException when MinIO client throws exception")
        void copyObject_MinioError_ThrowsFileProcessingException() throws Exception {
            // Given
            when(minioClient.copyObject(any(CopyObjectArgs.class)))
                    .thenThrow(new ServerException("Internal server error", 500, ""));

            // When/Then
            assertThrows(FileProcessingException.class, () ->
                    minioService.copyObject(SOURCE_BUCKET, SOURCE_KEY, TARGET_BUCKET, TARGET_KEY));
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for invalid source bucket")
        void copyObject_InvalidSourceBucket_ThrowsIllegalArgumentException() {
            // When/Then
            assertThrows(IllegalArgumentException.class, () ->
                    minioService.copyObject("invalid-bucket", SOURCE_KEY, TARGET_BUCKET, TARGET_KEY));
        }
    }

    @Nested
    @DisplayName("Object Exists Tests")
    class ObjectExistsTests {

        @Test
        @DisplayName("Should return true when object exists")
        void objectExists_ObjectExists_ReturnsTrue() throws Exception {
            // Given
            StatObjectResponse mockResponse = mock(StatObjectResponse.class);
            when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(mockResponse);

            // When
            boolean result = minioService.objectExists(SOURCE_BUCKET, SOURCE_KEY);

            // Then
            assertTrue(result);
            verify(minioClient).statObject(any(StatObjectArgs.class));
        }

        @Test
        @DisplayName("Should return false when object does not exist (NoSuchKey)")
        void objectExists_ObjectNotExists_ReturnsFalse() throws Exception {
            // Given - ErrorResponseException with NoSuchKey error code
            ErrorResponse errorResponse = mock(ErrorResponse.class);
            when(errorResponse.code()).thenReturn("NoSuchKey");

            ErrorResponseException errorEx = mock(ErrorResponseException.class);
            when(errorEx.errorResponse()).thenReturn(errorResponse);

            when(minioClient.statObject(any(StatObjectArgs.class))).thenThrow(errorEx);

            // When
            boolean result = minioService.objectExists(SOURCE_BUCKET, "nonexistent-file.csv");

            // Then
            assertFalse(result);
        }

        @Test
        @DisplayName("Should throw RuntimeException on unexpected error")
        void objectExists_UnexpectedError_ThrowsRuntimeException() throws Exception {
            // Given - ErrorResponseException with a different error code
            ErrorResponse errorResponse = mock(ErrorResponse.class);
            when(errorResponse.code()).thenReturn("AccessDenied");

            ErrorResponseException errorEx = mock(ErrorResponseException.class);
            when(errorEx.errorResponse()).thenReturn(errorResponse);

            when(minioClient.statObject(any(StatObjectArgs.class))).thenThrow(errorEx);

            // When/Then
            assertThrows(RuntimeException.class, () ->
                    minioService.objectExists(SOURCE_BUCKET, SOURCE_KEY));
        }

        @Test
        @DisplayName("Should verify correct arguments passed to statObject")
        void objectExists_VerifiesCorrectArgs() throws Exception {
            // Given
            StatObjectResponse mockResponse = mock(StatObjectResponse.class);
            when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(mockResponse);

            // When
            minioService.objectExists(SOURCE_BUCKET, SOURCE_KEY);

            // Then
            ArgumentCaptor<StatObjectArgs> captor = ArgumentCaptor.forClass(StatObjectArgs.class);
            verify(minioClient).statObject(captor.capture());

            StatObjectArgs args = captor.getValue();
            assertEquals(SOURCE_BUCKET, args.bucket());
            assertEquals(SOURCE_KEY, args.object());
        }
    }
}
