package com.example.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.jqwik.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for CalculatorTool using jqwik.
 * Feature: bedrock-java-agent
 */
class CalculatorToolPropertyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ObjectNode inputWithExpression(String expression) {
        ObjectNode input = MAPPER.createObjectNode();
        input.put("expression", expression);
        return input;
    }

    // ---------------------------------------------------------------------------
    // Property 11: Calculator Valid Expression Prefix
    // Validates: Requirements 7.1
    // ---------------------------------------------------------------------------

    // Feature: bedrock-java-agent, Property 11: Valid expression prefix
    @Property
    void validExpressionPrefix(@ForAll("safeEvaluableExpressions") String expression) {
        CalculatorTool tool = new CalculatorTool();
        String result = tool.execute(inputWithExpression(expression));

        assertTrue(result.startsWith("Result: "),
                "Expected result to start with 'Result: ' for expression '"
                        + expression + "' but got: " + result);
    }

    /**
     * Generates simple numeric expressions that are guaranteed to:
     * 1. Pass the safety regex
     * 2. Evaluate successfully (no syntax errors)
     *
     * Strategy: generate two non-negative integers and a binary operator.
     * Division by zero is avoided by ensuring the right operand is non-zero.
     */
    @Provide
    Arbitrary<String> safeEvaluableExpressions() {
        // Simple integer literals — always safe and always evaluable
        Arbitrary<String> intLiterals = Arbitraries.integers()
                .between(0, 10000)
                .map(Object::toString);

        // "a + b" style expressions
        Arbitrary<String> additions = Combinators.combine(
                Arbitraries.integers().between(0, 10000),
                Arbitraries.integers().between(0, 10000)
        ).as((a, b) -> a + " + " + b);

        // "a - b" style expressions
        Arbitrary<String> subtractions = Combinators.combine(
                Arbitraries.integers().between(0, 10000),
                Arbitraries.integers().between(0, 10000)
        ).as((a, b) -> a + " - " + b);

        // "a * b" style expressions
        Arbitrary<String> multiplications = Combinators.combine(
                Arbitraries.integers().between(0, 10000),
                Arbitraries.integers().between(0, 10000)
        ).as((a, b) -> a + " * " + b);

        // "a / b" style expressions (b != 0 to avoid division by zero)
        Arbitrary<String> divisions = Combinators.combine(
                Arbitraries.integers().between(0, 10000),
                Arbitraries.integers().between(1, 10000)
        ).as((a, b) -> a + " / " + b);

        return Arbitraries.oneOf(intLiterals, additions, subtractions, multiplications, divisions);
    }

    // ---------------------------------------------------------------------------
    // Property 12: Calculator Rejects Unsafe Expressions
    // Validates: Requirements 7.2
    // ---------------------------------------------------------------------------

    // Feature: bedrock-java-agent, Property 12: Rejects unsafe expressions
    @Property
    void rejectsUnsafeExpressions(@ForAll("unsafeExpressions") String expression) {
        CalculatorTool tool = new CalculatorTool();
        String result = tool.execute(inputWithExpression(expression));

        assertFalse(result.startsWith("Result: "),
                "Expected error for unsafe expression '" + expression + "' but got: " + result);
        assertTrue(result.startsWith("Error:"),
                "Expected error string for unsafe expression '" + expression + "' but got: " + result);
    }

    /**
     * Generates strings that contain at least one character outside the allowed set.
     * We inject a known unsafe character into an arbitrary safe prefix/suffix,
     * ensuring the overall string always fails the safety check.
     */
    @Provide
    Arbitrary<String> unsafeExpressions() {
        // Characters that are definitely outside the allowed set [0-9+\-*().\s%eE]
        // Using an index-based approach to avoid any literal character encoding issues
        Arbitrary<Character> unsafeChars = Arbitraries.integers()
                .between(0, 25)
                .map(i -> {
                    // lowercase letters a-z, excluding e (which is in the allowed set)
                    char[] unsafe = {
                        'a', 'b', 'c', 'd', 'f', 'g', 'h', 'i', 'j', 'k',
                        'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u',
                        'v', 'w', 'x', 'y', 'z', '!'
                    };
                    return unsafe[i];
                });

        // Safe prefix: only characters from the allowed set
        Arbitrary<String> safePrefix = Arbitraries.strings()
                .withChars("0123456789+-*/().% ")
                .ofMinLength(0)
                .ofMaxLength(10);

        return Combinators.combine(safePrefix, unsafeChars, safePrefix)
                .as((prefix, unsafeChar, suffix) -> prefix + unsafeChar + suffix);
    }

    // ---------------------------------------------------------------------------
    // Property 13: Calculator Error Contains Original Expression
    // Validates: Requirements 7.4
    // ---------------------------------------------------------------------------

    // Feature: bedrock-java-agent, Property 13: Error contains original expression
    @Property
    void errorContainsOriginalExpression(@ForAll("safeButInvalidExpressions") String expression) {
        CalculatorTool tool = new CalculatorTool();
        String result = tool.execute(inputWithExpression(expression));

        // The expression passes the safety check, so it should not be rejected for unsafe chars
        assertFalse(
                result.equals("Error: Expression contains invalid characters. Only numeric operators are allowed."),
                "Expression '" + expression + "' should pass the safety check");

        // The result should be an error (evaluation failed)
        assertFalse(result.startsWith("Result: "),
                "Expression '" + expression + "' should cause an evaluation error, but got: " + result);

        // The error response must contain the original expression
        assertTrue(result.contains(expression),
                "Error response should contain the original expression '"
                        + expression + "' but got: " + result);
    }

    /**
     * Generates expressions that:
     * 1. Pass the safety regex (only safe characters)
     * 2. Cause evaluation errors (syntax errors, unbalanced parens, trailing operators)
     *
     * Avoids "++" and "--" (valid JS increment/decrement operators).
     * Avoids "**" (valid JS exponentiation operator).
     * Uses trailing "*" or "/" and unbalanced parentheses which reliably fail.
     */
    @Provide
    Arbitrary<String> safeButInvalidExpressions() {
        // Expressions with trailing * or / — reliably cause syntax errors
        Arbitrary<String> trailingMulDiv = Combinators.combine(
                Arbitraries.integers().between(1, 999),
                Arbitraries.of("*", "/")
        ).as((n, op) -> n + op);

        // Expressions with trailing + — reliably causes a syntax error
        Arbitrary<String> trailingPlus = Arbitraries.integers()
                .between(1, 999)
                .map(n -> n + "+");

        // Expressions with leading * or / — reliably cause syntax errors
        Arbitrary<String> leadingMulDiv = Combinators.combine(
                Arbitraries.of("*", "/"),
                Arbitraries.integers().between(1, 999)
        ).as((op, n) -> op + n);

        // Unbalanced opening parentheses (e.g., "(((42") — reliably cause syntax errors
        Arbitrary<String> unbalancedOpen = Combinators.combine(
                Arbitraries.integers().between(2, 5).map(n -> "(".repeat(n)),
                Arbitraries.integers().between(1, 999)
        ).as((parens, n) -> parens + n);

        return Arbitraries.oneOf(trailingMulDiv, trailingPlus, leadingMulDiv, unbalancedOpen);
    }
}
