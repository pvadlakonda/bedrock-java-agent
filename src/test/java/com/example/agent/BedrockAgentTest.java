package com.example.agent;

import com.example.agent.config.AgentConfig;
import com.example.agent.knowledge.KnowledgeBaseService;
import com.example.agent.tools.Tool;
import com.example.agent.tools.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BedrockAgent covering error handling and edge cases.
 * Validates: Requirements 5.4, 5.5, 5.7, 5.8, 9.3
 */
class BedrockAgentTest {

    private BedrockRuntimeClient mockBedrock;
    private AgentConfig mockConfig;
    private ToolRegistry mockRegistry;
    private KnowledgeBaseService mockKb;
    private BedrockAgent agent;

    @BeforeEach
    void setUp() {
        mockBedrock = mock(BedrockRuntimeClient.class);
        mockConfig = mock(AgentConfig.class);
        mockRegistry = mock(ToolRegistry.class);
        mockKb = mock(KnowledgeBaseService.class);

        when(mockConfig.getModelId()).thenReturn("anthropic.claude-3-haiku-20240307-v1:0");
        when(mockConfig.getSystemPrompt()).thenReturn("You are a helpful AI assistant.");
        when(mockConfig.getMaxTokens()).thenReturn(1024);
        when(mockConfig.getAwsRegion()).thenReturn("us-east-1");

        when(mockKb.retrieve(any())).thenReturn("");
        when(mockRegistry.getAll()).thenReturn(Collections.emptyList());

        agent = new BedrockAgent(mockBedrock, mockConfig, mockRegistry, mockKb);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Builds an END_TURN response with a single text block. */
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

    /** Builds a TOOL_USE response with a single tool use block. */
    private ConverseResponse toolUseResponse(String toolUseId, String toolName) {
        ToolUseBlock toolUseBlock = ToolUseBlock.builder()
                .toolUseId(toolUseId)
                .name(toolName)
                .input(Document.fromString("{}"))
                .build();
        ContentBlock block = ContentBlock.fromToolUse(toolUseBlock);
        Message assistantMessage = Message.builder()
                .role(ConversationRole.ASSISTANT)
                .content(block)
                .build();
        ConverseOutput output = ConverseOutput.builder()
                .message(assistantMessage)
                .build();
        return ConverseResponse.builder()
                .output(output)
                .stopReason(StopReason.TOOL_USE)
                .build();
    }

    // -------------------------------------------------------------------------
    // Test 1: Unknown tool name returns error string to model (Req 5.4)
    // -------------------------------------------------------------------------

    /**
     * When the model requests a tool that is not registered, the agent must
     * return an error string in the tool result content block — not throw an exception.
     * Validates: Requirements 5.4
     */
    @Test
    void unknownToolNameReturnsErrorStringToModel() {
        String unknownToolName = "nonexistent_tool";
        String toolUseId = "tool-use-001";

        // Registry returns null for any tool name (tool not found)
        when(mockRegistry.get(unknownToolName)).thenReturn(null);

        // First call: model requests the unknown tool
        // Second call: model returns final answer after receiving the error result
        ArgumentCaptor<ConverseRequest> requestCaptor = ArgumentCaptor.forClass(ConverseRequest.class);
        when(mockBedrock.converse(requestCaptor.capture()))
                .thenReturn(toolUseResponse(toolUseId, unknownToolName))
                .thenReturn(endTurnResponse("I could not use that tool."));

        // Act — must not throw
        String result = agent.chat("use the unknown tool");

        // Assert: two Bedrock calls were made
        verify(mockBedrock, times(2)).converse(any(ConverseRequest.class));

        // The second request must contain a tool result with an error string
        List<ConverseRequest> capturedRequests = requestCaptor.getAllValues();
        ConverseRequest secondRequest = capturedRequests.get(1);

        // Find the user message that carries the tool result
        Message toolResultMessage = secondRequest.messages().stream()
                .filter(m -> m.role() == ConversationRole.USER)
                .filter(m -> m.content().stream().anyMatch(cb -> cb.toolResult() != null))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No tool result message found in second request"));

        // Extract the tool result content text
        String toolResultText = toolResultMessage.content().stream()
                .filter(cb -> cb.toolResult() != null)
                .map(cb -> cb.toolResult().content().get(0).text())
                .findFirst()
                .orElseThrow(() -> new AssertionError("No tool result content found"));

        assertNotNull(toolResultText, "Tool result text must not be null");
        assertTrue(toolResultText.toLowerCase().contains("error"),
                "Tool result must contain an error message, but was: " + toolResultText);
        assertTrue(toolResultText.contains(unknownToolName),
                "Tool result must reference the unknown tool name, but was: " + toolResultText);

        // The final return value is the model's text response
        assertEquals("I could not use that tool.", result);
    }

    // -------------------------------------------------------------------------
    // Test 2: Tool execute() throwing an exception returns error string (Req 5.5)
    // -------------------------------------------------------------------------

    /**
     * When a registered tool's execute() method throws an exception, the agent
     * must catch it and return an error string in the tool result — not propagate the exception.
     * Validates: Requirements 5.5
     */
    @Test
    void toolExecuteThrowingExceptionReturnsErrorStringToModel() {
        String toolName = "failing_tool";
        String toolUseId = "tool-use-002";
        String exceptionMessage = "Something went wrong internally";

        // Create a mock tool that throws when executed
        Tool failingTool = mock(Tool.class);
        when(failingTool.getName()).thenReturn(toolName);
        when(failingTool.getDescription()).thenReturn("A tool that fails");
        when(failingTool.execute(any())).thenThrow(new RuntimeException(exceptionMessage));

        when(mockRegistry.get(toolName)).thenReturn(failingTool);

        ArgumentCaptor<ConverseRequest> requestCaptor = ArgumentCaptor.forClass(ConverseRequest.class);
        when(mockBedrock.converse(requestCaptor.capture()))
                .thenReturn(toolUseResponse(toolUseId, toolName))
                .thenReturn(endTurnResponse("I encountered an error with that tool."));

        // Act — must not throw
        String result = agent.chat("use the failing tool");

        // Assert: two Bedrock calls were made
        verify(mockBedrock, times(2)).converse(any(ConverseRequest.class));

        // The second request must contain a tool result with an error string
        List<ConverseRequest> capturedRequests = requestCaptor.getAllValues();
        ConverseRequest secondRequest = capturedRequests.get(1);

        String toolResultText = secondRequest.messages().stream()
                .filter(m -> m.role() == ConversationRole.USER)
                .filter(m -> m.content().stream().anyMatch(cb -> cb.toolResult() != null))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No tool result message found"))
                .content().stream()
                .filter(cb -> cb.toolResult() != null)
                .map(cb -> cb.toolResult().content().get(0).text())
                .findFirst()
                .orElseThrow(() -> new AssertionError("No tool result content found"));

        assertNotNull(toolResultText, "Tool result text must not be null");
        assertTrue(toolResultText.toLowerCase().contains("error"),
                "Tool result must contain an error message, but was: " + toolResultText);
        assertTrue(toolResultText.contains(toolName),
                "Tool result must reference the tool name, but was: " + toolResultText);
        assertTrue(toolResultText.contains(exceptionMessage),
                "Tool result must contain the exception message, but was: " + toolResultText);

        assertEquals("I encountered an error with that tool.", result);
    }

    // -------------------------------------------------------------------------
    // Test 3: Agentic loop hitting 10 iterations returns fallback message (Req 5.7)
    // -------------------------------------------------------------------------

    /**
     * When the agentic loop reaches 10 iterations without a final text response,
     * the agent must return the fixed fallback message.
     * Validates: Requirements 5.7
     */
    @Test
    void agenticLoopHittingMaxIterationsReturnsFallbackMessage() {
        String toolName = "infinite_tool";
        String toolUseId = "tool-use-loop";

        // Registry returns a tool so the loop can proceed
        Tool infiniteTool = mock(Tool.class);
        when(infiniteTool.getName()).thenReturn(toolName);
        when(infiniteTool.execute(any())).thenReturn("tool result");
        when(mockRegistry.get(toolName)).thenReturn(infiniteTool);

        // Bedrock always returns TOOL_USE — never END_TURN
        when(mockBedrock.converse(any(ConverseRequest.class)))
                .thenReturn(toolUseResponse(toolUseId, toolName));

        // Act
        String result = agent.chat("keep looping");

        // Assert: the fallback message is returned
        assertNotNull(result, "Result must not be null");
        assertFalse(result.isBlank(), "Result must not be blank");
        // The exact fallback message from BedrockAgent
        assertEquals(
                "I'm sorry, I got stuck in a loop trying to answer your question. Please try rephrasing.",
                result,
                "Agent must return the fixed fallback message after max iterations"
        );

        // Bedrock must have been called exactly 10 times (MAX_TOOL_ITERATIONS)
        verify(mockBedrock, times(10)).converse(any(ConverseRequest.class));
    }

    // -------------------------------------------------------------------------
    // Test 4: Bedrock API exception is wrapped in RuntimeException (Req 5.8)
    // -------------------------------------------------------------------------

    /**
     * When the Bedrock Converse API throws an exception, the agent must wrap it
     * in a RuntimeException and propagate it to the caller.
     * Validates: Requirements 5.8
     */
    @Test
    void bedrockApiExceptionIsWrappedInRuntimeException() {
        RuntimeException bedrockException = new RuntimeException("Bedrock service unavailable");
        when(mockBedrock.converse(any(ConverseRequest.class))).thenThrow(bedrockException);

        // Act & Assert: chat() must throw a RuntimeException
        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> agent.chat("hello"),
                "chat() must throw a RuntimeException when Bedrock API fails");

        assertNotNull(thrown.getMessage(), "Exception message must not be null");
        assertTrue(thrown.getMessage().contains("Bedrock") || thrown.getMessage().contains("Failed"),
                "Exception message should describe the failure, but was: " + thrown.getMessage());
    }

