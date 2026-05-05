package com.example.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link GetCurrentTimeTool}.
 *
 * Validates: Requirements 6.2
 */
class GetCurrentTimeToolTest {

    private GetCurrentTimeTool tool;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        tool = new GetCurrentTimeTool();
        mapper = new ObjectMapper();
    }

    /**
     * When no "timezone" parameter is present the tool should default to UTC.
     */
    @Test
    void testInvokeWithoutTimezoneParameterReturnsUtcTime() {
        ObjectNode input = mapper.createObjectNode();

        String result = tool.execute(input);

        assertNotNull(result);
        assertTrue(result.startsWith("Current time in UTC:"),
                "Expected result to start with 'Current time in UTC:' but was: " + result);
        assertTrue(result.contains("UTC"),
                "Expected result to contain 'UTC' but was: " + result);
    }

    /**
     * When the "timezone" parameter is present but blank the tool should fall back to UTC.
     */
    @Test
    void testInvokeWithBlankTimezoneValueReturnsUtcTime() {
        ObjectNode input = mapper.createObjectNode();
        input.put("timezone", "   ");

        String result = tool.execute(input);

        assertNotNull(result);
        assertTrue(result.startsWith("Current time in UTC:"),
                "Expected result to start with 'Current time in UTC:' but was: " + result);
        assertTrue(result.contains("UTC"),
                "Expected result to contain 'UTC' but was: " + result);
    }
}
