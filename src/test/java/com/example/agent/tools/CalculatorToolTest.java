package com.example.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CalculatorTool.
 * Feature: bedrock-java-agent
 * Validates: Requirements 7.3
 */
class CalculatorToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ---------------------------------------------------------------------------
    // Requirement 7.3: Missing 'expression' parameter returns an error string
    // ---------------------------------------------------------------------------

    @Test
    void missingExpressionParameterReturnsError() {
        CalculatorTool tool = new CalculatorTool();
        ObjectNode emptyInput = MAPPER.createObjectNode();

        String result = tool.execute(emptyInput);

        assertEquals("Error: 'expression' parameter is required.", result,
                "Expected specific error message when 'expression' parameter is absent");
    }
}
