package com.example.agent;

import com.example.agent.config.AgentConfig;
import com.example.agent.knowledge.KnowledgeBaseService;
import com.example.agent.tools.CalculatorTool;
import com.example.agent.tools.GetCurrentTimeTool;
import com.example.agent.tools.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration test for {@link BedrockAgent}.
 *
 * Mocks the {@link BedrockRuntimeClient} at the SDK level and verifies that a
 * full chat turn sends the complete conversation history, system prompt,
 * configured max tokens, and tool definitions in the Converse request.
 *
 * Validates: Requirements 5.2, 9.2
 */
class BedrockAgentIntegrationTest {

    private static final String MODEL_ID = "anthropic.claude-3-haiku-20240307-v1:0";
    private static final String SYSTEM_PROMPT = "You are a helpful AI assistant.";
    private static final int MAX_TOKENS = 512;

    private BedrockRuntimeClient mockBedrock;
    private AgentConfig mockConfig;
    private ToolRegistry toolRegistry;
    private KnowledgeBaseService mockKb;
    private BedrockAgent agent;

    @BeforeEach
    void setUp() {
        mockBedrock = mock(BedrockRuntimeClient.class);
        mockConfig = mock(AgentConfig.class);
        mockKb = mock(KnowledgeBaseService.class);

        when(mockConfig.getModelId()).thenReturn(MODEL_ID);
        when(mockConfig.getSystemPrompt()).thenReturn(SYSTEM_PROMPT);
        when(mockConfig.getMaxTokens()).thenReturn(MAX_TOKENS);
        when(mockConfig.getAwsRegion()).thenReturn("us-east-1");

        // KB returns no context so the user message is stored as-is
        when(mockKb.retrieve(any())).thenReturn("");

        // Register two real tools so tool definitions are present in the request
        toolRegistry = new ToolRegistry();
        toolRegistry.register(new GetCurrentTimeTool());
        toolRegistry.register(new CalculatorTool());

        agent = new BedrockAgent(mockBedrock, mockConfig, toolRegistry, mockKb);
    }

    // -------------------------------------------------------------------------
    // Helper: build a simple END_TURN response
    // -------------------------------------------------------------------------

    private ConverseResponse endTurnResponse(String text) {
        Message assistantMessage = Message.builder()
                .role(ConversationRole.ASSISTANT)
                .content(ContentBlock.fromText(text))
                .build();
        return ConverseResponse.builder()
                .output(ConverseOutput.builder().message(assistantMessage).build())
                .stopReason(StopReason.END_TURN)
                .build();
    }

    // -------------------------------------------------------------------------
    // Test: full chat turn sends history, system prompt, max tokens, tool defs
    // -------------------------------------------------------------------------

