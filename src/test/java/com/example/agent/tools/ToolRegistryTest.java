package com.example.agent.tools;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ToolRegistry.
 * Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5
 */
class ToolRegistryTest {

    private ToolRegistry registry;

    // ---------------------------------------------------------------------------
    // Stub Tool implementation for testing
    // ---------------------------------------------------------------------------

    private static Tool stubTool(String name) {
        return new Tool() {
            @Override public String getName()        { return name; }
            @Override public String getDescription() { return "stub-" + name; }
            @Override public ObjectNode getInputSchema() { return null; }
            @Override public String execute(ObjectNode input) { return ""; }
        };
    }

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
    }

    // ---------------------------------------------------------------------------
    // Tests for duplicate registration
    // ---------------------------------------------------------------------------

    @Test
    void duplicateRegistrationThrowsIllegalArgumentException() {
        Tool tool = stubTool("calculator");
        registry.register(tool);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(stubTool("calculator")),
                "Registering a tool with a duplicate name should throw IllegalArgumentException"
        );
        assertTrue(ex.getMessage().contains("calculator"),
                "Exception message should mention the duplicate tool name");
    }

    @Test
    void duplicateRegistrationWithSameInstanceThrowsIllegalArgumentException() {
        Tool tool = stubTool("myTool");
        registry.register(tool);

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(tool),
                "Re-registering the same tool instance should throw IllegalArgumentException"
        );
    }

    // ---------------------------------------------------------------------------
    // Tests for get() on empty registry
    // ---------------------------------------------------------------------------

    @Test
    void getOnEmptyRegistryReturnsNull() {
        assertNull(registry.get("nonexistent"),
                "get() on an empty registry should return null");
    }

    @Test
    void getUnknownNameAfterRegistrationReturnsNull() {
        registry.register(stubTool("knownTool"));

        assertNull(registry.get("unknownTool"),
                "get() for an unregistered name should return null");
    }

    // ---------------------------------------------------------------------------
    // Tests for getAll() on empty registry
    // ---------------------------------------------------------------------------

    @Test
    void getAllOnEmptyRegistryReturnsEmptyCollection() {
        assertTrue(registry.getAll().isEmpty(),
                "getAll() on an empty registry should return an empty collection");
    }

    @Test
    void getAllReturnsAllRegisteredTools() {
        registry.register(stubTool("toolA"));
        registry.register(stubTool("toolB"));

        assertEquals(2, registry.getAll().size(),
                "getAll() should return all registered tools");
    }
}
