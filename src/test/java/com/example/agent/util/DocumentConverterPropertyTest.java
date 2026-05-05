package com.example.agent.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.jqwik.api.*;
import software.amazon.awssdk.core.document.Document;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for DocumentConverter using jqwik.
 * Feature: bedrock-java-agent
 */
class DocumentConverterPropertyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ---------------------------------------------------------------------------
    // Property 3: DocumentConverter Round-Trip
    // Validates: Requirements 3.1, 3.2, 3.3
    // ---------------------------------------------------------------------------

    // Feature: bedrock-java-agent, Property 3: ObjectNode round-trip
    @Property
    void documentConverterRoundTrip(@ForAll("objectNodes") ObjectNode original) {
        // Convert ObjectNode -> Document -> ObjectNode
        Document document = DocumentConverter.toDocument(original);
        ObjectNode roundTripped = DocumentConverter.documentToObjectNode(document, MAPPER);

        // Assert JSON equivalence by comparing the serialized JSON strings
        // (ObjectNode.equals() uses reference equality, not structural equality)
        assertEquals(
                original.toString(),
                roundTripped.toString(),
                "Round-trip conversion should produce an equivalent JSON structure.\n"
                        + "Original:     " + original + "\n"
                        + "Round-tripped: " + roundTripped
        );
    }

    // ---------------------------------------------------------------------------
    // Arbitraries / Generators
    // ---------------------------------------------------------------------------

    /**
     * Generates arbitrary ObjectNode values with various value types:
     * strings, numbers, booleans, nulls, nested objects, and arrays.
     */
    @Provide
    Arbitrary<ObjectNode> objectNodes() {
        return objectNodeArbitrary(2);
    }

    /**
     * Recursive generator for ObjectNode with a depth limit to avoid
     * unbounded recursion.
     */
    private Arbitrary<ObjectNode> objectNodeArbitrary(int maxDepth) {
        Arbitrary<String> keys = Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(10);

        // Exclude null characters (\u0000) from strings: the AWS SDK Document
        // serialization does not preserve them through JSON round-trip.
        Arbitrary<String> safeStrings = Arbitraries.strings()
                .withCharRange(' ', '\uD7FF')  // printable BMP chars, no surrogates
                .ofMaxLength(50);

        // Use integers and simple decimals only: the Document round-trip normalizes
        // BigDecimal scientific notation (e.g. 9.9E+5 -> 990000), so we avoid
        // generating values that differ only in representation.
        Arbitrary<Object> leafValues = Arbitraries.oneOf(
                safeStrings.map(s -> (Object) s),
                Arbitraries.integers().map(i -> (Object) BigDecimal.valueOf(i)),
                Arbitraries.of(true, false).map(b -> (Object) b),
                Arbitraries.just(null)
        );

        if (maxDepth <= 0) {
            // At max depth, only generate flat objects with leaf values
            return Combinators.combine(
                    keys.list().ofMinSize(0).ofMaxSize(4),
                    leafValues.list().ofMinSize(0).ofMaxSize(4)
            ).as((keyList, valueList) -> {
                ObjectNode node = MAPPER.createObjectNode();
                int size = Math.min(keyList.size(), valueList.size());
                for (int i = 0; i < size; i++) {
                    putValue(node, keyList.get(i), valueList.get(i));
                }
                return node;
            });
        }

        // With remaining depth, mix leaf values, nested objects, and arrays
        Arbitrary<Object> nestedObject = objectNodeArbitrary(maxDepth - 1).map(o -> (Object) o);
        Arbitrary<Object> nestedArray = arrayNodeArbitrary(maxDepth - 1).map(a -> (Object) a);

        Arbitrary<Object> allValues = Arbitraries.oneOf(leafValues, nestedObject, nestedArray);

        return Combinators.combine(
                keys.list().ofMinSize(0).ofMaxSize(4),
                allValues.list().ofMinSize(0).ofMaxSize(4)
        ).as((keyList, valueList) -> {
            ObjectNode node = MAPPER.createObjectNode();
            int size = Math.min(keyList.size(), valueList.size());
            for (int i = 0; i < size; i++) {
                putValue(node, keyList.get(i), valueList.get(i));
            }
            return node;
        });
    }

    /**
     * Recursive generator for ArrayNode with a depth limit.
     */
    private Arbitrary<ArrayNode> arrayNodeArbitrary(int maxDepth) {
        // Same safe string constraint: exclude null characters
        Arbitrary<String> safeStrings = Arbitraries.strings()
                .withCharRange(' ', '\uD7FF')
                .ofMaxLength(50);

        // Use integers only for numbers (same reason as objectNodeArbitrary)
        Arbitrary<Object> leafValues = Arbitraries.oneOf(
                safeStrings.map(s -> (Object) s),
                Arbitraries.integers().map(i -> (Object) BigDecimal.valueOf(i)),
                Arbitraries.of(true, false).map(b -> (Object) b),
                Arbitraries.just(null)
        );

        Arbitrary<Object> elements = maxDepth <= 0
                ? leafValues
                : Arbitraries.oneOf(
                        leafValues,
                        objectNodeArbitrary(maxDepth - 1).map(o -> (Object) o)
                  );

        return elements.list().ofMinSize(0).ofMaxSize(4).map(items -> {
            ArrayNode array = MAPPER.createArrayNode();
            for (Object item : items) {
                addToArray(array, item);
            }
            return array;
        });
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private void putValue(ObjectNode node, String key, Object value) {
        if (value == null) {
            node.putNull(key);
        } else if (value instanceof String s) {
            node.put(key, s);
        } else if (value instanceof BigDecimal bd) {
            node.put(key, bd);
        } else if (value instanceof Boolean b) {
            node.put(key, b);
        } else if (value instanceof ObjectNode on) {
            node.set(key, on);
        } else if (value instanceof ArrayNode an) {
            node.set(key, an);
        }
    }

    private void addToArray(ArrayNode array, Object value) {
        if (value == null) {
            array.addNull();
        } else if (value instanceof String s) {
            array.add(s);
        } else if (value instanceof BigDecimal bd) {
            array.add(bd);
        } else if (value instanceof Boolean b) {
            array.add(b);
        } else if (value instanceof ObjectNode on) {
            array.add(on);
        } else if (value instanceof ArrayNode an) {
            array.add(an);
        }
    }
}
