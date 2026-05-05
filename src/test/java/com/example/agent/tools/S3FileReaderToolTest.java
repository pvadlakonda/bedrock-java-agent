package com.example.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for S3FileReaderTool.
 * Feature: bedrock-java-agent
 * Validates: Requirements 8.3, 8.4, 8.6, 8.8
 */
class S3FileReaderToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Create a ResponseInputStream that wraps the given content string,
     * as returned by a mocked S3Client.getObject() call.
     */
    private ResponseInputStream<GetObjectResponse> responseStreamFor(String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        AbortableInputStream abortable = AbortableInputStream.create(new ByteArrayInputStream(bytes));
        return new ResponseInputStream<>(GetObjectResponse.builder().build(), abortable);
    }

    // ---------------------------------------------------------------------------
    // Requirement 8.3: Path traversal (..) in key returns error without S3 call
    // ---------------------------------------------------------------------------

    @Test
    void pathTraversalInKeyReturnsErrorWithoutS3Call() {
        S3Client mockS3 = mock(S3Client.class);
        S3FileReaderTool tool = new S3FileReaderTool(mockS3, "my-bucket", true);

        ObjectNode input = MAPPER.createObjectNode();
        input.put("key", "documents/../secret.txt");

        String result = tool.execute(input);

        assertTrue(result.startsWith("Error:"),
                "Expected an error string for path traversal, but got: " + result);
        assertTrue(result.contains("path traversal"),
                "Error message should mention path traversal, but got: " + result);

        // S3 must NOT have been called
        verify(mockS3, never()).getObject(any(GetObjectRequest.class));
    }

    // ---------------------------------------------------------------------------
    // Requirement 8.4: Missing 'key' parameter returns error string
    // ---------------------------------------------------------------------------

    @Test
    void missingKeyParameterReturnsError() {
        S3Client mockS3 = mock(S3Client.class);
        S3FileReaderTool tool = new S3FileReaderTool(mockS3, "my-bucket", true);

        ObjectNode input = MAPPER.createObjectNode();
        // 'key' is intentionally absent

        String result = tool.execute(input);

        assertTrue(result.startsWith("Error:"),
                "Expected an error string when 'key' is missing, but got: " + result);
        assertTrue(result.contains("key"),
                "Error message should mention the 'key' parameter, but got: " + result);
    }

    // ---------------------------------------------------------------------------
    // Requirement 8.6: Unconfigured S3 with no 'bucket' parameter returns error
    // ---------------------------------------------------------------------------

    @Test
    void unconfiguredS3WithoutBucketParameterReturnsError() {
        S3Client mockS3 = mock(S3Client.class);
        // configured=false simulates S3 not being set up
        S3FileReaderTool tool = new S3FileReaderTool(mockS3, "", false);

        ObjectNode input = MAPPER.createObjectNode();
        input.put("key", "some/file.txt");
        // 'bucket' is intentionally absent

        String result = tool.execute(input);

        assertTrue(result.startsWith("Error:"),
                "Expected an error string when S3 is not configured and no bucket is provided, but got: " + result);
        assertTrue(result.toLowerCase().contains("s3") || result.toLowerCase().contains("bucket"),
                "Error message should mention S3 or bucket configuration, but got: " + result);
    }

    // ---------------------------------------------------------------------------
    // Requirement 8.8: Explicit 'bucket' parameter overrides the configured default
    // ---------------------------------------------------------------------------

    @Test
    void explicitBucketParameterOverridesDefault() throws Exception {
        S3Client mockS3 = mock(S3Client.class);
        String defaultBucket = "default-bucket";
        String explicitBucket = "explicit-bucket";

        when(mockS3.getObject(any(GetObjectRequest.class)))
                .thenReturn(responseStreamFor("hello world"));

        S3FileReaderTool tool = new S3FileReaderTool(mockS3, defaultBucket, true);

        ObjectNode input = MAPPER.createObjectNode();
        input.put("bucket", explicitBucket);
        input.put("key", "data/file.txt");

        String result = tool.execute(input);

        // Result should reference the explicit bucket URI, not the default
        assertTrue(result.contains("s3://" + explicitBucket + "/"),
                "Result should contain the explicit bucket URI 's3://" + explicitBucket + "/' but got: " + result);
        assertFalse(result.contains("s3://" + defaultBucket + "/"),
                "Result should NOT reference the default bucket, but got: " + result);
    }
}
