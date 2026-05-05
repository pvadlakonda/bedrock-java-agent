package com.example.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.jqwik.api.*;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for S3FileReaderTool using jqwik.
 * Feature: bedrock-java-agent
 */
class S3FileReaderToolPropertyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Build an input ObjectNode with bucket and key set. */
    private ObjectNode inputWith(String bucket, String key) {
        ObjectNode input = MAPPER.createObjectNode();
        input.put("bucket", bucket);
        input.put("key", key);
        return input;
    }

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
    // Property 14: S3 File Reader Truncation
    // Validates: Requirements 8.2
    // ---------------------------------------------------------------------------

    // Feature: bedrock-java-agent, Property 14: Content truncation
    @Property
    void contentIsTruncatedAt4000Characters(
            @ForAll("longContent") String content) throws Exception {

        S3Client mockS3 = mock(S3Client.class);
        when(mockS3.getObject(any(GetObjectRequest.class)))
                .thenReturn(responseStreamFor(content));

        S3FileReaderTool tool = new S3FileReaderTool(mockS3, "test-bucket", true);
        ObjectNode input = inputWith("test-bucket", "some/file.txt");

        String result = tool.execute(input);

        // The content portion after the "Contents of s3://..." header must be truncated
        // The header is: "Contents of s3://test-bucket/some/file.txt:\n\n"
        String header = "Contents of s3://test-bucket/some/file.txt:\n\n";
        assertTrue(result.startsWith(header),
                "Result should start with the S3 URI header, but got: " + result);

        String body = result.substring(header.length());

        // The body must contain the truncation notice
        assertTrue(body.contains("[Content truncated"),
                "Result should contain a truncation notice, but got: " + result);

        // The content portion before the truncation notice must be exactly 4000 chars
        int truncationNoticeIndex = body.indexOf("\n\n[Content truncated");
        assertTrue(truncationNoticeIndex >= 0,
                "Truncation notice not found in expected position in: " + result);
        assertEquals(4000, truncationNoticeIndex,
                "Content before truncation notice should be exactly 4000 characters");
    }

    /**
     * Generates strings longer than 4000 characters.
     * Uses a repeated ASCII character pattern to keep generation fast.
     */
    @Provide
    Arbitrary<String> longContent() {
        // Generate a base string of printable ASCII chars, then repeat to exceed 4000
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(1)
                .ofMaxLength(50)
                .map(base -> base.repeat((4000 / base.length()) + 2));
    }

    // ---------------------------------------------------------------------------
    // Property 15: S3 File Reader Missing Key Error Contains URI
    // Validates: Requirements 8.5
    // ---------------------------------------------------------------------------

    // Feature: bedrock-java-agent, Property 15: Missing key error contains URI
    @Property
    void missingKeyErrorContainsS3Uri(
            @ForAll("safeBucketNames") String bucket,
            @ForAll("safeKeyStrings") String key) {

        S3Client mockS3 = mock(S3Client.class);
        when(mockS3.getObject(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("The specified key does not exist.").build());

        S3FileReaderTool tool = new S3FileReaderTool(mockS3, bucket, true);
        ObjectNode input = inputWith(bucket, key);

        String result = tool.execute(input);

        String expectedUri = "s3://" + bucket + "/" + key;
        assertTrue(result.contains(expectedUri),
                "Error response should contain '" + expectedUri + "' but got: " + result);
    }

    /**
     * Generates bucket name strings: alphanumeric + hyphens, non-empty.
     */
    @Provide
    Arbitrary<String> safeBucketNames() {
        return Arbitraries.strings()
                .withChars("abcdefghijklmnopqrstuvwxyz0123456789-")
                .ofMinLength(1)
                .ofMaxLength(30);
    }

    /**
     * Generates key strings that do not contain ".." (path traversal).
     * Uses alphanumeric chars, slashes, hyphens, underscores, and dots
     * but never two consecutive dots.
     */
    @Provide
    Arbitrary<String> safeKeyStrings() {
        return Arbitraries.strings()
                .withChars("abcdefghijklmnopqrstuvwxyz0123456789/_-")
                .ofMinLength(1)
                .ofMaxLength(50);
    }

    // ---------------------------------------------------------------------------
    // Property 16: S3 File Reader General Error Contains Exception Message
    // Validates: Requirements 8.7
    // ---------------------------------------------------------------------------

    // Feature: bedrock-java-agent, Property 16: General error contains exception message
    @Property
    void generalErrorContainsExceptionMessage(
            @ForAll("nonEmptyStrings") String exceptionMessage) {

        S3Client mockS3 = mock(S3Client.class);
        when(mockS3.getObject(any(GetObjectRequest.class)))
                .thenThrow(new RuntimeException(exceptionMessage));

        S3FileReaderTool tool = new S3FileReaderTool(mockS3, "test-bucket", true);
        ObjectNode input = inputWith("test-bucket", "some/file.txt");

        String result = tool.execute(input);

        assertTrue(result.contains(exceptionMessage),
                "Error response should contain the exception message '"
                        + exceptionMessage + "' but got: " + result);
    }

    /**
     * Generates non-empty strings for use as exception messages.
     */
    @Provide
    Arbitrary<String> nonEmptyStrings() {
        return Arbitraries.strings()
                .withCharRange(' ', '~')   // printable ASCII
                .ofMinLength(1)
                .ofMaxLength(100);
    }
}
