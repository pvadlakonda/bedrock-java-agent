package com.example.agent.tools;

import com.fasterxml.jackson.databind.node.ObjectNode;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;
import net.jqwik.api.constraints.UniqueElements;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for ToolRegistry using jqwik.
 * Feature: bedrock-java-agent
 */
class ToolRegistryPropertyTest {

    // ---------------------------------------------------------------------------
    // Stub Tool implementation used across all properties
    // ---------------------------------------------------------------------------

    private static Tool stubTool(String name) {
        return new Tool() {
            @Override public String getName()        { return name; }
            @Override public String getDescription() { return "stub-" + name; }
            @Override public ObjectNode getInputSchema() { return null; }
            @Override public String execute(ObjectNode input) { return ""; }
        };
    }

    // ---------------------------------------------------------------------------
    // Property 1: Tool Registry Round-Trip
    // Validates: Requirements 2.1, 2.2, 2.5
    // ---------------------------------------------------------------------------

    // Feature: bedrock-java-agent, Property 1: Tool registry round-trip
    @Property
    void toolRegistryRoundTrip(
            @ForAll @UniqueElements List<@AlphaChars @StringLength(min = 1, max = 20) String> names) {

        ToolRegistry registry = new ToolRegistry();
        for (String name : names) {
            registry.register(stubTool(name));
        }

        // Each registered tool must be retrievable by its exact name
        for (String name : names) {
            Tool found = registry.get(name);
            assertNotNull(found, "Expected to find tool with name: " + name);
            assertEquals(name, found.getName());
        }

        // getAll() must return exactly the registered tools (same names, same count)
        Set<String> allNames = registry.getAll().stream()
                .map(Tool::getName)
                .collect(Collectors.toSet());
        assertEquals(
                names.stream().collect(Collectors.toSet()),
                allNames,
                "getAll() should return exactly the registered tools"
        );
    }

    // ---------------------------------------------------------------------------
    // Property 2: Tool Registry Returns Null for Unknown Names
    // Validates: Requirements 2.3
    // ---------------------------------------------------------------------------

    // Feature: bedrock-java-agent, Property 2: Null for unknown names
    @Property
    void nullForUnknownNames(
            @ForAll @UniqueElements List<@AlphaChars @StringLength(min = 1, max = 20) String> registeredNames,
            @ForAll @AlphaChars @StringLength(min = 1, max = 20) String unknownName) {

        Assume.that(!registeredNames.contains(unknownName));

        ToolRegistry registry = new ToolRegistry();
        for (String name : registeredNames) {
            registry.register(stubTool(name));
        }

        assertNull(registry.get(unknownName),
                "get() should return null for a name not in the registry");
    }
}
