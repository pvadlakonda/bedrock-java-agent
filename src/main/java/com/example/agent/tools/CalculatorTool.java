package com.example.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

/**
 * Tool: calculator
 * Evaluates a mathematical expression and returns the result.
 *
 * Uses the Nashorn/GraalJS script engine for safe expression evaluation.
 * Only numeric expressions are allowed — no function calls or assignments.
 */
public class CalculatorTool implements Tool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Regex: allow digits, operators, parentheses, dots, spaces, and e/E for scientific notation
    private static final String SAFE_EXPRESSION_PATTERN = "[0-9+\\-*/().\\s%eE]+";

    @Override
    public String getName() {
        return "calculator";
    }

    @Override
    public String getDescription() {
        return "Evaluates a mathematical expression and returns the numeric result. " +
               "Supports +, -, *, /, %, parentheses, and decimal numbers. " +
               "Example: '(100 + 50) * 2 / 3'";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");
        ObjectNode expression = properties.putObject("expression");
        expression.put("type", "string");
        expression.put("description", "The mathematical expression to evaluate, e.g. '(100 + 50) * 2'");

        schema.putArray("required").add("expression");
        return schema;
    }

    @Override
    public String execute(ObjectNode input) {
        if (!input.has("expression")) {
            return "Error: 'expression' parameter is required.";
        }

        String expression = input.get("expression").asText().trim();

        // Safety check — only allow safe characters
        if (!expression.matches(SAFE_EXPRESSION_PATTERN)) {
            return "Error: Expression contains invalid characters. Only numeric operators are allowed.";
        }

        try {
            ScriptEngineManager manager = new ScriptEngineManager();
            ScriptEngine engine = manager.getEngineByName("JavaScript");

            if (engine == null) {
                // Fallback: simple eval for basic arithmetic using Java
                return evaluateSimple(expression);
            }

            Object result = engine.eval(expression);
            return "Result: " + result;
        } catch (Exception e) {
            return "Error evaluating expression '" + expression + "': " + e.getMessage();
        }
    }

    /**
     * Very simple fallback evaluator for basic expressions when no script engine is available.
     * Handles only single-operator expressions like "3 + 4".
     */
    private String evaluateSimple(String expression) {
        try {
            expression = expression.replaceAll("\\s+", "");
            if (expression.contains("+")) {
                String[] parts = expression.split("\\+", 2);
                return "Result: " + (Double.parseDouble(parts[0]) + Double.parseDouble(parts[1]));
            } else if (expression.contains("-")) {
                String[] parts = expression.split("-", 2);
                return "Result: " + (Double.parseDouble(parts[0]) - Double.parseDouble(parts[1]));
            } else if (expression.contains("*")) {
                String[] parts = expression.split("\\*", 2);
                return "Result: " + (Double.parseDouble(parts[0]) * Double.parseDouble(parts[1]));
            } else if (expression.contains("/")) {
                String[] parts = expression.split("/", 2);
                double divisor = Double.parseDouble(parts[1]);
                if (divisor == 0) return "Error: Division by zero.";
                return "Result: " + (Double.parseDouble(parts[0]) / divisor);
            }
            return "Result: " + Double.parseDouble(expression);
        } catch (NumberFormatException e) {
            return "Error: Could not evaluate expression '" + expression + "'";
        }
    }
}
