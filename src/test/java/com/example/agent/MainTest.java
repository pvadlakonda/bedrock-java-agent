package com.example.agent;

import com.example.agent.config.AgentConfig;
import com.example.agent.knowledge.KnowledgeBaseService;
import com.example.agent.tools.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Main.handleCommand().
 * Validates: Requirements 10.3, 10.4, 10.5, 10.6
 */
class MainTest {

    private BedrockAgent agent;

    @BeforeEach
    void setUp() {
        BedrockRuntimeClient mockBedrock = mock(BedrockRuntimeClient.class);
        AgentConfig mockConfig = mock(AgentConfig.class);
        ToolRegistry mockRegistry = mock(ToolRegistry.class);
        KnowledgeBaseService mockKb = mock(KnowledgeBaseService.class);

        when(mockConfig.getModelId()).thenReturn("anthropic.claude-3-haiku-20240307-v1:0");
        when(mockConfig.getSystemPrompt()).thenReturn("You are a helpful AI assistant.");
        when(mockConfig.getMaxTokens()).thenReturn(1024);
        when(mockConfig.getAwsRegion()).thenReturn("us-east-1");
        when(mockKb.retrieve(any())).thenReturn("");
        when(mockRegistry.getAll()).thenReturn(Collections.emptyList());

        agent = new BedrockAgent(mockBedrock, mockConfig, mockRegistry, mockKb);
    }

    // -------------------------------------------------------------------------
    // /reset — calls resetConversation() and returns false (Req 10.3)
    // -------------------------------------------------------------------------

    /**
     * /reset must call agent.resetConversation() and return false (do not exit).
     * Validates: Requirements 10.3
     */
    @Test
    void resetCommandCallsResetConversationAndReturnsFalse() {
        BedrockAgent spyAgent = spy(agent);

        boolean shouldExit = Main.handleCommand("/reset", spyAgent);

        assertFalse(shouldExit, "/reset must return false (do not exit)");
        verify(spyAgent, times(1)).resetConversation();
    }

    // -------------------------------------------------------------------------
    // /quit, /exit, /q — return true (Req 10.4)
    // -------------------------------------------------------------------------

    /**
     * /quit must return true (exit the loop).
     * Validates: Requirements 10.4
     */
    @Test
    void quitCommandReturnsTrue() {
        boolean shouldExit = Main.handleCommand("/quit", agent);
        assertTrue(shouldExit, "/quit must return true (exit)");
    }

    /**
     * /exit must return true (exit the loop).
     * Validates: Requirements 10.4
     */
    @Test
    void exitCommandReturnsTrue() {
        boolean shouldExit = Main.handleCommand("/exit", agent);
        assertTrue(shouldExit, "/exit must return true (exit)");
    }

    /**
     * /q must return true (exit the loop).
     * Validates: Requirements 10.4
     */
    @Test
    void qShorthandCommandReturnsTrue() {
        boolean shouldExit = Main.handleCommand("/q", agent);
        assertTrue(shouldExit, "/q must return true (exit)");
    }

    // -------------------------------------------------------------------------
    // /help, /? — return false (Req 10.5)
    // -------------------------------------------------------------------------

    /**
     * /help must return false (do not exit).
     * Validates: Requirements 10.5
     */
    @Test
    void helpCommandReturnsFalse() {
        boolean shouldExit = Main.handleCommand("/help", agent);
        assertFalse(shouldExit, "/help must return false (do not exit)");
    }

    /**
     * /? must return false (do not exit).
     * Validates: Requirements 10.5
     */
    @Test
    void helpShorthandCommandReturnsFalse() {
        boolean shouldExit = Main.handleCommand("/?", agent);
        assertFalse(shouldExit, "/? must return false (do not exit)");
    }

    // -------------------------------------------------------------------------
    // Unknown command — returns false (Req 10.6)
    // -------------------------------------------------------------------------

    /**
     * An unrecognized slash command must return false (do not exit).
     * Validates: Requirements 10.6
     */
    @Test
    void unknownCommandReturnsFalse() {
        boolean shouldExit = Main.handleCommand("/unknown", agent);
        assertFalse(shouldExit, "/unknown must return false (do not exit)");
    }
}
