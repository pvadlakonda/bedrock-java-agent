package com.example.agent;

import com.example.agent.config.AgentConfig;
import com.example.agent.knowledge.KnowledgeBaseService;
import com.example.agent.tools.ToolRegistry;
import net.jqwik.api.*;
import net.jqwik.api.constraints.StringLength;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for BedrockAgent using jqwik.
 * Feature: bedrock-java-agent
 */
class BedrockAgentPropertyTest {

    // ---------------------------------------------------------------------------
    // Helpers: build mocked dependencies
    // ---------------------------------------------------------------------------

    private AgentConfig mockConfig() {
        AgentConfig config = mock(AgentConfig.class);
        when(config.getModelId()).thenReturn("anthropic.claude-3-haiku-20240307-v1:0");
        when(config.getSystemPrompt()).thenReturn("You are a helpful AI assistant.");
        when(config.getMaxTokens()).thenReturn(1024);
        when(config.getAwsRegion()).thenReturn("us-east-1");
        return config;
    }

    private ToolRegistry emptyToolRegistry() {
        ToolRegistry registry = mock(ToolRegistry.class);
        when(registry.getAll()).thenReturn(Collections.emptyList());
        return registry;
    }

    /** Builds a ConverseResponse that signals END_TURN with a single text block. */
    private ConverseResponse endTurnResponse(String text) {
        ContentBlock textBlock = ContentBlock.fromText(text);
        Message assistantMessage = Message.builder()
                .role(ConversationRole.ASSISTANT)
                .content(textBlock)
                .build();
        ConverseOutput output = ConverseOutput.builder()
                .message(assistantMessage)
                .build();
        return ConverseResponse.builder()
                .output(output)
                .stopReason(StopReason.END_TURN)
                .build();
    }

    /** Builds a ConverseResponse that signals END_TURN with the given content blocks. */
    private ConverseResponse endTurnResponseWithBlocks(List<ContentBlock> blocks) {
        Message assistantMessage = Message.builder()
                .role(ConversationRole.ASSISTANT)
                .content(blocks)
                .build();
        ConverseOutput output = ConverseOutput.builder()
                .message(assistantMessage)
                .build();
        return ConverseResponse.builder()
                .output(output)
                .stopReason(StopReason.END_TURN)
                .build();
    }

    /** Builds a ConverseResponse that signals TOOL_USE with the given tool use blocks. */
    private ConverseResponse toolUseResponse(List<ToolUseBlock> toolUseBlocks) {
        List<ContentBlock> blocks = new ArrayList<>();
        for (ToolUseBlock tub : toolUseBlocks) {
            blocks.add(ContentBlock.fromToolUse(tub));
        }
        Message assistantMessage = Message.builder()
                .role(ConversationRole.ASSISTANT)
                .content(blocks)
                .build();
        ConverseOutput output = ConverseOutput.builder()
                .message(assistantMessage)
                .build();
        return ConverseResponse.builder()
                .output(output)
                .stopReason(StopReason.TOOL_USE)
                .build();
    }

    // ---------------------------------------------------------------------------
    // Property 5: KB Context Prepended to User Message
    // Validates: Requirements 5.1
    // ---------------------------------------------------------------------------

    // Feature: bedrock-java-agent, Property 5: KB context prepended to history
    @Property(tries = 100)
    void kbContextPrependedToUserMessage(
            @ForAll @StringLength(min = 1, max = 100) String userMessage,
            @ForAll @StringLength(min = 1, max = 200) String kbContext) {

        // Arrange
        BedrockRuntimeClient mockBedrock = mock(BedrockRuntimeClient.class);
        KnowledgeBaseService mockKb = mock(KnowledgeBaseService.class);
        AgentConfig config = mockConfig();
        ToolRegistry registry = emptyToolRegistry();

        when(mockKb.retrieve(any())).thenReturn(kbContext);

        ArgumentCaptor<ConverseRequest> requestCaptor = ArgumentCaptor.forClass(ConverseRequest.class);
        when(mockBedrock.converse(requestCaptor.capture())).thenReturn(endTurnResponse("ok"));

        BedrockAgent agent = new BedrockAgent(mockBedrock, config, registry, mockKb);

        // Act
        agent.chat(userMessage);

        // Assert: the first user message in the captured request must contain both
        // the KB context and the original user message
        ConverseRequest capturedRequest = requestCaptor.getValue();
        assertFalse(capturedRequest.messages().isEmpty(),
                "Request must contain at least one message");

        Message firstMessage = capturedRequest.messages().get(0);
        assertEquals(ConversationRole.USER, firstMessage.role(),
                "First message must be from the user");
        assertFalse(firstMessage.content().isEmpty(),
                "First user message must have content");

        String firstMessageText = firstMessage.content().get(0).text();
        assertNotNull(firstMessageText, "First content block must be text");
        assertTrue(firstMessageText.contains(kbContext),
                "First user message must contain the KB context.\n"
                        + "Expected to find: " + kbContext + "\nActual: " + firstMessageText);
        assertTrue(firstMessageText.contains(userMessage),
                "First user message must contain the original user message.\n"
                        + "Expected to find: " + userMessage + "\nActual: " + firstMessageText);
    }

    // ---------------------------------------------------------------------------
    // Property 6: Tool Use Results Added to History
    // Validates: Requirements 5.3
    // ---------------------------------------------------------------------------

