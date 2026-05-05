package com.example.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Tool: get_current_time
 * Returns the current date and time in a requested timezone (defaults to UTC).
 */
public class GetCurrentTimeTool implements Tool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String getName() {
        return "get_current_time";
    }

    @Override
    public String getDescription() {
        return "Returns the current date and time. Optionally accepts a timezone (e.g. 'America/New_York', 'Europe/London'). Defaults to UTC.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");
        ObjectNode timezone = properties.putObject("timezone");
        timezone.put("type", "string");
        timezone.put("description", "IANA timezone name, e.g. 'America/New_York'. Defaults to UTC.");

        // timezone is optional — no required array
        return schema;
    }

    @Override
    public String execute(ObjectNode input) {
        String tz = "UTC";
        if (input.has("timezone") && !input.get("timezone").asText().isBlank()) {
            tz = input.get("timezone").asText();
        }

        try {
            ZoneId zoneId = ZoneId.of(tz);
            ZonedDateTime now = ZonedDateTime.now(zoneId);
            String formatted = now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' HH:mm:ss z"));
            return "Current time in " + tz + ": " + formatted;
        } catch (Exception e) {
            return "Error: Unknown timezone '" + tz + "'. Please use a valid IANA timezone name.";
        }
    }
}
