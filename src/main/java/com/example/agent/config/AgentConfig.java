package com.example.agent.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Loads agent configuration from config.properties on the classpath.
 */
public class AgentConfig {

    private static final String CONFIG_FILE = "config.properties";
    private final Properties props;

    public AgentConfig() {
        props = new Properties();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (is == null) {
                throw new IllegalStateException("config.properties not found on classpath");
            }
            props.load(is);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load config.properties", e);
        }
    }

    /**
     * Package-private constructor for testing: loads configuration from the
     * provided InputStream instead of the classpath.
     *
     * @param configStream the stream to load properties from, or {@code null}
     *                     to simulate a missing config file
     * @throws IllegalStateException if configStream is null or cannot be read
     */
    AgentConfig(InputStream configStream) {
        props = new Properties();
        if (configStream == null) {
            throw new IllegalStateException("config.properties not found on classpath");
        }
        try (InputStream is = configStream) {
            props.load(is);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load config.properties", e);
        }
    }

    public String getAwsRegion() {
        return props.getProperty("aws.region", "us-east-1");
    }

    public String getModelId() {
        return props.getProperty("bedrock.model.id", "amazon.nova-pro-v1:0");
    }

    public int getMaxTokens() {
        return Integer.parseInt(props.getProperty("bedrock.max.tokens", "1024"));
    }

    public String getKnowledgeBaseId() {
        return props.getProperty("bedrock.knowledge.base.id", "");
    }

    public int getKnowledgeBaseResults() {
        return Integer.parseInt(props.getProperty("bedrock.knowledge.base.results", "5"));
    }

    public String getS3DefaultBucket() {
        return props.getProperty("s3.default.bucket", "");
    }

    public String getSystemPrompt() {
        return props.getProperty("agent.system.prompt", "You are a helpful AI assistant.");
    }

    /** Returns true if a real Knowledge Base ID has been configured. */
    public boolean isKnowledgeBaseConfigured() {
        String kbId = getKnowledgeBaseId();
        return kbId != null && !kbId.isBlank() && !kbId.equals("YOUR_KNOWLEDGE_BASE_ID");
    }

    /** Returns true if a real S3 bucket has been configured. */
    public boolean isS3Configured() {
        String bucket = getS3DefaultBucket();
        return bucket != null && !bucket.isBlank() && !bucket.equals("YOUR_S3_BUCKET_NAME");
    }
}
