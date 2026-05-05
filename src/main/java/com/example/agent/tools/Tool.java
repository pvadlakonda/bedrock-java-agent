package com.example.agent.tools;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Contract for all agent tools.
 * Each tool declares its name, description, and JSON Schema for its input,
 * and implements an execute() method that returns a plain-text result.
 */
public interface Tool {

    /** Unique tool name — must match what you register with Bedrock. */
    String getName();

    /** Human-readable description sent to the model so it knows when to use this tool. */
    String getDescription();

    /**
     * JSON Schema object describing the tool's input parameters.
     * Example:
     * {
     *   "type": "object",
     *   "properties": {
     *     "expression": { "type": "string", "description": "Math expression to evaluate" }
     *   },
     *   "required": ["expression"]
     * }
     */
    ObjectNode getInputSchema();

    /**
     * Execute the tool with the given JSON input (already parsed).
     *
     * @param input parsed JSON object matching the declared schema
     * @return plain-text result to send back to the model
     */
    String execute(ObjectNode input);
}
