# Implementation Plan: Bedrock Java Agent — Test Suite

## Overview

The production source code is fully implemented. This plan covers adding test dependencies to `pom.xml` and writing the complete test suite: property-based tests (jqwik), unit tests (JUnit 5 + Mockito), and integration tests. All tasks build incrementally toward a fully green `mvn test` run.

## Tasks

- [x] 1. Add test dependencies to pom.xml
  - Add `jqwik 1.8.4` with `<scope>test</scope>`
  - Add `junit-jupiter 5.10.2` with `<scope>test</scope>`
  - Add `mockito-core 5.11.0` with `<scope>test</scope>`
  - Add `mockito-junit-jupiter 5.11.0` with `<scope>test</scope>`
  - Configure `maven-surefire-plugin` (version 3.2.5) to include jqwik's `@Property` tests alongside JUnit 5 tests
  - _Requirements: all test requirements depend on this_

- [x] 2. Write property-based tests for ToolRegistry
  - [x] 2.1 Create `src/test/java/com/example/agent/tools/ToolRegistryPropertyTest.java`
    - Implement the test class using jqwik `@Property` annotations
    - _Requirements: 2.1, 2.2, 2.5_

  - [x] 2.2 Write property test for Tool Registry Round-Trip (Property 1)
    - **Property 1: Tool Registry Round-Trip**
    - Generate arbitrary sets of tools with distinct names; register all; assert each is retrievable by exact name and `getAll()` returns exactly those tools
    - Tag: `// Feature: bedrock-java-agent, Property 1: Tool registry round-trip`
    - **Validates: Requirements 2.1, 2.2, 2.5**

  - [x] 2.3 Write property test for Tool Registry Null for Unknown Names (Property 2)
    - **Property 2: Tool Registry Returns Null for Unknown Names**
    - Generate a registry with N registered tools and an arbitrary name not in the registered set; assert `get(name)` returns `null`
    - Tag: `// Feature: bedrock-java-agent, Property 2: Null for unknown names`
    - **Validates: Requirements 2.3**

- [x] 3. Write unit tests for ToolRegistry
  - [x] 3.1 Create `src/test/java/com/example/agent/tools/ToolRegistryTest.java`
    - Test duplicate registration throws `IllegalArgumentException`
    - Test `get()` on empty registry returns null
    - Test `getAll()` on empty registry returns empty collection
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_

- [x] 4. Write property-based tests for DocumentConverter
  - [x] 4.1 Create `src/test/java/com/example/agent/util/DocumentConverterPropertyTest.java`
    - Implement the test class using jqwik `@Property` annotations

  - [x] 4.2 Write property test for DocumentConverter Round-Trip (Property 3)
    - **Property 3: DocumentConverter Round-Trip**
    - Generate arbitrary `ObjectNode` values (nested objects, arrays, strings, numbers, booleans, nulls); convert to `Document` via `toDocument()` then back via `documentToObjectNode()`; assert JSON equivalence
    - Tag: `// Feature: bedrock-java-agent, Property 3: ObjectNode round-trip`
    - **Validates: Requirements 3.1, 3.2, 3.3**

- [x] 5. Write unit tests for DocumentConverter
  - [x] 5.1 Create `src/test/java/com/example/agent/util/DocumentConverterTest.java`
    - Test each `JsonNode` type individually: boolean, number, string, array, object, null
    - Test fallback for unhandled node type returns `Document.fromString`
    - _Requirements: 3.1, 3.4_

- [x] 6. Checkpoint — Ensure all tests pass so far
  - Run `mvn test -pl . -Dtest="ToolRegistryTest,ToolRegistryPropertyTest,DocumentConverterTest,DocumentConverterPropertyTest"` and confirm green
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. Write unit tests for AgentConfig
  - [x] 7.1 Create `src/test/java/com/example/agent/config/AgentConfigTest.java`
    - Test successful load from a test `config.properties` on the classpath
    - Test `IllegalStateException` when `config.properties` is absent
    - Test all default values are returned when properties are missing
    - Test `isKnowledgeBaseConfigured()` returns `false` for blank and `YOUR_KNOWLEDGE_BASE_ID`
    - Test `isS3Configured()` returns `false` for blank and `YOUR_S3_BUCKET_NAME`
    - Test `isKnowledgeBaseConfigured()` and `isS3Configured()` return `true` for real values
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6_

