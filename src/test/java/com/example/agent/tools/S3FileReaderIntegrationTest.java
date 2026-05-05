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
 * Integration test for {@link S3FileReaderTool}.
 *
 * Mocks {@link S3Client} to return known file content and verifies that the
 * tool response is prefixed with the full {@code s3://bucket/key} URI.
 *
 * Validates: Requirements 8.1
 */
class S3FileReaderIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Wraps a plain string as a {@link ResponseInputStream} that a mocked
     * {@link S3Client#getObject(GetObjectRequest)} can return.
     */
    private ResponseInputStream<GetObjectResponse> responseStreamFor(String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        AbortableInputStream abortable = AbortableInputStream.create(new ByteArrayInputStream(bytes));
        return new ResponseInputStream<>(GetObjectResponse.builder().build(), abortable);
    }

    // -------------------------------------------------------------------------
    // Requirement 8.1: successful read returns content prefixed with S3 URI
    // -------------------------------------------------------------------------

    /**
     * When the S3 client returns a known file content, the tool must return a
     * string that starts with {@code "Contents of s3://bucket/key:\n\n"} and
     * contains the file content.
     *
     * Validates: Requirements 8.1
     */
    @Test
    void successfulReadReturnsPrefixedWithS3Uri() {
        String bucket = "my-test-bucket";
        String key = "documents/hello.txt";
        String fileContent = "Hello, world! This is the file content.";

        S3Client mockS3 = mock(S3Client.class);
        when(mockS3.getObject(any(GetObjectRequest.class)))
                .thenReturn(responseStreamFor(fileContent));

        S3FileReaderTool tool = new S3FileReaderTool(mockS3, bucket, true);

        ObjectNode input = MAPPER.createObjectNode();
        input.put("key", key);

        String result = tool.execute(input);

        // The result must start with the full S3 URI prefix
        String expectedPrefix = "Contents of s3://" + bucket + "/" + key + ":\n\n";
        assertTrue(result.startsWith(expectedPrefix),
                "Result must start with '" + expectedPrefix + "' but was:\n" + result);

        // The result must also contain the actual file content
        assertTrue(result.contains(fileContent),
                "Result must contain the file content '" + fileContent + "' but was:\n" + result);
    }

    // -------------------------------------------------------------------------
    // Additional: explicit bucket parameter uses that bucket in the URI prefix
    // -------------------------------------------------------------------------

    /**
     * When an explicit {@code bucket} parameter is provided, the URI prefix
     * must use that bucket name.
     *
     * Validates: Requirements 8.1, 8.8
     */
    @Test
    void explicitBucketParameterAppearsInUriPrefix() {
        String defaultBucket = "default-bucket";
        String explicitBucket = "override-bucket";
        String key = "data/report.csv";
        String fileContent = "col1,col2\nval1,val2";

        S3Client mockS3 = mock(S3Client.class);
        when(mockS3.getObject(any(GetObjectRequest.class)))
                .thenReturn(responseStreamFor(fileContent));

        S3FileReaderTool tool = new S3FileReaderTool(mockS3, defaultBucket, true);

        ObjectNode input = MAPPER.createObjectNode();
        input.put("bucket", explicitBucket);
        input.put("key", key);

        String result = tool.execute(input);

        String expectedPrefix = "Contents of s3://" + explicitBucket + "/" + key + ":\n\n";
        assertTrue(result.startsWith(expectedPrefix),
                "Result must start with '" + expectedPrefix + "' but was:\n" + result);
    }

    // -------------------------------------------------------------------------
    // Additional: S3Client.getObject is called with the correct bucket and key
    // -------------------------------------------------------------------------

    /**
     * Verifies that the tool passes the correct bucket and key to the S3 client.
     *
     * Validates: Requirements 8.1
     */
    @Test
    void s3ClientIsCalledWithCorrectBucketAndKey() {
        String bucket = "verification-bucket";
        String key = "path/to/file.txt";

        S3Client mockS3 = mock(S3Client.class);
        when(mockS3.getObject(any(GetObjectRequest.class)))
                .thenReturn(responseStreamFor("content"));

        S3FileReaderTool tool = new S3FileReaderTool(mockS3, bucket, true);

        ObjectNode input = MAPPER.createObjectNode();
        input.put("key", key);

        tool.execute(input);

        // Capture the actual GetObjectRequest sent to S3
        var requestCaptor = org.mockito.ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(mockS3, times(1)).getObject(requestCaptor.capture());

        GetObjectRequest capturedRequest = requestCaptor.getValue();
        assertEquals(bucket, capturedRequest.bucket(),
                "S3 request must use the configured bucket");
        assertEquals(key, capturedRequest.key(),
                "S3 request must use the provided key");
    }
}
