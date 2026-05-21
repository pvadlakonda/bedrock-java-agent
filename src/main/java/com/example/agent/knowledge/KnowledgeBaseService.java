package com.example.agent.knowledge;

import com.example.agent.config.AgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseQuery;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalResult;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseVectorSearchConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalResultLocation;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveResponse;

import java.util.List;

/**
 * Retrieves relevant context from a Bedrock Knowledge Base (backed by S3 documents).
 *
 * The Knowledge Base must be created in the AWS Console:
 *   Bedrock > Knowledge Bases > Create Knowledge Base
 * Point it at your S3 bucket and run a sync. Then set the ID in config.properties.
 */
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private final BedrockAgentRuntimeClient client;
    private final String knowledgeBaseId;
    private final int maxResults;
    private final boolean configured;

    public KnowledgeBaseService(AgentConfig config) {
        this.knowledgeBaseId = config.getKnowledgeBaseId();
        this.maxResults = config.getKnowledgeBaseResults();
        this.configured = config.isKnowledgeBaseConfigured();

        this.client = BedrockAgentRuntimeClient.builder()
                .region(Region.of(config.getAwsRegion()))
                .build();
    }

    /**
     * Package-private constructor for testing: accepts a pre-built mock client.
     *
     * @param client         the mock {@link BedrockAgentRuntimeClient} to use
     * @param knowledgeBaseId the knowledge base ID to use in requests
     * @param maxResults     the maximum number of results to request
     */
    KnowledgeBaseService(BedrockAgentRuntimeClient client, String knowledgeBaseId, int maxResults) {
        this.client = client;
        this.knowledgeBaseId = knowledgeBaseId;
        this.maxResults = maxResults;
        this.configured = true;
    }

    /**
     * Retrieves relevant passages from the knowledge base for the given query.
     *
     * @param query the user's question or search query
     * @return formatted string of retrieved passages, or empty string if KB not configured
     */
    public String retrieve(String query) {
        if (!configured) {
            log.debug("Knowledge base not configured — skipping retrieval");
            return "";
        }

        log.debug("Retrieving from knowledge base '{}' for query: {}", knowledgeBaseId, query);

        try {
            RetrieveRequest request = RetrieveRequest.builder()
                    .knowledgeBaseId(knowledgeBaseId)
                    .retrievalQuery(KnowledgeBaseQuery.builder()
                            .text(query)
                            .build())
                    .retrievalConfiguration(KnowledgeBaseRetrievalConfiguration.builder()
                            .vectorSearchConfiguration(KnowledgeBaseVectorSearchConfiguration.builder()
                                    .numberOfResults(maxResults)
                                    .build())
                            .build())
                    .build();

            RetrieveResponse response = client.retrieve(request);
            List<KnowledgeBaseRetrievalResult> results = response.retrievalResults();

            if (results.isEmpty()) {
                log.debug("No results found in knowledge base for query: {}", query);
                return "";
            }

            log.debug("Retrieved {} results from knowledge base", results.size());

            // Format results into a context block for the model
            StringBuilder context = new StringBuilder();
            context.append("=== Relevant information from knowledge base ===\n\n");

            for (int i = 0; i < results.size(); i++) {
                KnowledgeBaseRetrievalResult result = results.get(i);
                String text = result.content().text();
                String source = extractSource(result);

                context.append("--- Source ").append(i + 1);
                if (!source.isEmpty()) {
                    context.append(" (").append(source).append(")");
                }
                context.append(" ---\n");
                context.append(text).append("\n\n");
            }

            context.append("=== End of knowledge base context ===\n");
            return context.toString();

        } catch (Exception e) {
            log.error("Failed to retrieve from knowledge base: {}", e.getMessage(), e);
            return ""; // Gracefully degrade — agent continues without KB context
        }
    }

    /** Extracts a human-readable source reference from a retrieval result. */
    private String extractSource(KnowledgeBaseRetrievalResult result) {
        try {
            RetrievalResultLocation location = result.location();
            if (location == null) return "";

            if (location.s3Location() != null && location.s3Location().uri() != null) {
                String uri = location.s3Location().uri();
                // Extract just the filename from the S3 URI
                return uri.substring(uri.lastIndexOf('/') + 1);
            }
        } catch (Exception e) {
            log.debug("Could not extract source from result: {}", e.getMessage());
        }
        return "";
    }
}