    // Feature: bedrock-java-agent, Property 6: Tool results added to history
    @Property(tries = 100)
    void toolUseResultsAddedToHistory(
            @ForAll("toolUseBlockLists") List<ToolUseBlock> toolUseBlocks) {

        // Arrange
        BedrockRuntimeClient mockBedrock = mock(BedrockRuntimeClient.class);
        KnowledgeBaseService mockKb = mock(KnowledgeBaseService.class);
        AgentConfig config = mockConfig();
        ToolRegistry registry = emptyToolRegistry();

        when(mockKb.retrieve(any())).thenReturn("");

        // First call returns TOOL_USE, second call returns END_TURN
        when(mockBedrock.converse(any(ConverseRequest.class)))
                .thenReturn(toolUseResponse(toolUseBlocks))
                .thenReturn(endTurnResponse("final answer"));

        BedrockAgent agent = new BedrockAgent(mockBedrock, config, registry, mockKb);

        // Act
        agent.chat("test message");

        // Assert: history must be at least 3 messages:
        //   [0] user message
        //   [1] assistant message with tool use blocks
        //   [2] user message with tool results (one per tool use block)
        int historyLength = agent.getConversationLength();
        assertTrue(historyLength >= 3,
                "History must contain at least 3 messages after a tool-use turn, but was: " + historyLength);

        // Verify the mock was called twice (once for tool use, once for final answer)
        verify(mockBedrock, times(2)).converse(any(ConverseRequest.class));
    }

    // ---------------------------------------------------------------------------
    // Property 7: Text Extraction from Assistant Messages
    // Validates: Requirements 5.6
    // ---------------------------------------------------------------------------

    // Feature: bedrock-java-agent, Property 7: Text extraction from messages
    @Property(tries = 100)
    void textExtractionFromAssistantMessages(
            @ForAll("textBlockLists") List<String> textParts) {

        // Arrange
        BedrockRuntimeClient mockBedrock = mock(BedrockRuntimeClient.class);
        KnowledgeBaseService mockKb = mock(KnowledgeBaseService.class);
        AgentConfig config = mockConfig();
        ToolRegistry registry = emptyToolRegistry();

        when(mockKb.retrieve(any())).thenReturn("");

        // Build content blocks from the generated text parts
        List<ContentBlock> blocks = new ArrayList<>();
        for (String part : textParts) {
            blocks.add(ContentBlock.fromText(part));
        }

        when(mockBedrock.converse(any(ConverseRequest.class)))
                .thenReturn(endTurnResponseWithBlocks(blocks));

        BedrockAgent agent = new BedrockAgent(mockBedrock, config, registry, mockKb);

        // Act
        String result = agent.chat("test message");

        // Assert: result must equal the concatenation of all text blocks (trimmed)
        StringBuilder expected = new StringBuilder();
        for (String part : textParts) {
            expected.append(part);
        }
        assertEquals(expected.toString().trim(), result,
                "Returned text must equal the concatenation of all text blocks (trimmed).\n"
                        + "Text parts: " + textParts + "\nExpected: '" + expected.toString().trim()
                        + "'\nActual: '" + result + "'");
    }

    // ---------------------------------------------------------------------------
    // Property 8: Conversation History Growth
    // Validates: Requirements 9.1, 9.4
    // ---------------------------------------------------------------------------

    // Feature: bedrock-java-agent, Property 8: Conversation history growth
    @Property(tries = 100)
    void conversationHistoryGrowth(
            @ForAll("turnCounts") int n) {

        // Arrange
        BedrockRuntimeClient mockBedrock = mock(BedrockRuntimeClient.class);
        KnowledgeBaseService mockKb = mock(KnowledgeBaseService.class);
        AgentConfig config = mockConfig();
        ToolRegistry registry = emptyToolRegistry();

        when(mockKb.retrieve(any())).thenReturn("");
        when(mockBedrock.converse(any(ConverseRequest.class)))
                .thenReturn(endTurnResponse("response"));

        BedrockAgent agent = new BedrockAgent(mockBedrock, config, registry, mockKb);

        // Act: perform N chat turns
        for (int i = 0; i < n; i++) {
            agent.chat("message " + i);
        }

        // Assert: history length must be at least 2*N
        int historyLength = agent.getConversationLength();
        assertTrue(historyLength >= 2 * n,
                "After " + n + " turns, history length must be at least " + (2 * n)
                        + " but was: " + historyLength);
    }

    // ---------------------------------------------------------------------------
    // Arbitraries / Generators
    // ---------------------------------------------------------------------------

    /**
     * Generates lists of 1–3 ToolUseBlock objects with unique IDs and names.
     */
    @Provide
    Arbitrary<List<ToolUseBlock>> toolUseBlockLists() {
        Arbitrary<ToolUseBlock> toolUseArbitrary = Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10)
        ).as((id, name) -> ToolUseBlock.builder()
                .toolUseId("tool-use-" + id)
                .name("tool_" + name)
                .input(software.amazon.awssdk.core.document.Document.fromString("{}"))
                .build());

        return toolUseArbitrary.list().ofMinSize(1).ofMaxSize(3);
    }

    /**
     * Generates lists of 1–5 non-empty text strings for building ContentBlock lists.
     */
    @Provide
    Arbitrary<List<String>> textBlockLists() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(50)
                .list()
                .ofMinSize(1)
                .ofMaxSize(5);
    }

    /**
     * Generates turn counts between 1 and 5 (inclusive).
     */
    @Provide
    Arbitrary<Integer> turnCounts() {
        return Arbitraries.integers().between(1, 5);
    }
}