    /**
     * A single chat turn must produce a Converse request that contains:
     * <ul>
     *   <li>The user message in the messages list (full conversation history)</li>
     *   <li>The configured system prompt in the system field</li>
     *   <li>The configured max tokens in inferenceConfig</li>
     *   <li>Tool definitions for every registered tool in toolConfig</li>
     * </ul>
     *
     * Validates: Requirements 5.2, 9.2
     */
    @Test
    void chatTurnSendsHistorySystemPromptMaxTokensAndToolDefinitions() {
        String userMessage = "What is 2 + 2?";
        String agentReply = "The answer is 4.";

        ArgumentCaptor<ConverseRequest> requestCaptor = ArgumentCaptor.forClass(ConverseRequest.class);
        when(mockBedrock.converse(requestCaptor.capture()))
                .thenReturn(endTurnResponse(agentReply));

        // Act
        String result = agent.chat(userMessage);

        // Verify the agent returned the expected reply
        assertEquals(agentReply, result);

        // Exactly one Bedrock call for a simple END_TURN response
        verify(mockBedrock, times(1)).converse(any(ConverseRequest.class));

        ConverseRequest capturedRequest = requestCaptor.getValue();

        // --- 1. Conversation history contains the user message ---
        List<Message> messages = capturedRequest.messages();
        assertNotNull(messages, "messages() must not be null");
        assertFalse(messages.isEmpty(), "messages() must not be empty");

        boolean userMessageFound = messages.stream()
                .filter(m -> m.role() == ConversationRole.USER)
                .flatMap(m -> m.content().stream())
                .anyMatch(cb -> cb.text() != null && cb.text().contains(userMessage));
        assertTrue(userMessageFound,
                "Conversation history must contain the user message '" + userMessage + "'");

        // --- 2. System prompt is present ---
        List<SystemContentBlock> systemBlocks = capturedRequest.system();
        assertNotNull(systemBlocks, "system() must not be null");
        assertFalse(systemBlocks.isEmpty(), "system() must not be empty");

        boolean systemPromptFound = systemBlocks.stream()
                .anyMatch(sb -> SYSTEM_PROMPT.equals(sb.text()));
        assertTrue(systemPromptFound,
                "System prompt must equal '" + SYSTEM_PROMPT + "'");

        // --- 3. Max tokens matches configured value ---
        InferenceConfiguration inferenceConfig = capturedRequest.inferenceConfig();
        assertNotNull(inferenceConfig, "inferenceConfig() must not be null");
        assertEquals(MAX_TOKENS, inferenceConfig.maxTokens(),
                "maxTokens must equal the configured value " + MAX_TOKENS);

        // --- 4. Tool definitions are present for all registered tools ---
        ToolConfiguration toolConfig = capturedRequest.toolConfig();
        assertNotNull(toolConfig, "toolConfig() must not be null when tools are registered");

        List<software.amazon.awssdk.services.bedrockruntime.model.Tool> bedrockTools = toolConfig.tools();
        assertNotNull(bedrockTools, "toolConfig.tools() must not be null");
        assertEquals(toolRegistry.getAll().size(), bedrockTools.size(),
                "Number of tool definitions must match the number of registered tools");

        // Verify each registered tool has a corresponding definition
        for (com.example.agent.tools.Tool registeredTool : toolRegistry.getAll()) {
            boolean toolFound = bedrockTools.stream()
                    .anyMatch(bt -> bt.toolSpec() != null
                            && registeredTool.getName().equals(bt.toolSpec().name()));
            assertTrue(toolFound,
                    "Tool definition for '" + registeredTool.getName() + "' must be present in toolConfig");
        }
    }

    // -------------------------------------------------------------------------
    // Test: multi-turn conversation accumulates history across turns (Req 9.2)
    // -------------------------------------------------------------------------

    /**
     * After two chat turns, the second Converse request must include messages
     * from both turns (full conversation history).
     *
     * Validates: Requirements 9.2
     */
    @Test
    void multiTurnConversationSendsFullHistoryOnEachTurn() {
        String firstMessage = "Hello, who are you?";
        String firstReply = "I am an AI assistant.";
        String secondMessage = "What can you do?";
        String secondReply = "I can answer questions and use tools.";

        ArgumentCaptor<ConverseRequest> requestCaptor = ArgumentCaptor.forClass(ConverseRequest.class);
        when(mockBedrock.converse(requestCaptor.capture()))
                .thenReturn(endTurnResponse(firstReply))
                .thenReturn(endTurnResponse(secondReply));

        // Act: two chat turns
        agent.chat(firstMessage);
        agent.chat(secondMessage);

        List<ConverseRequest> capturedRequests = requestCaptor.getAllValues();
        assertEquals(2, capturedRequests.size(), "Expected exactly 2 Bedrock calls");

        // First request: 1 user message
        ConverseRequest firstRequest = capturedRequests.get(0);
        assertEquals(1, firstRequest.messages().size(),
                "First request must contain exactly 1 message (the user message)");

        // Second request: 3 messages (user1, assistant1, user2)
        ConverseRequest secondRequest = capturedRequests.get(1);
        assertEquals(3, secondRequest.messages().size(),
                "Second request must contain 3 messages (full history: user1, assistant1, user2)");

        // Verify the second user message is present in the second request
        boolean secondUserMessageFound = secondRequest.messages().stream()
                .filter(m -> m.role() == ConversationRole.USER)
                .flatMap(m -> m.content().stream())
                .anyMatch(cb -> cb.text() != null && cb.text().contains(secondMessage));
        assertTrue(secondUserMessageFound,
                "Second request must contain the second user message");
    }
}
