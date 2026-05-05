# Requirements Document

## Introduction

This document describes the requirements for a Java-based AI Agent that integrates with Amazon Bedrock. The agent uses the Bedrock Converse API to interact with Claude 3 Haiku, supports tool use (function calling), retrieves contextual information from a Bedrock Knowledge Base backed by S3 documents (RAG), and maintains conversation memory across turns. Users interact with the agent through a CLI chat interface.

## Glossary

- **Agent**: The `BedrockAgent` component responsible for orchestrating the agentic loop, conversation history, and model interactions.
- **Agentic_Loop**: The iterative process in which the Agent calls the model, executes any requested tools, feeds results back, and repeats until the model produces a final text response.
- **Bedrock_Client**: The AWS SDK v2 `BedrockRuntimeClient` used to call the Bedrock Converse API.
- **Converse_API**: The Amazon Bedrock Converse API endpoint that supports multi-turn conversation and native tool use.
- **Tool**: A named, executable capability (implementing the `Tool` interface) that the model may invoke during the Agentic_Loop.
- **Tool_Registry**: The `ToolRegistry` component that stores and provides access to all registered Tools.
- **Knowledge_Base**: An Amazon Bedrock Knowledge Base backed by S3 documents, used for Retrieval-Augmented Generation (RAG).
- **KB_Service**: The `KnowledgeBaseService` component responsible for querying the Knowledge_Base and returning relevant passages.
- **Document_Converter**: The `DocumentConverter` utility that bridges Jackson `JsonNode` objects and AWS SDK `Document` objects.
- **Config**: The `AgentConfig` component that loads configuration from `config.properties` at startup.
- **CLI**: The command-line interface chat loop implemented in `Main`, through which users interact with the Agent.
- **System_Prompt**: A configurable instruction string prepended to every Bedrock Converse API request to define the Agent's persona and behavior.
- **Conversation_History**: The ordered list of `Message` objects maintained by the Agent across all turns in a session.
- **S3_File_Reader**: The `s3_file_reader` Tool that reads text files from Amazon S3 and returns their contents.
- **Calculator**: The `calculator` Tool that evaluates mathematical expressions and returns numeric results.
- **Time_Tool**: The `get_current_time` Tool that returns the current date and time for a given timezone.
- **Weather_Tool**: The `get_current_weather` Tool that returns current weather conditions for a given city using the Open-Meteo API.

---

## Requirements

### Requirement 1: Configuration Loading

**User Story:** As a developer, I want the agent to load all runtime configuration from a properties file, so that I can change settings without recompiling the application.

#### Acceptance Criteria

1. THE Config SHALL load configuration from `config.properties` on the classpath at startup.
2. IF `config.properties` is not found on the classpath, THEN THE Config SHALL throw an `IllegalStateException` with a descriptive message.
3. THE Config SHALL provide the AWS region, Bedrock model ID, max tokens, Knowledge Base ID, Knowledge Base result count, S3 default bucket name, and System_Prompt as typed accessors.
4. THE Config SHALL use default values for all properties when they are not present in `config.properties`.
5. WHEN the Knowledge Base ID property equals `YOUR_KNOWLEDGE_BASE_ID` or is blank, THE Config SHALL report the Knowledge_Base as not configured.
6. WHEN the S3 bucket property equals `YOUR_S3_BUCKET_NAME` or is blank, THE Config SHALL report S3 as not configured.

---

### Requirement 2: Tool Interface and Registry

**User Story:** As a developer, I want a consistent interface for defining tools and a registry to manage them, so that new tools can be added without modifying the core agent logic.

#### Acceptance Criteria

1. THE Tool_Registry SHALL store Tool instances indexed by their unique name.
2. WHEN a Tool is registered, THE Tool_Registry SHALL make it retrievable by its exact name.
3. WHEN a Tool name is requested that has not been registered, THE Tool_Registry SHALL return null.
4. THE Tool interface SHALL require each Tool to declare a unique name, a human-readable description, a JSON Schema `ObjectNode` for its input parameters, and an `execute` method that accepts a parsed `ObjectNode` and returns a plain-text result string.
5. THE Tool_Registry SHALL expose all registered Tools as a collection for use when building the Bedrock tool configuration.

---

### Requirement 3: Document Conversion

**User Story:** As a developer, I want a utility that converts between Jackson `JsonNode` and AWS SDK `Document` types, so that tool schemas and tool inputs can be exchanged with the Bedrock Converse API.

#### Acceptance Criteria

