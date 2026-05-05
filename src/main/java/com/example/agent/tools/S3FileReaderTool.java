package com.example.agent.tools;

import com.example.agent.config.AgentConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * Tool: s3_file_reader
 * Reads a text file from S3 and returns its contents.
 * Useful for reading documents, configs, or data files stored in S3.
 */
public class S3FileReaderTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(S3FileReaderTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_CHARS = 4000; // Limit content size sent to model

    private final S3Client s3Client;
    private final String defaultBucket;
    private final boolean configured;

    public S3FileReaderTool(AgentConfig config) {
        this.defaultBucket = config.getS3DefaultBucket();
        this.configured = config.isS3Configured();

        this.s3Client = S3Client.builder()
                .region(Region.of(config.getAwsRegion()))
                .build();
    }

    /**
     * Test-friendly constructor that accepts a pre-built S3Client.
     * Enables mocking without reflection in unit and property-based tests.
     *
     * @param s3Client      pre-built (or mocked) S3 client
     * @param defaultBucket default bucket name (may be empty)
     * @param configured    whether S3 is considered configured
     */
    S3FileReaderTool(S3Client s3Client, String defaultBucket, boolean configured) {
        this.s3Client = s3Client;
        this.defaultBucket = defaultBucket;
        this.configured = configured;
    }

    @Override
    public String getName() {
        return "s3_file_reader";
    }

    @Override
    public String getDescription() {
        return "Reads a text file from Amazon S3 and returns its contents. " +
               "Use this to access documents, data files, or any text content stored in S3. " +
               "Provide the S3 key (file path) and optionally the bucket name.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode key = properties.putObject("key");
        key.put("type", "string");
        key.put("description", "The S3 object key (file path), e.g. 'documents/report.txt'");

        ObjectNode bucket = properties.putObject("bucket");
        bucket.put("type", "string");
        bucket.put("description", "The S3 bucket name. Uses the default configured bucket if not provided.");

        schema.putArray("required").add("key");
        return schema;
    }

    @Override
    public String execute(ObjectNode input) {
        if (!configured && !input.has("bucket")) {
            return "Error: S3 is not configured. Set 's3.default.bucket' in config.properties or provide a 'bucket' parameter.";
        }

        if (!input.has("key")) {
            return "Error: 'key' parameter is required.";
        }

        String key = input.get("key").asText().trim();
        String bucket = input.has("bucket") ? input.get("bucket").asText().trim() : defaultBucket;

        // Basic path traversal protection
        if (key.contains("..")) {
            return "Error: Invalid key — path traversal not allowed.";
        }

        log.debug("Reading S3 object: s3://{}/{}", bucket, key);

        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            try (ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(response, StandardCharsets.UTF_8))) {

                String content = reader.lines().collect(Collectors.joining("\n"));

                if (content.length() > MAX_CHARS) {
                    content = content.substring(0, MAX_CHARS) +
                              "\n\n[Content truncated — file exceeds " + MAX_CHARS + " characters]";
                }

                return "Contents of s3://" + bucket + "/" + key + ":\n\n" + content;
            }

        } catch (NoSuchKeyException e) {
            return "Error: File not found — s3://" + bucket + "/" + key;
        } catch (Exception e) {
            log.error("Failed to read S3 object s3://{}/{}", bucket, key, e);
            return "Error reading file from S3: " + e.getMessage();
        }
    }
}
