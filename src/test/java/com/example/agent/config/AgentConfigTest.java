package com.example.agent.config;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AgentConfig.
 * Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5, 1.6
 */
class AgentConfigTest {

    // -------------------------------------------------------------------------
    // Helper: create an AgentConfig from a raw properties string.
    // Uses the package-private AgentConfig(InputStream) constructor so we can
    // control exactly which properties are present without touching the classpath.
    // -------------------------------------------------------------------------

    private static AgentConfig fromProperties(String propertiesContent) {
        byte[] bytes = propertiesContent.getBytes(StandardCharsets.ISO_8859_1);
        return new AgentConfig(new ByteArrayInputStream(bytes));
    }

    // -------------------------------------------------------------------------
    // 1.1 — Successful load from config.properties on the classpath
    // -------------------------------------------------------------------------

    @Test
    void successfulLoadFromClasspath() {
        // The real config.properties from src/main/resources is on the classpath.
        // AgentConfig() should load without throwing.
        assertDoesNotThrow((org.junit.jupiter.api.function.Executable) AgentConfig::new,
                "AgentConfig should load successfully when config.properties is on the classpath");
    }

    @Test
    void successfulLoadReturnsConfiguredRegion() {
        // The real config.properties sets aws.region=us-east-1
        AgentConfig config = new AgentConfig();
        assertEquals("us-east-1", config.getAwsRegion(),
                "Should read aws.region from the real config.properties");
    }

    // -------------------------------------------------------------------------
    // 1.2 — IllegalStateException when config.properties is absent
    // -------------------------------------------------------------------------

    @Test
    void throwsIllegalStateExceptionWhenConfigAbsent() {
        // Passing null simulates the file being absent from the classpath
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> new AgentConfig((InputStream) null),
                "Constructor should throw IllegalStateException when config stream is null"
        );
        assertTrue(ex.getMessage().contains("config.properties"),
                "Exception message should mention config.properties");
    }

    // -------------------------------------------------------------------------
    // 1.3 — Default values when properties are missing from the file
    // -------------------------------------------------------------------------

    @Test
    void defaultValuesReturnedWhenPropertiesMissing() {
        // Empty properties file — all values should fall back to defaults
        AgentConfig config = fromProperties("");

        assertEquals("us-east-1", config.getAwsRegion(),
                "Default aws.region should be us-east-1");
        assertEquals("anthropic.claude-3-haiku-20240307-v1:0", config.getModelId(),
                "Default bedrock.model.id should be anthropic.claude-3-haiku-20240307-v1:0");
        assertEquals(1024, config.getMaxTokens(),
                "Default bedrock.max.tokens should be 1024");
        assertEquals("", config.getKnowledgeBaseId(),
                "Default bedrock.knowledge.base.id should be empty string");
        assertEquals(5, config.getKnowledgeBaseResults(),
                "Default bedrock.knowledge.base.results should be 5");
        assertEquals("", config.getS3DefaultBucket(),
                "Default s3.default.bucket should be empty string");
        assertEquals("You are a helpful AI assistant.", config.getSystemPrompt(),
                "Default agent.system.prompt should be 'You are a helpful AI assistant.'");
    }

    // -------------------------------------------------------------------------
    // 1.4 — isKnowledgeBaseConfigured() returns false for blank and sentinel
    // -------------------------------------------------------------------------

    @Test
    void isKnowledgeBaseConfiguredReturnsFalseForBlankId() {
        AgentConfig config = fromProperties("bedrock.knowledge.base.id=");
        assertFalse(config.isKnowledgeBaseConfigured(),
                "isKnowledgeBaseConfigured() should return false when KB ID is blank");
    }

    @Test
    void isKnowledgeBaseConfiguredReturnsFalseForSentinelValue() {
        AgentConfig config = fromProperties(
                "bedrock.knowledge.base.id=YOUR_KNOWLEDGE_BASE_ID");
        assertFalse(config.isKnowledgeBaseConfigured(),
                "isKnowledgeBaseConfigured() should return false for sentinel value YOUR_KNOWLEDGE_BASE_ID");
    }

    @Test
    void isKnowledgeBaseConfiguredReturnsFalseWhenPropertyAbsent() {
        // Property not present at all — defaults to empty string
        AgentConfig config = fromProperties("");
        assertFalse(config.isKnowledgeBaseConfigured(),
                "isKnowledgeBaseConfigured() should return false when property is absent (defaults to empty)");
    }

    // -------------------------------------------------------------------------
    // 1.5 — isS3Configured() returns false for blank and sentinel
    // -------------------------------------------------------------------------

    @Test
    void isS3ConfiguredReturnsFalseForBlankBucket() {
        AgentConfig config = fromProperties("s3.default.bucket=");
        assertFalse(config.isS3Configured(),
                "isS3Configured() should return false when bucket is blank");
    }

    @Test
    void isS3ConfiguredReturnsFalseForSentinelValue() {
        AgentConfig config = fromProperties("s3.default.bucket=YOUR_S3_BUCKET_NAME");
        assertFalse(config.isS3Configured(),
                "isS3Configured() should return false for sentinel value YOUR_S3_BUCKET_NAME");
    }

    @Test
    void isS3ConfiguredReturnsFalseWhenPropertyAbsent() {
        // Property not present at all — defaults to empty string
        AgentConfig config = fromProperties("");
        assertFalse(config.isS3Configured(),
                "isS3Configured() should return false when property is absent (defaults to empty)");
    }

    // -------------------------------------------------------------------------
    // 1.6 — isKnowledgeBaseConfigured() and isS3Configured() return true for real values
    // -------------------------------------------------------------------------

    @Test
    void isKnowledgeBaseConfiguredReturnsTrueForRealId() {
        AgentConfig config = fromProperties("bedrock.knowledge.base.id=ABCDEF1234");
        assertTrue(config.isKnowledgeBaseConfigured(),
                "isKnowledgeBaseConfigured() should return true for a real KB ID");
    }

    @Test
    void isS3ConfiguredReturnsTrueForRealBucket() {
        AgentConfig config = fromProperties("s3.default.bucket=my-real-bucket");
        assertTrue(config.isS3Configured(),
                "isS3Configured() should return true for a real S3 bucket name");
    }

    @Test
    void bothConfiguredReturnsTrueWhenBothSet() {
        String props = "bedrock.knowledge.base.id=ABCDEF1234\n"
                + "s3.default.bucket=my-real-bucket\n";
        AgentConfig config = fromProperties(props);
        assertTrue(config.isKnowledgeBaseConfigured(),
                "isKnowledgeBaseConfigured() should return true");
        assertTrue(config.isS3Configured(),
                "isS3Configured() should return true");
    }
}