- [x] 8. Write property-based tests for KnowledgeBaseService
  - [x] 8.1 Create `src/test/java/com/example/agent/knowledge/KnowledgeBaseServicePropertyTest.java`
    - Use Mockito to mock `BedrockAgentRuntimeClient`; inject via a test constructor or reflection

  - [x] 8.2 Write property test for KB Context Formatting (Property 4)
    - **Property 4: Knowledge Base Context Formatting**
    - Generate arbitrary non-empty lists of `KnowledgeBaseRetrievalResult` objects with S3 URIs; assert the formatted context string contains the filename extracted from each URI
    - Tag: `// Feature: bedrock-java-agent, Property 4: KB context formatting`
    - **Validates: Requirements 4.3**

- [x] 9. Write unit tests for KnowledgeBaseService
  - [x] 9.1 Create `src/test/java/com/example/agent/knowledge/KnowledgeBaseServiceTest.java`
    - Test returns empty string when KB is not configured (no API call made)
    - Test returns empty string when KB returns zero results
    - Test returns empty string when KB API call throws an exception (graceful degradation)
    - _Requirements: 4.2, 4.4, 4.5_

- [x] 10. Write property-based tests for GetCurrentTimeTool
  - [x] 10.1 Create `src/test/java/com/example/agent/tools/GetCurrentTimeToolPropertyTest.java`

  - [x] 10.2 Write property test for get_current_time Output Format (Property 9)
    - **Property 9: get_current_time Output Format**
    - Generate arbitrary valid IANA timezone names (e.g., from `ZoneId.getAvailableZoneIds()`); invoke the tool; assert the result matches the pattern `"EEEE, MMMM d, yyyy 'at' HH:mm:ss z"` and contains the timezone name
    - Tag: `// Feature: bedrock-java-agent, Property 9: Output format for valid timezones`
    - **Validates: Requirements 6.1**

  - [x] 10.3 Write property test for get_current_time Error Contains Invalid Timezone (Property 10)
    - **Property 10: get_current_time Error Contains Invalid Timezone**
    - Generate arbitrary strings that are not valid IANA timezone names; invoke the tool; assert the result is an error string containing the invalid timezone string
    - Tag: `// Feature: bedrock-java-agent, Property 10: Error contains invalid timezone`
    - **Validates: Requirements 6.3**

- [x] 11. Write unit tests for GetCurrentTimeTool
  - [x] 11.1 Create `src/test/java/com/example/agent/tools/GetCurrentTimeToolTest.java`
    - Test invocation without `timezone` parameter returns UTC time
    - Test invocation with blank `timezone` value returns UTC time
    - _Requirements: 6.2_

- [x] 12. Write property-based tests for CalculatorTool
  - [x] 12.1 Create `src/test/java/com/example/agent/tools/CalculatorToolPropertyTest.java`

  - [x] 12.2 Write property test for Calculator Valid Expression Prefix (Property 11)
    - **Property 11: Calculator Valid Expression Prefix**
    - Generate arbitrary expression strings that match `[0-9+\-*/().\s%eE]+`; invoke the tool; assert the result starts with `"Result: "`
    - Tag: `// Feature: bedrock-java-agent, Property 11: Valid expression prefix`
    - **Validates: Requirements 7.1**

  - [x] 12.3 Write property test for Calculator Rejects Unsafe Expressions (Property 12)
    - **Property 12: Calculator Rejects Unsafe Expressions**
    - Generate arbitrary strings containing at least one character outside the allowed set; invoke the tool; assert the result is an error string (does not start with `"Result: "`)
    - Tag: `// Feature: bedrock-java-agent, Property 12: Rejects unsafe expressions`
    - **Validates: Requirements 7.2**

  - [x] 12.4 Write property test for Calculator Error Contains Original Expression (Property 13)
    - **Property 13: Calculator Error Contains Original Expression**
    - Generate expression strings that pass the safety check but cause evaluation errors (e.g., unbalanced parentheses, trailing operators); invoke the tool; assert the error response contains the original expression string
    - Tag: `// Feature: bedrock-java-agent, Property 13: Error contains original expression`
    - **Validates: Requirements 7.4**

- [x] 13. Write unit tests for CalculatorTool
  - [x] 13.1 Create `src/test/java/com/example/agent/tools/CalculatorToolTest.java`
    - Test invocation without `expression` parameter returns error string
    - _Requirements: 7.3_

