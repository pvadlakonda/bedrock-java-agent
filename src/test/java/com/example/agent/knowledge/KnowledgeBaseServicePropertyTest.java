package com.example.agent.knowledge;

import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;
import org.mockito.Mockito;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalResult;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalResultContent;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalResultLocation;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalResultS3Location;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Property-based tests for KnowledgeBaseService using jqwik.
 * Feature: bedrock-java-agent
 */
class KnowledgeBaseServicePropertyTest {

    // ---------------------------------------------------------------------------
    // Property 4: Knowledge Base Context Formatting
    // Validates: Requirements 4.3
    // ---------------------------------------------------------------------------

    // Feature: bedrock-java-agent, Property 4: KB context formatting
    @Property
    void kbContextFormattingContainsFilenames(
            @ForAll("retrievalResultLists") List<KnowledgeBaseRetrievalResult> results) {

        // Arrange: mock the BedrockAgentRuntimeClient to return the generated results
        BedrockAgentRuntimeClient mockClient = Mockito.mock(BedrockAgentRuntimeClient.class);
        RetrieveResponse mockResponse = RetrieveResponse.builder()
                .retrievalResults(results)
                .build();
        when(mockClient.retrieve(any(RetrieveRequest.class))).thenReturn(mockResponse);

        // Inject the mock client via the package-private test constructor
        KnowledgeBaseService service = new KnowledgeBaseService(mockClient, "test-kb-id", 10);

        // Act
        String context = service.retrieve("test query");

        // Assert: the formatted context must contain the filename from each S3 URI
        for (KnowledgeBaseRetrievalResult result : results) {
            String uri = result.location().s3Location().uri();
            String expectedFilename = uri.substring(uri.lastIndexOf('/') + 1);
            assertTrue(
                    context.contains(expectedFilename),
                    "Context should contain filename '" + expectedFilename + "' extracted from URI '" + uri + "'.\n"
                            + "Actual context:\n" + context
            );
        }
    }

    // ---------------------------------------------------------------------------
    // Arbitraries / Generators
    // ---------------------------------------------------------------------------

    /**
     * Generates non-empty lists of KnowledgeBaseRetrievalResult objects,
     * each with a valid S3 URI of the form s3://bucket/path/filename.txt.
     */
    @Provide
    Arbitrary<List<KnowledgeBaseRetrievalResult>> retrievalResultLists() {
        return retrievalResultArbitrary().list().ofMinSize(1).ofMaxSize(5);
    }

    /**
     * Generates a single KnowledgeBaseRetrievalResult with an S3 URI
     * whose filename component is a non-empty alpha string with a .txt extension.
     */
    private Arbitrary<KnowledgeBaseRetrievalResult> retrievalResultArbitrary() {
        Arbitrary<String> bucketNames = Arbitraries.strings()
                .alpha()
                .ofMinLength(3)
                .ofMaxLength(10)
                .map(String::toLowerCase);

        Arbitrary<String> pathSegments = Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(10)
                .map(String::toLowerCase);

        Arbitrary<String> filenames = Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(15)
                .map(s -> s.toLowerCase() + ".txt");

        Arbitrary<String> contentTexts = Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(50);

        return Combinators.combine(bucketNames, pathSegments, filenames, contentTexts)
                .as((bucket, path, filename, text) -> {
                    String uri = "s3://" + bucket + "/" + path + "/" + filename;

                    RetrievalResultS3Location s3Location = RetrievalResultS3Location.builder()
                            .uri(uri)
                            .build();

                    RetrievalResultLocation location = RetrievalResultLocation.builder()
                            .s3Location(s3Location)
                            .build();

                    RetrievalResultContent content = RetrievalResultContent.builder()
                            .text(text)
                            .build();

                    return KnowledgeBaseRetrievalResult.builder()
                            .content(content)
                            .location(location)
                            .build();
                });
    }
}
