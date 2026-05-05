package com.example.agent.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.document.Document;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DocumentConverter.
 * Validates: Requirements 3.1, 3.4
 */
class DocumentConverterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // -------------------------------------------------------------------------
    // Boolean node
    // -------------------------------------------------------------------------

    @Test
    void toDocument_booleanTrue_returnsDocumentFromBooleanTrue() {
        JsonNode node = MAPPER.getNodeFactory().booleanNode(true);
        Document result = DocumentConverter.toDocument(node);
        assertEquals(Document.fromBoolean(true), result);
    }

    @Test
    void toDocument_booleanFalse_returnsDocumentFromBooleanFalse() {
        JsonNode node = MAPPER.getNodeFactory().booleanNode(false);
        Document result = DocumentConverter.toDocument(node);
        assertEquals(Document.fromBoolean(false), result);
    }

    // -------------------------------------------------------------------------
    // Number node
    // -------------------------------------------------------------------------

    @Test
    void toDocument_integerNumber_returnsDocumentFromNumber() {
        JsonNode node = MAPPER.getNodeFactory().numberNode(42);
        Document result = DocumentConverter.toDocument(node);
        assertEquals(Document.fromNumber("42"), result);
    }

    @Test
    void toDocument_decimalNumber_returnsDocumentFromNumber() {
        JsonNode node = MAPPER.getNodeFactory().numberNode(new BigDecimal("3.14"));
        Document result = DocumentConverter.toDocument(node);
        assertEquals(Document.fromNumber("3.14"), result);
    }

    @Test
    void toDocument_negativeNumber_returnsDocumentFromNumber() {
        JsonNode node = MAPPER.getNodeFactory().numberNode(-7);
        Document result = DocumentConverter.toDocument(node);
        assertEquals(Document.fromNumber("-7"), result);
    }

    // -------------------------------------------------------------------------
    // String node
    // -------------------------------------------------------------------------

    @Test
    void toDocument_string_returnsDocumentFromString() {
        JsonNode node = MAPPER.getNodeFactory().textNode("hello");
        Document result = DocumentConverter.toDocument(node);
        assertEquals(Document.fromString("hello"), result);
    }

    @Test
    void toDocument_emptyString_returnsDocumentFromEmptyString() {
        JsonNode node = MAPPER.getNodeFactory().textNode("");
        Document result = DocumentConverter.toDocument(node);
        assertEquals(Document.fromString(""), result);
    }

    // -------------------------------------------------------------------------
    // Null node
    // -------------------------------------------------------------------------

    @Test
    void toDocument_nullNode_returnsDocumentFromNull() {
        JsonNode node = MAPPER.getNodeFactory().nullNode();
        Document result = DocumentConverter.toDocument(node);
        assertEquals(Document.fromNull(), result);
    }

    @Test
    void toDocument_javaNull_returnsDocumentFromNull() {
        Document result = DocumentConverter.toDocument(null);
        assertEquals(Document.fromNull(), result);
    }

    // -------------------------------------------------------------------------
    // Array node
    // -------------------------------------------------------------------------

    @Test
    void toDocument_emptyArray_returnsDocumentFromEmptyList() {
        ArrayNode node = MAPPER.createArrayNode();
        Document result = DocumentConverter.toDocument(node);
        assertEquals(Document.fromList(List.of()), result);
    }

    @Test
    void toDocument_arrayWithMixedElements_returnsDocumentFromList() {
        ArrayNode node = MAPPER.createArrayNode();
        node.add("text");
        node.add(10);
        node.add(true);
        node.addNull();

        Document result = DocumentConverter.toDocument(node);

        Document expected = Document.fromList(List.of(
                Document.fromString("text"),
                Document.fromNumber("10"),
                Document.fromBoolean(true),
                Document.fromNull()
        ));
        assertEquals(expected, result);
    }

    @Test
    void toDocument_nestedArray_convertsRecursively() {
        ArrayNode inner = MAPPER.createArrayNode();
        inner.add(1);
        inner.add(2);

        ArrayNode outer = MAPPER.createArrayNode();
        outer.add(inner);

        Document result = DocumentConverter.toDocument(outer);

        Document expected = Document.fromList(List.of(
                Document.fromList(List.of(
                        Document.fromNumber("1"),
                        Document.fromNumber("2")
                ))
        ));
        assertEquals(expected, result);
    }

    // -------------------------------------------------------------------------
    // Object node
    // -------------------------------------------------------------------------

    @Test
    void toDocument_emptyObject_returnsDocumentFromEmptyMap() {
        ObjectNode node = MAPPER.createObjectNode();
        Document result = DocumentConverter.toDocument(node);
        assertEquals(Document.fromMap(Map.of()), result);
    }

    @Test
    void toDocument_objectWithStringField_returnsDocumentFromMap() {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("key", "value");

        Document result = DocumentConverter.toDocument(node);

        Document expected = Document.fromMap(Map.of("key", Document.fromString("value")));
        assertEquals(expected, result);
    }

    @Test
    void toDocument_objectWithMixedFields_convertsAllFields() {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("str", "hello");
        node.put("num", 99);
        node.put("flag", false);
        node.putNull("nothing");

        Document result = DocumentConverter.toDocument(node);

        // Build expected map preserving insertion order
        java.util.Map<String, Document> map = new java.util.LinkedHashMap<>();
        map.put("str", Document.fromString("hello"));
        map.put("num", Document.fromNumber("99"));
        map.put("flag", Document.fromBoolean(false));
        map.put("nothing", Document.fromNull());

        assertEquals(Document.fromMap(map), result);
    }

    @Test
    void toDocument_nestedObject_convertsRecursively() {
        ObjectNode inner = MAPPER.createObjectNode();
        inner.put("x", 1);

        ObjectNode outer = MAPPER.createObjectNode();
        outer.set("inner", inner);

        Document result = DocumentConverter.toDocument(outer);

        Document expected = Document.fromMap(Map.of(
                "inner", Document.fromMap(Map.of("x", Document.fromNumber("1")))
        ));
        assertEquals(expected, result);
    }

    // -------------------------------------------------------------------------
    // Fallback for unhandled node type
    // -------------------------------------------------------------------------

    @Test
    void toDocument_missingNode_fallsBackToDocumentFromString() {
        // MissingNode is a special node type not handled by any explicit branch
        JsonNode missingNode = MAPPER.getNodeFactory().missingNode();
        Document result = DocumentConverter.toDocument(missingNode);
        // Fallback: Document.fromString(node.toString())
        assertEquals(Document.fromString(missingNode.toString()), result);
    }
}