    // -------------------------------------------------------------------------
    // Test 5: resetConversation() clears history to zero length (Req 9.3)
    // -------------------------------------------------------------------------

    /**
     * After calling resetConversation(), the conversation history must be empty.
     * Validates: Requirements 9.3
     */
    @Test
    void resetConversationClearsHistoryToZeroLength() {
        // Arrange: perform a chat turn to populate history
        when(mockBedrock.converse(any(ConverseRequest.class)))
                .thenReturn(endTurnResponse("Hello! How can I help?"));

        agent.chat("Hello");

        // Verify history is non-empty after a chat turn
        assertTrue(agent.getConversationLength() > 0,
                "Conversation history must be non-empty after a chat turn");

        // Act: reset the conversation
        agent.resetConversation();

        // Assert: history is now empty
        assertEquals(0, agent.getConversationLength(),
                "Conversation history must be zero after resetConversation()");
    }

    // -------------------------------------------------------------------------
    // Test 6: GUARDRAIL_INTERVENED stop reason returns replacement message (Req 13.3, 13.4)
    // -------------------------------------------------------------------------

    /**
     * When Bedrock returns GUARDRAIL_INTERVENED, the agent must return the
     * guardrail's replacement message text and stop the agentic loop immediately
     * — it must not make another Bedrock call.
     * Validates: Requirements 13.3, 13.4
     */
    @Test
    void guardrailIntervenedReturnsReplacementMessageAndStopsLoop() {
        String replacementMessage = "I'm sorry, I can't help with that request.";

        // Build a GUARDRAIL_INTERVENED response carrying the replacement text
        Message assistantMessage = Message.builder()
                .role(ConversationRole.ASSISTANT)
                .content(ContentBlock.fromText(replacementMessage))
                .build();
        ConverseResponse guardrailResponse = ConverseResponse.builder()
                .output(ConverseOutput.builder().message(assistantMessage).build())
                .stopReason(StopReason.GUARDRAIL_INTERVENED)
                .build();

        when(mockBedrock.converse(any(ConverseRequest.class))).thenReturn(guardrailResponse);

        // Act
        String result = agent.chat("say something harmful");

        // Assert: the guardrail replacement message is returned
        assertEquals(replacementMessage, result,
                "Agent must return the guardrail replacement message when GUARDRAIL_INTERVENED");

        // Assert: Bedrock was called exactly once — the loop must not continue
        verify(mockBedrock, times(1)).converse(any(ConverseRequest.class));
    }

    /**
     * When GUARDRAIL_INTERVENED is returned, the agentic loop must not add
     * any further messages to the conversation history beyond the initial
     * user message and the assistant's guardrail response.
     * Validates: Requirements 13.3
     */
    @Test
    void guardrailIntervenedDoesNotContinueAgenticLoop() {
        String replacementMessage = "That topic is not allowed.";

        Message assistantMessage = Message.builder()
                .role(ConversationRole.ASSISTANT)
                .content(ContentBlock.fromText(replacementMessage))
                .build();
        ConverseResponse guardrailResponse = ConverseResponse.builder()
                .output(ConverseOutput.builder().message(assistantMessage).build())
                .stopReason(StopReason.GUARDRAIL_INTERVENED)
                .build();

        when(mockBedrock.converse(any(ConverseRequest.class))).thenReturn(guardrailResponse);

        agent.chat("blocked input");

        // History should contain exactly 2 messages: user + assistant (guardrail response)
        assertEquals(2, agent.getConversationLength(),
                "Conversation history must contain exactly 2 messages after a guardrail intervention");
    }
}