- [x] 14. Checkpoint — Ensure all tests pass so far
  - Run `mvn test` and confirm all tests written to this point are green
  - Ensure all tests pass, ask the user if questions arise.

- [x] 15. Write property-based tests for S3FileReaderTool
  - [x] 15.1 Create `src/test/java/com/example/agent/tools/S3FileReaderToolPropertyTest.java`
    - Use Mockito to mock `S3Client`; inject via a test constructor or reflection

  - [x] 15.2 Write property test for S3 File Reader Truncation (Property 14)
    - **Property 14: S3 File Reader Truncation**
    - Generate arbitrary file content strings longer than 4000 characters; mock the S3 client to return that content; invoke the tool; assert the content portion is truncated at 4000 characters and the result contains a truncation notice
    - Tag: `// Feature: bedrock-java-agent, Property 14: Content truncation`
    - **Validates: Requirements 8.2**

  - [x] 15.3 Write property test for S3 File Reader Missing Key Error Contains URI (Property 15)
    - **Property 15: S3 File Reader Missing Key Error Contains URI**
    - Generate arbitrary bucket names and key strings (without `..`); mock the S3 client to throw `NoSuchKeyException`; invoke the tool; assert the error response contains `s3://bucket/key`
    - Tag: `// Feature: bedrock-java-agent, Property 15: Missing key error contains URI`
    - **Validates: Requirements 8.5**

  - [x] 15.4 Write property test for S3 File Reader General Error Contains Exception Message (Property 16)
    - **Property 16: S3 File Reader General Error Contains Exception Message**
    - Generate arbitrary exception messages; mock the S3 client to throw a generic `RuntimeException` with that message; invoke the tool; assert the error response contains the exception message
    - Tag: `// Feature: bedrock-java-agent, Property 16: General error contains exception message`
    - **Validates: Requirements 8.7**

- [x] 16. Write unit tests for S3FileReaderTool
  - [x] 16.1 Create `src/test/java/com/example/agent/tools/S3FileReaderToolTest.java`
    - Test path traversal (`..` in key) returns error string without making S3 call
    - Test missing `key` parameter returns error string
    - Test unconfigured S3 with no `bucket` parameter returns error string
    - Test explicit `bucket` parameter overrides the configured default
    - _Requirements: 8.3, 8.4, 8.6, 8.8_

- [x] 17. Write property-based tests for BedrockAgent
  - [x] 17.1 Create `src/test/java/com/example/agent/BedrockAgentPropertyTest.java`
    - Use Mockito to mock `BedrockRuntimeClient`, `KnowledgeBaseService`, and `ToolRegistry`

  - [x] 17.2 Write property test for KB Context Prepended to User Message (Property 5)
    - **Property 5: KB Context Prepended to User Message**
    - Generate arbitrary user message strings and non-empty KB context strings; mock the KB service to return the context; mock Bedrock to return `END_TURN`; invoke `chat()`; assert the first user message in history contains both the KB context and the original user message
    - Tag: `// Feature: bedrock-java-agent, Property 5: KB context prepended to history`
    - **Validates: Requirements 5.1**

  - [x] 17.3 Write property test for Tool Use Results Added to History (Property 6)
    - **Property 6: Tool Use Results Added to History**
    - Generate arbitrary sets of tool use blocks; mock Bedrock to return `TOOL_USE` on the first call then `END_TURN`; assert the history contains a user-role message with a tool result for every tool use block
    - Tag: `// Feature: bedrock-java-agent, Property 6: Tool results added to history`
    - **Validates: Requirements 5.3**

  - [x] 17.4 Write property test for Text Extraction from Assistant Messages (Property 7)
    - **Property 7: Text Extraction from Assistant Messages**
    - Generate arbitrary assistant `Message` objects with one or more text `ContentBlock` entries; mock Bedrock to return those messages with `END_TURN`; assert the returned string equals the concatenation of all text blocks (trimmed)
    - Tag: `// Feature: bedrock-java-agent, Property 7: Text extraction from messages`
    - **Validates: Requirements 5.6**

  - [x] 17.5 Write property test for Conversation History Growth (Property 8)
    - **Property 8: Conversation History Growth**
    - For N successful chat turns (each producing a final text response), assert `getConversationLength()` is at least 2×N after N turns
    - Tag: `// Feature: bedrock-java-agent, Property 8: Conversation history growth`
    - **Validates: Requirements 9.1, 9.4**