1. THE Document_Converter SHALL convert a Jackson `JsonNode` of type boolean, number, string, array, object, or null to the corresponding AWS SDK `Document` type.
2. THE Document_Converter SHALL convert an AWS SDK `Document` back to a Jackson `ObjectNode` by serializing the Document to its JSON string representation and parsing it with Jackson.
3. FOR ALL valid Jackson `ObjectNode` values, converting to `Document` then back to `ObjectNode` SHALL produce an equivalent JSON structure (round-trip property).
4. IF a `JsonNode` type is not explicitly handled, THEN THE Document_Converter SHALL fall back to converting the node's string representation to a `Document.fromString`.

---

### Requirement 4: Knowledge Base Retrieval

**User Story:** As a user, I want the agent to automatically retrieve relevant passages from my S3-backed Knowledge Base before answering, so that responses are grounded in my documents.

#### Acceptance Criteria

1. WHEN the Knowledge_Base is configured and a user query is received, THE KB_Service SHALL query the Knowledge_Base using the Bedrock Agent Runtime `RetrieveRequest` and return the top-N matching passages, where N is the configured result count.
2. WHEN the Knowledge_Base is not configured, THE KB_Service SHALL return an empty string without making any API call.
3. WHEN the Knowledge_Base returns results, THE KB_Service SHALL format them as a labeled context block including a source filename extracted from the S3 URI of each result.
4. WHEN the Knowledge_Base returns no results for a query, THE KB_Service SHALL return an empty string.
5. IF the Knowledge_Base API call fails, THEN THE KB_Service SHALL log the error and return an empty string, allowing the Agent to continue without KB context.

---

### Requirement 5: Agentic Loop and Tool Execution

**User Story:** As a user, I want the agent to automatically call tools when needed and incorporate their results into its response, so that I receive accurate, grounded answers.

#### Acceptance Criteria

1. WHEN a user message is received, THE Agent SHALL prepend any retrieved KB context to the user message before adding it to the Conversation_History.
2. THE Agent SHALL call the Converse_API with the full Conversation_History, the System_Prompt, the configured max tokens, and the tool definitions from the Tool_Registry on every turn.
3. WHEN the Converse_API returns a stop reason of `TOOL_USE`, THE Agent SHALL extract all tool use blocks from the response, execute each named Tool via the Tool_Registry, and add the results to the Conversation_History as a user-role message before calling the Converse_API again.
4. WHEN a tool name requested by the model is not found in the Tool_Registry, THE Agent SHALL return an error string to the model indicating the tool is unavailable, without throwing an exception.
5. WHEN a Tool's `execute` method throws an exception, THE Agent SHALL catch it, log the error, and return an error string to the model containing the tool name and exception message.
6. WHEN the Converse_API returns a stop reason of `END_TURN` or `MAX_TOKENS`, THE Agent SHALL extract the text content from the response and return it as the final answer.
7. WHEN the Agentic_Loop reaches 10 iterations without a final text response, THE Agent SHALL return a fixed fallback message and stop iterating.
8. IF the Converse_API call throws an exception, THEN THE Agent SHALL wrap it in a `RuntimeException` with a descriptive message and propagate it to the caller.

---

### Requirement 6: get_current_time Tool

**User Story:** As a user, I want to ask the agent for the current time in any timezone, so that I can get accurate time information without leaving the chat.

#### Acceptance Criteria

1. WHEN the `get_current_time` tool is invoked with a valid IANA timezone name, THE Time_Tool SHALL return the current date and time formatted as `"EEEE, MMMM d, yyyy 'at' HH:mm:ss z"` in that timezone.
2. WHEN the `get_current_time` tool is invoked without a timezone parameter or with a blank value, THE Time_Tool SHALL return the current date and time in UTC.
3. WHEN the `get_current_time` tool is invoked with an unrecognized timezone name, THE Time_Tool SHALL return an error string identifying the invalid timezone.

---

### Requirement 7: calculator Tool

**User Story:** As a user, I want to ask the agent to evaluate mathematical expressions, so that I can get accurate numeric results within the conversation.

#### Acceptance Criteria

1. WHEN the `calculator` tool is invoked with a valid mathematical expression containing digits, operators (`+`, `-`, `*`, `/`, `%`), parentheses, decimal points, and scientific notation characters, THE Calculator SHALL evaluate the expression and return the numeric result prefixed with `"Result: "`.
2. WHEN the `calculator` tool is invoked with an expression containing characters outside the allowed set, THE Calculator SHALL return an error string without evaluating the expression.
3. WHEN the `calculator` tool is invoked without the `expression` parameter, THE Calculator SHALL return an error string indicating the parameter is required.
4. WHEN the `calculator` tool is invoked with an expression that causes an evaluation error (e.g., syntax error), THE Calculator SHALL return an error string containing the original expression and the error message.

