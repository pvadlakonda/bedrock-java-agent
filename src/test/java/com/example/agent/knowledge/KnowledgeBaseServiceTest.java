package com.example.agent.knowledge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveResponse;

import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link KnowledgeBaseService}.
 *
 * Covers Requirements 4.2, 4.4, 4.5.
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceTest {

    @Mock
    private BedrockAgentRuntimeClient mockClient;

    // -------------------------------------------------------------------------
    // Requirement 4.2 — KB not configured: skip retrieval, return empty string
    // -------------------------------------------------------------------------

    /**
     * When the knowledge base is not configured (configured=false), retrieve()
     * must return an empty string without making any API call.
     *
     * The package-private test constructor always sets configured=true, so we
     * use reflection to flip the flag to false, simulating the state produced
     * by the public AgentConfig constructor when no KB ID is set.
     *
     * Validates: Requirements 4.2
     */
    @Test
    void retrieve_returnsEmptyString_whenKnowledgeBaseNotConfigured() throws Exception {
        KnowledgeBaseService service = new KnowledgeBaseService(mockClient, "test-kb", 5);

        // Flip the private `configured` field to false to simulate an unconfigured KB
        Field configuredField = KnowledgeBaseService.class.getDeclaredField("configured");
        configuredField.setAccessible(true);
        configuredField.setBoolean(service, false);

        String result = service.retrieve("any query");

        assertEquals("", result, "retrieve() should return empty string when KB is not configured");
        verify(mockClient, never()).retrieve(any(RetrieveRequest.class));
    }

    // -------------------------------------------------------------------------
    // Requirement 4.4 — KB returns zero results: return empty string
    // -------------------------------------------------------------------------

    /**
     * When the knowledge base API returns an empty result list, retrieve()
     * must return an empty string.
     *
     * Validates: Requirements 4.4
     */
    @Test
    void retrieve_returnsEmptyString_whenKnowledgeBaseReturnsZeroResults() {
        RetrieveResponse emptyResponse = RetrieveResponse.builder()
                .retrievalResults(Collections.emptyList())
                .build();
        when(mockClient.retrieve(any(RetrieveRequest.class))).thenReturn(emptyResponse);

        KnowledgeBaseService service = new KnowledgeBaseService(mockClient, "test-kb-id", 5);

        String result = service.retrieve("what is the capital of France?");

        assertEquals("", result, "retrieve() should return empty string when KB returns no results");
    }

    // -------------------------------------------------------------------------
    // Requirement 4.5 — KB API throws exception: graceful degradation
    // -------------------------------------------------------------------------

    /**
     * When the knowledge base API call throws an exception, retrieve() must
     * catch it and return an empty string (graceful degradation), allowing
     * the agent to continue without KB context.
     *
     * Validates: Requirements 4.5
     */
    @Test
    void retrieve_returnsEmptyString_whenKnowledgeBaseApiThrowsException() {
        when(mockClient.retrieve(any(RetrieveRequest.class)))
                .thenThrow(new RuntimeException("Simulated KB API failure"));

        KnowledgeBaseService service = new KnowledgeBaseService(mockClient, "test-kb-id", 5);

        String result = service.retrieve("some query");

        assertEquals("", result,
                "retrieve() should return empty string and not propagate exceptions from the KB API");
    }
}