- [x] 18. Write unit tests for BedrockAgent
  - [x] 18.1 Create `src/test/java/com/example/agent/BedrockAgentTest.java`
    - Test unknown tool name returns error string to model (not an exception)
    - Test tool `execute()` throwing an exception returns error string to model
    - Test agentic loop hitting 10 iterations returns the fixed fallback message
    - Test Bedrock API exception is wrapped in `RuntimeException` and propagated
    - Test `resetConversation()` clears history to zero length
    - _Requirements: 5.4, 5.5, 5.7, 5.8, 9.3_

- [x] 19. Write unit tests for Main
  - [x] 19.1 Create `src/test/java/com/example/agent/MainTest.java`
    - Test `handleCommand("/reset", agent)` calls `resetConversation()` and returns `false`
    - Test `handleCommand("/quit", agent)` returns `true`
    - Test `handleCommand("/exit", agent)` returns `true`
    - Test `handleCommand("/q", agent)` returns `true`
    - Test `handleCommand("/help", agent)` returns `false`
    - Test `handleCommand("/?", agent)` returns `false`
    - Test `handleCommand("/unknown", agent)` returns `false`
    - _Requirements: 10.3, 10.4, 10.5, 10.6_

- [x] 20. Write integration tests
  - [x] 20.1 Create `src/test/java/com/example/agent/BedrockAgentIntegrationTest.java`
    - Mock `BedrockRuntimeClient` at the SDK level using Mockito
    - Verify a full chat turn sends the full conversation history, system prompt, max tokens, and tool definitions in the Converse request
    - _Requirements: 5.2, 9.2_

  - [x] 20.2 Create `src/test/java/com/example/agent/tools/S3FileReaderIntegrationTest.java`
    - Mock `S3Client` to return a known file content
    - Verify the response is prefixed with the full `s3://bucket/key` URI
    - _Requirements: 8.1_

  - [x] 20.3 Create `src/test/java/com/example/agent/knowledge/KnowledgeBaseServiceIntegrationTest.java`
    - Mock `BedrockAgentRuntimeClient` to return N results
    - Verify the formatted context string contains all N source filenames
    - _Requirements: 4.1_

- [x] 21. Final checkpoint — Full build passes
  - Run `mvn test` and confirm all tests (unit, property-based, integration) pass with zero failures
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 22. Add WeatherTool
  - [ ] 22.1 Create `src/main/java/com/example/agent/tools/WeatherTool.java`
    - Implement `Tool` interface with name `get_current_weather`
    - Geocode city via Open-Meteo Geocoding API; fetch current conditions via Open-Meteo Forecast API
    - Return formatted summary: temperature, feels-like, humidity, wind speed/direction, weather description
    - Return error string for missing city, unresolvable city, or API failure
    - Add package-private constructor accepting `HttpClient` for testability
    - _Requirements: 11.1, 11.2, 11.3, 11.4_

  - [ ] 22.2 Register `WeatherTool` in `Main.java`
    - Add `toolRegistry.register(new WeatherTool())` after `CalculatorTool`
    - Update the tools-registered print line and `/help` output
    - _Requirements: 11.1_

- [ ] 23. Write unit tests for WeatherTool
  - [ ] 23.1 Create `src/test/java/com/example/agent/tools/WeatherToolTest.java`
    - Test missing `city` parameter returns error string (Req 11.2)
    - Test blank `city` parameter returns error string (Req 11.2)
    - _Requirements: 11.2_

- [ ] 24. Write property-based tests for WeatherTool
  - [ ] 24.1 Create `src/test/java/com/example/agent/tools/WeatherToolPropertyTest.java`

  - [ ] 24.2 Write property test for Weather Tool Missing City (Property 17)
    - **Property 17: Weather Tool Missing City Parameter**
    - Generate invocations without a `city` field or with a blank value; assert the result is an error string and no HTTP call is made
    - Tag: `// Feature: bedrock-java-agent, Property 17: Missing city parameter returns error`
    - **Validates: Requirements 11.2**

  - [ ] 24.3 Write property test for Weather Tool City Not Found (Property 18)
    - **Property 18: Weather Tool City Not Found**
    - Mock the HttpClient to return an empty geocoding result; generate arbitrary city name strings; assert the result is an error string containing the city name
    - Tag: `// Feature: bedrock-java-agent, Property 18: City not found returns error containing city name`
    - **Validates: Requirements 11.3**