---

### Requirement 8: s3_file_reader Tool

**User Story:** As a user, I want the agent to read specific files from S3 on my behalf, so that I can ask questions about file contents without downloading them manually.

#### Acceptance Criteria

1. WHEN the `s3_file_reader` tool is invoked with a valid S3 key and an accessible bucket, THE S3_File_Reader SHALL retrieve the object, read it as UTF-8 text, and return its contents prefixed with the full S3 URI.
2. WHEN the retrieved file content exceeds 4000 characters, THE S3_File_Reader SHALL truncate the content at 4000 characters and append a truncation notice.
3. WHEN the `s3_file_reader` tool is invoked with a key containing `..`, THE S3_File_Reader SHALL return an error string rejecting the path traversal attempt without making any S3 API call.
4. WHEN the `s3_file_reader` tool is invoked without the `key` parameter, THE S3_File_Reader SHALL return an error string indicating the parameter is required.
5. WHEN the requested S3 object does not exist, THE S3_File_Reader SHALL return an error string identifying the missing S3 URI.
6. WHEN S3 is not configured and no `bucket` parameter is provided, THE S3_File_Reader SHALL return an error string instructing the user to configure the S3 bucket.
7. IF the S3 API call fails for any reason other than a missing key, THEN THE S3_File_Reader SHALL log the error and return an error string containing the exception message.
8. WHEN the `s3_file_reader` tool is invoked with an explicit `bucket` parameter, THE S3_File_Reader SHALL use that bucket instead of the configured default.

---

### Requirement 11: get_current_weather Tool

**User Story:** As a user, I want to ask the agent for the current weather in any city, so that I can get up-to-date weather information without leaving the chat.

#### Acceptance Criteria

1. WHEN the `get_current_weather` tool is invoked with a valid city name, THE Weather_Tool SHALL return current conditions including temperature, feels-like temperature, humidity, wind speed, wind direction, and a human-readable weather description.
2. WHEN the `get_current_weather` tool is invoked without the `city` parameter or with a blank value, THE Weather_Tool SHALL return an error string indicating the parameter is required.
3. WHEN the city name cannot be resolved to coordinates, THE Weather_Tool SHALL return an error string identifying the unrecognised city.
4. IF the geocoding or weather API call fails, THEN THE Weather_Tool SHALL log the error and return an error string containing the exception message.

**User Story:** As a user, I want the agent to remember what was said earlier in our conversation, so that I can ask follow-up questions without repeating context.

#### Acceptance Criteria

1. THE Agent SHALL maintain a Conversation_History of all user and assistant messages for the duration of a session.
2. WHEN the Agent sends a request to the Converse_API, THE Agent SHALL include the full Conversation_History in the request.
3. WHEN the `/reset` command is issued via the CLI, THE Agent SHALL clear the Conversation_History so that the next turn starts a fresh session.
4. THE Agent SHALL expose the current length of the Conversation_History as an integer accessor.

---

### Requirement 10: CLI Chat Interface

**User Story:** As a user, I want an interactive command-line interface to chat with the agent, so that I can send messages and receive responses in a simple terminal session.

#### Acceptance Criteria

1. WHEN the CLI starts, THE CLI SHALL display the agent's configuration status including the AWS region, model ID, Knowledge Base status, and S3 bucket status.
2. WHEN the user enters a non-empty, non-command message, THE CLI SHALL send it to the Agent and print the response prefixed with `"Agent: "`.
3. WHEN the user enters `/reset` or `/clear`, THE CLI SHALL call `Agent.resetConversation()` and print a confirmation message.
4. WHEN the user enters `/quit`, `/exit`, or `/q`, THE CLI SHALL exit the chat loop and terminate the program.
5. WHEN the user enters `/help` or `/?`, THE CLI SHALL print the list of available commands and the list of available tools.
6. WHEN the user enters an unrecognized slash command, THE CLI SHALL print an error message identifying the unknown command.
7. WHEN the Agent throws an exception during a chat turn, THE CLI SHALL print the error message to stderr and continue the chat loop without exiting.
8. WHEN the user enters an empty line, THE CLI SHALL ignore it and prompt again without sending a message to the Agent.
