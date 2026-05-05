package com.example.agent.knowledge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalResult;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalResultContent;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalResultLocation;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalResultS3Location;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveResponse;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration test for {@link KnowledgeBaseService}.
 *
 * Mocks {@link BedrockAgentRuntimeClient} to return N results and verifies
 * that the formatted context string contains all N source filenames extracted
 * from the S3 URIs.
 *
 * Validates: Requirements 4.1
 */
class KnowledgeBaseServiceIntegrationTest {

    // -------------------------------------------------------------------------
    // Helper: build a list of N KnowledgeBaseRetrievalResult objects
    // -------------------------------------------------------------------------

    /**
     * Builds a list of {@code n} retrieval results with S3 URIs of the form
     * {@code s3://bucket/file-N.txt} (1-indexed).
     */
    private List<KnowledgeBaseRetrievalResult> buildResults(int n) {
        List<KnowledgeBaseRetrievalResult> results = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            String uri = "s3://bucket/file-" + i + ".txt";

            RetrievalResultS3Location s3Location = RetrievalResultS3Location.builder()
                    .uri(uri)
                    .build();

            RetrievalResultLocation location = RetrievalResultLocation.builder()
                    .s3Location(s3Location)
                    .build();

            RetrievalResultContent content = RetrievalResultContent.builder()
                    .text("Content of file " + i)
                    .build();

            results.add(KnowledgeBaseRetrievalResult.builder()
                    .content(content)
                    .location(location)
                    .build());
        }
        return results;
    }

    // -------------------------------------------------------------------------
    // Requirement 4.1: KB returns top-N results; formatted context contains
    //                  all N source filenames
    // -------------------------------------------------------------------------

    /**
     * When the mocked client returns N results, the formatted context string
     * must contain each filename {@code file-N.txt} extracted from the S3 URI.
     *
     * Validates: Requirements 4.1
     */
    @ParameterizedTest(name = "N={0} results")
    @ValueSource(ints = {1, 3, 5})
    void formattedContextContainsAllSourceFilenames(int n) {
        List<KnowledgeBaseRetrievalResult> results = buildResults(n);

        BedrockAgentRuntimeClient mockClient = mock(BedrockAgentRuntimeClient.class);
        when(mockClient.retrieve(any(RetrieveRequest.class)))
                .thenReturn(RetrieveResponse.builder()
                        .retrievalResults(results)
                        .build());

        KnowledgeBaseService service = new KnowledgeBaseService(mockClient, "test-kb-id", n);

        String context = service.retrieve("test query");

        // The context must be non-empty when results are returned
        assertFalse(context.isBlank(),
                "Context must not be blank when " + n + " results are returned");

        // Every filename must appear in the formatted context
        for (int i = 1; i <= n; i++) {
            String expectedFilename = "file-" + i + ".txt";
            assertTrue(context.contains(expectedFilename),
                    "Context must contain filename '" + expectedFilename + "' but was:\n" + context);
        }
    }

    /**
     * Explicit test for N=5 (the default configured result count) to verify
     * the retrieve API is called and all 5 filenames appear in the context.
     *
     * Validates: Requirements 4.1
     */
    @Test
    void formattedContextContainsFiveSourceFilenames() {
        int n = 5;
        List<KnowledgeBaseRetrievalResult> results = buildResults(n);

        BedrockAgentRuntimeClient mockClient = mock(BedrockAgentRuntimeClient.class);
        when(mockClient.retrieve(any(RetrieveRequest.class)))
                .thenReturn(RetrieveResponse.builder()
                        .retrievalResults(results)
                        .build());

        KnowledgeBaseService service = new KnowledgeBaseService(mockClient, "test-kb-id", n);

        String context = service.retrieve("what documents do we have?");

        // Verify the client was called exactly once
        verify(mockClient, times(1)).retrieve(any(RetrieveRequest.class));

        // All 5 filenames must be present
        for (int i = 1; i <= n; i++) {
            String expectedFilename = "file-" + i + ".txt";
            assertTrue(context.contains(expectedFilename),
                    "Context must contain '" + expectedFilename + "' but was:\n" + context);
        }
    }

    /**
     * Verifies that the retrieve request is sent with the correct knowledge base ID.
     *
     * Validates: Requirements 4.1
     */
    @Test
    void retrieveRequestUsesConfiguredKnowledgeBaseId() {
        String knowledgeBaseId = "kb-integration-test-id";
        List<KnowledgeBaseRetrievalResult> results = buildResults(2);

        BedrockAgentRuntimeClient mockClient = mock(BedrockAgentRuntimeClient.class);
        var requestCaptor = org.mockito.ArgumentCaptor.forClass(RetrieveRequest.class);
        when(mockClient.retrieve(requestCaptor.capture()))
                .thenReturn(RetrieveResponse.builder()
                        .retrievalResults(results)
                        .build());

        KnowledgeBaseService service = new KnowledgeBaseService(mockClient, knowledgeBaseId, 2);

        service.retrieve("some query");

        RetrieveRequest capturedRequest = requestCaptor.getValue();
        assertEquals(knowledgeBaseId, capturedRequest.knowledgeBaseId(),
                "RetrieveRequest must use the configured knowledge base ID");
    }
}
