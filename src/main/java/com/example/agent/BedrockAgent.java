package com.example.agent;

import com.example.agent.config.AgentConfig;
import com.example.agent.knowledge.KnowledgeBaseService;
import com.example.agent.tools.Tool;
import com.example.agent.tools.ToolRegistry;
import com.example.agent.util.DocumentConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

import java.util.ArrayList;
import java.util.List;

/**
 * Core AI Agent that:
 * 1. Maintains conversation history
 * 2. Retrieves context from a Bedrock Knowledge Base before each turn
 * 3. Calls Claude via the Bedrock Converse API (supports tool use natively)
 * 4. Executes tool calls and feeds results back to the model
 * 5. Loops until the model produces a final text response
 */
public class BedrockAgent {

    private static final Logger log = LoggerFactory.getLogger(BedrockAgent.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Max tool-call iterations per turn to prevent infinite loops
    private static final int MAX_TOOL_ITERATIONS = 10;

    private final BedrockRuntimeClient bedrockClient;
    private final AgentConfig config;
    private final ToolRegistry toolRegistry;
    private final KnowledgeBaseService knowledgeBaseService;

    // Conversation history — persisted across turns
    private final List<Message> conversationHistory = new ArrayList<>();

    public BedrockAgent(AgentConfig config, ToolRegistry toolRegistry, KnowledgeBaseService knowledgeBaseService) {
        this.config = config;
        this.toolRegistry = toolRegistry;
        this.knowledgeBaseService = knowledgeBaseService;

        this.bedrockClient = BedrockRuntimeClient.builder()
                .region(Region.of(config.getAwsRegion()))
                .build();

        log.info("BedrockAgent initialized with model: {}", config.getModelId());
    }

    /**
     * Package-private constructor for testing: accepts a pre-built mock client.
     *
     * @param bedrockClient      the mock {@link BedrockRuntimeClient} to use
     * @param config             the agent configuration
     * @param toolRegistry       the tool registry
     * @param knowledgeBaseService the knowledge base service
     */
    BedrockAgent(BedrockRuntimeClient bedrockClient, AgentConfig config,
                 ToolRegistry toolRegistry, KnowledgeBaseService knowledgeBaseService) {
        this.bedrockClient = bedrockClient;
        this.config = config;
        this.toolRegistry = toolRegistry;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    /**
     * Send a user message and get the agent's response.
     * Handles tool calls automatically in a loop.
     *
     * @param userMessage the user's input text
     * @return the agent's final text response
     */
    public String chat(String userMessage) {
        log.debug("User: {}", userMessage);

        // Step 1: Retrieve relevant context from knowledge base
        String kbContext = knowledgeBaseService.retrieve(userMessage);

        // Step 2: Build the user message, prepending KB context if available
        String fullUserMessage = userMessage;
        if (!kbContext.isEmpty()) {
            fullUserMessage = kbContext + "\n\nUser question: " + userMessage;
            log.debug("Augmented user message with knowledge base context");
        }

        // Step 3: Add user message to history
        conversationHistory.add(Message.builder()
                .role(ConversationRole.USER)
                .content(ContentBlock.fromText(fullUserMessage))
                .build());

        // Step 4: Agentic loop — call model, handle tool calls, repeat until done
        String finalResponse = runAgentLoop();

        log.debug("Agent: {}", finalResponse);
        return finalResponse;
    }

    /**
     * The agentic loop:
     * - Calls the model
     * - If the model requests tool use, executes tools and feeds results back
     * - Repeats until the model returns a final text response
     */
    private String runAgentLoop() {
        for (int iteration = 0; iteration < MAX_TOOL_ITERATIONS; iteration++) {
            ConverseResponse response = callBedrock();
            StopReason stopReason = response.stopReason();
            Message assistantMessage = response.output().message();

            log.debug("Model stop reason: {}", stopReason);

            // Add assistant's response to history
            conversationHistory.add(assistantMessage);

            if (stopReason == StopReason.END_TURN || stopReason == StopReason.MAX_TOKENS) {
                // Model is done — extract and return the text response
                return extractTextFromMessage(assistantMessage);
            }

            if (stopReason == StopReason.GUARDRAIL_INTERVENED) {
                // A Bedrock Guardrail blocked or modified the response
                log.warn("Guardrail intervened (iteration {})", iteration + 1);
                return extractTextFromMessage(assistantMessage);
            }

            if (stopReason == StopReason.TOOL_USE) {
                // Model wants to use tools — execute them and continue the loop
                List<ToolResultBlock> toolResults = executeToolCalls(assistantMessage);

                // Add tool results back to the conversation as a user message
                List<ContentBlock> resultBlocks = toolResults.stream()
                        .map(ContentBlock::fromToolResult)
                        .toList();

                conversationHistory.add(Message.builder()
                        .role(ConversationRole.USER)
                        .content(resultBlocks)
                        .build());

                log.debug("Executed {} tool(s), continuing agent loop (iteration {})", toolResults.size(), iteration + 1);
                continue;
            }

            // Unexpected stop reason
            log.warn("Unexpected stop reason: {}", stopReason);
            return extractTextFromMessage(assistantMessage);
        }

        log.warn("Agent loop reached max iterations ({})", MAX_TOOL_ITERATIONS);
        return "I'm sorry, I got stuck in a loop trying to answer your question. Please try rephrasing.";
    }

    /** Calls the Bedrock Converse API with the current conversation history. */
    private ConverseResponse callBedrock() {
        ConverseRequest.Builder requestBuilder = ConverseRequest.builder()
                .modelId(config.getModelId())
                .messages(conversationHistory)
                .system(SystemContentBlock.fromText(config.getSystemPrompt()))
                .inferenceConfig(InferenceConfiguration.builder()
                        .maxTokens(config.getMaxTokens())
                        .temperature(0.7f)
                        .build());

        // Attach tool definitions if any tools are registered
        if (!toolRegistry.getAll().isEmpty()) {
            requestBuilder.toolConfig(buildToolConfig());
        }

        // Attach guardrail if configured
        if (config.isGuardrailConfigured()) {
            requestBuilder.guardrailConfig(GuardrailConfiguration.builder()
                    .guardrailIdentifier(config.getGuardrailId())
                    .guardrailVersion(config.getGuardrailVersion())
                    .build());
            log.debug("Guardrail applied: {} ({})", config.getGuardrailId(), config.getGuardrailVersion());
        }

        try {
            return bedrockClient.converse(requestBuilder.build());
        } catch (Exception e) {
            log.error("Bedrock API call failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to call Bedrock: " + e.getMessage(), e);
        }
    }

    /** Builds the ToolConfiguration from all registered tools. */
    private ToolConfiguration buildToolConfig() {
        List<software.amazon.awssdk.services.bedrockruntime.model.Tool> bedrockTools =
                toolRegistry.getAll().stream()
                        .map(tool -> {
                            // Convert Jackson ObjectNode schema to AWS SDK Document
                            Document schemaDocument = DocumentConverter.toDocument(tool.getInputSchema());
                            return software.amazon.awssdk.services.bedrockruntime.model.Tool.builder()
                                    .toolSpec(ToolSpecification.builder()
                                            .name(tool.getName())
                                            .description(tool.getDescription())
                                            .inputSchema(ToolInputSchema.builder()
                                                    .json(schemaDocument)
                                                    .build())
                                            .build())
                                    .build();
                        })
                        .toList();

        return ToolConfiguration.builder()
                .tools(bedrockTools)
                .build();
    }
    /**
     * Finds all tool use blocks in the assistant message,
     * executes each tool, and returns the results.
     */
    private List<ToolResultBlock> executeToolCalls(Message assistantMessage) {
        List<ToolResultBlock> results = new ArrayList<>();

        for (ContentBlock block : assistantMessage.content()) {
            if (block.toolUse() == null) continue;

            ToolUseBlock toolUse = block.toolUse();
            String toolName = toolUse.name();
            String toolUseId = toolUse.toolUseId();

            log.info("Executing tool: {} (id={})", toolName, toolUseId);

            Tool tool = toolRegistry.get(toolName);
            String resultText;

            if (tool == null) {
                log.warn("Unknown tool requested: {}", toolName);
                resultText = "Error: Tool '" + toolName + "' is not available.";
            } else {
                try {
                    // Parse the tool input JSON
                    ObjectNode inputJson = parseToolInput(toolUse.input());
                    log.debug("Tool input: {}", inputJson);

                    resultText = tool.execute(inputJson);
                    log.debug("Tool result: {}", resultText);
                } catch (Exception e) {
                    log.error("Tool '{}' threw an exception: {}", toolName, e.getMessage(), e);
                    resultText = "Error executing tool '" + toolName + "': " + e.getMessage();
                }
            }

            results.add(ToolResultBlock.builder()
                    .toolUseId(toolUseId)
                    .content(ToolResultContentBlock.fromText(resultText))
                    .build());
        }

        return results;
    }

    /**
     * Parses the tool input from the Bedrock SDK Document type into a Jackson ObjectNode.
     */
    private ObjectNode parseToolInput(Document input) {
        return DocumentConverter.documentToObjectNode(input, MAPPER);
    }

    /** Extracts the text content from an assistant message. */
    private String extractTextFromMessage(Message message) {
        StringBuilder text = new StringBuilder();
        for (ContentBlock block : message.content()) {
            if (block.text() != null) {
                text.append(block.text());
            }
        }
        return text.toString().trim();
    }

    /** Clears the conversation history (start a new session). */
    public void resetConversation() {
        conversationHistory.clear();
        log.info("Conversation history cleared");
    }

    /** Returns the number of messages in the current conversation. */
    public int getConversationLength() {
        return conversationHistory.size();
    }
}