- [ ] 25. Write integration test for WeatherTool
  - [ ] 25.1 Create `src/test/java/com/example/agent/tools/WeatherToolIntegrationTest.java`
    - Mock `HttpClient` to return a realistic geocoding response followed by a realistic weather response
    - Verify the result contains temperature, humidity, and wind fields
    - _Requirements: 11.1_

- [ ] 26. Final checkpoint — Full build passes with WeatherTool
  - Run `mvn test` and confirm all tests pass with zero failures

- [ ] 27. Add Bedrock Guardrails support
  - [x] 27.1 Add guardrail accessors to `AgentConfig`
    - Add `getGuardrailId()` returning `bedrock.guardrail.id` (default `""`)
    - Add `getGuardrailVersion()` returning `bedrock.guardrail.version` (default `"DRAFT"`)
    - Add `isGuardrailConfigured()` returning `false` when ID is blank or equals `YOUR_GUARDRAIL_ID`
    - _Requirements: 12.1, 12.2, 12.3, 12.4_

  - [x] 27.2 Wire guardrail into `BedrockAgent.callBedrock()`
    - Import `GuardrailConfiguration` from the Bedrock Runtime SDK
    - When `config.isGuardrailConfigured()` is true, attach `GuardrailConfiguration` (ID + version) to the `ConverseRequest`
    - Log at DEBUG level when the guardrail is applied
    - _Requirements: 13.1, 13.2_

  - [x] 27.3 Handle `GUARDRAIL_INTERVENED` stop reason in `BedrockAgent.runAgentLoop()`
    - Add a branch for `StopReason.GUARDRAIL_INTERVENED`
    - Log a warning and return `extractTextFromMessage(assistantMessage)` (the guardrail's replacement text)
    - _Requirements: 13.3, 13.4_

  - [x] 27.4 Add guardrail properties to `config.properties`
    - Add `bedrock.guardrail.id=YOUR_GUARDRAIL_ID`
    - Add `bedrock.guardrail.version=DRAFT`
    - Include a comment explaining how to obtain and set the guardrail ID
    - _Requirements: 12.1_

  - [x] 27.5 Update `Main.printConfigStatus()` to display guardrail status
    - Print the guardrail ID and version when configured, or `NOT CONFIGURED` otherwise
    - _Requirements: 10.1_

- [ ] 28. Write unit and integration tests for Guardrails
  - [ ] 28.1 Update `AgentConfigTest` with guardrail accessor tests
    - Test `isGuardrailConfigured()` returns `false` for blank and `YOUR_GUARDRAIL_ID`
    - Test `isGuardrailConfigured()` returns `true` for a real ID
    - Test `getGuardrailVersion()` defaults to `"DRAFT"`
    - _Requirements: 12.1, 12.2, 12.3, 12.4_

  - [ ] 28.2 Update `BedrockAgentTest` with guardrail stop reason test
    - Test that when the mocked Bedrock client returns `GUARDRAIL_INTERVENED`, `chat()` returns the assistant message text without continuing the loop
    - _Requirements: 13.3, 13.4_

  - [ ] 28.3 Update `BedrockAgentIntegrationTest` with guardrail attachment tests
    - **Property 19**: When guardrail is configured, verify every `ConverseRequest` contains a `GuardrailConfiguration` with the correct ID and version
    - **Property 20**: When guardrail is not configured, verify `ConverseRequest` has no `GuardrailConfiguration`
    - _Requirements: 13.1, 13.2_

- [ ] 29. Final checkpoint — Full build passes with Guardrails
  - Run `mvn test` and confirm all tests pass with zero failures

## Notes

- Tasks marked with `*` are optional and can be skipped for a faster MVP
- Each task references specific requirements for traceability
- Property tests use jqwik `@Property` with the default 1000 iterations; each must include the tag comment referencing the design property number
- AWS SDK clients (`BedrockRuntimeClient`, `S3Client`, `BedrockAgentRuntimeClient`) must be injected or accessible for mocking — add package-private or test constructors where needed
- `S3FileReaderTool` and `KnowledgeBaseService` construct their AWS clients internally; a test-friendly constructor accepting a pre-built client should be added to each class to enable mocking without reflection
- Checkpoints at tasks 6, 14, and 21 ensure incremental validation throughout the test suite build
