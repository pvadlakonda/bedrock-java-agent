package com.example.agent.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import software.amazon.awssdk.core.document.Document;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts Jackson JsonNode objects to AWS SDK Document objects.
 * Required because the Bedrock Converse API uses Document for tool input schemas.
 */
public final class DocumentConverter {

    private DocumentConverter() {}

    /**
     * Converts a Jackson JsonNode to an AWS SDK Document.
     */
    public static Document toDocument(JsonNode node) {
        if (node == null || node.isNull()) {
            return Document.fromNull();
        }
        if (node.isBoolean()) {
            return Document.fromBoolean(node.booleanValue());
        }
        if (node.isNumber()) {
            return Document.fromNumber(node.decimalValue().toPlainString());
        }
        if (node.isTextual()) {
            return Document.fromString(node.textValue());
        }
        if (node.isArray()) {
            List<Document> list = new ArrayList<>();
            for (JsonNode element : (ArrayNode) node) {
                list.add(toDocument(element));
            }
            return Document.fromList(list);
        }
        if (node.isObject()) {
            Map<String, Document> map = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry ->
                    map.put(entry.getKey(), toDocument(entry.getValue())));
            return Document.fromMap(map);
        }
        // Fallback for any other node type
        return Document.fromString(node.toString());
    }

    /**
     * Converts an AWS SDK Document back to a Jackson ObjectNode.
     * Used to parse tool inputs received from the model.
     */
    public static ObjectNode documentToObjectNode(Document document, com.fasterxml.jackson.databind.ObjectMapper mapper) {
        // The Document.toString() produces valid JSON — parse it back with Jackson
        try {
            return (ObjectNode) mapper.readTree(document.toString());
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }
}
