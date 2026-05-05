package com.example.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.jqwik.api.*;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for GetCurrentTimeTool using jqwik.
 * Feature: bedrock-java-agent
 */
class GetCurrentTimeToolPropertyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Pattern matching "EEEE, MMMM d, yyyy 'at' HH:mm:ss z"
    // e.g. "Wednesday, July 9, 2025 at 14:30:00 UTC"
    private static final Pattern TIME_PATTERN = Pattern.compile(
            "^[A-Za-z]+, [A-Za-z]+ \\d{1,2}, \\d{4} at \\d{2}:\\d{2}:\\d{2} .+$"
    );

    private ObjectNode inputWithTimezone(String timezone) {
        ObjectNode input = MAPPER.createObjectNode();
        input.put("timezone", timezone);
        return input;
    }

    // ---------------------------------------------------------------------------
    // Property 9: get_current_time Output Format
    // Validates: Requirements 6.1
    // ---------------------------------------------------------------------------

    // Feature: bedrock-java-agent, Property 9: Output format for valid timezones
    @Property
    void outputFormatForValidTimezones(
            @ForAll("validTimezones") String timezone) {

        GetCurrentTimeTool tool = new GetCurrentTimeTool();
        String result = tool.execute(inputWithTimezone(timezone));

        // Result should not be an error
        assertFalse(result.startsWith("Error:"),
                "Expected valid output for timezone '" + timezone + "' but got: " + result);

        // Result should be prefixed with "Current time in <tz>: "
        assertTrue(result.startsWith("Current time in " + timezone + ": "),
                "Result should start with 'Current time in " + timezone + ": ' but was: " + result);

        // Extract the formatted time portion after the prefix
        String prefix = "Current time in " + timezone + ": ";
        String timePart = result.substring(prefix.length());

        // Verify the time portion matches the expected pattern
        assertTrue(TIME_PATTERN.matcher(timePart).matches(),
                "Time portion '" + timePart + "' does not match expected pattern for timezone: " + timezone);

        // Verify the formatted time is parseable with the same formatter
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' HH:mm:ss z", Locale.ENGLISH);
        // If parsing throws, the format is wrong — this will fail the test
        ZonedDateTime parsed = ZonedDateTime.parse(timePart, formatter);
        assertNotNull(parsed, "Parsed time should not be null");
    }

    @Provide
    Arbitrary<String> validTimezones() {
        String[] zones = ZoneId.getAvailableZoneIds().toArray(new String[0]);
        return Arbitraries.of(zones);
    }

    // ---------------------------------------------------------------------------
    // Property 10: get_current_time Error Contains Invalid Timezone
    // Validates: Requirements 6.3
    // ---------------------------------------------------------------------------

    // Feature: bedrock-java-agent, Property 10: Error contains invalid timezone
    @Property
    void errorContainsInvalidTimezone(
            @ForAll("invalidTimezones") String timezone) {

        GetCurrentTimeTool tool = new GetCurrentTimeTool();
        String result = tool.execute(inputWithTimezone(timezone));

        // Result must be an error string
        assertTrue(result.startsWith("Error:"),
                "Expected error for invalid timezone '" + timezone + "' but got: " + result);

        // Error must contain the invalid timezone string
        assertTrue(result.contains(timezone),
                "Error message should contain the invalid timezone '" + timezone + "' but was: " + result);
    }

    @Provide
    Arbitrary<String> invalidTimezones() {
        // Generate arbitrary strings that are not accepted by ZoneId.of()
        // (which accepts IANA names, offset IDs like "Z", "+05:30", etc.)
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .ofMinLength(1)
                .ofMaxLength(30)
                .filter(tz -> {
                    try {
                        ZoneId.of(tz);
                        return false; // valid timezone — exclude it
                    } catch (Exception e) {
                        return true; // invalid timezone — include it
                    }
                });
    }
}
