package com.example.agent.tools;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry that holds all available tools.
 * Tools are looked up by name when the model requests a tool call.
 */
public class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    /** Register a tool. Throws if a tool with the same name already exists. */
    public void register(Tool tool) {
        if (tools.containsKey(tool.getName())) {
            throw new IllegalArgumentException("Tool already registered: " + tool.getName());
        }
        tools.put(tool.getName(), tool);
    }

    /** Look up a tool by name. Returns null if not found. */
    public Tool get(String name) {
        return tools.get(name);
    }

    /** All registered tools (used to build the tool config sent to Bedrock). */
    public Collection<Tool> getAll() {
        return tools.values();
    }
}
