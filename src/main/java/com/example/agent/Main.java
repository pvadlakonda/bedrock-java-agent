package com.example.agent;

import com.example.agent.config.AgentConfig;
import com.example.agent.knowledge.KnowledgeBaseService;
import com.example.agent.tools.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Scanner;

/**
 * Entry point — starts an interactive CLI chat session with the Bedrock Agent.
 *
 * Usage:
 *   mvn package
 *   java -jar target/bedrock-agent-1.0-SNAPSHOT.jar
 *
 * Commands:
 *   /reset  — clear conversation history and start fresh
 *   /quit   — exit the program
 *   /help   — show available commands
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║        Bedrock AI Agent (Nova Pro)       ║");
        System.out.println("╚══════════════════════════════════════════╝");
        System.out.println();

        // Load configuration
        AgentConfig config = new AgentConfig();
        printConfigStatus(config);

        // Register tools
        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.register(new GetCurrentTimeTool());
        toolRegistry.register(new CalculatorTool());
        toolRegistry.register(new WeatherTool());
        toolRegistry.register(new S3FileReaderTool(config));

        System.out.println("Tools registered: get_current_time, calculator, get_current_weather, s3_file_reader");
        System.out.println();

        // Initialize knowledge base service
        KnowledgeBaseService knowledgeBaseService = new KnowledgeBaseService(config);

        // Create the agent
        BedrockAgent agent = new BedrockAgent(config, toolRegistry, knowledgeBaseService);

        System.out.println("Agent ready! Type your message and press Enter.");
        System.out.println("Commands: /reset, /quit, /help");
        System.out.println("─".repeat(50));
        System.out.println();

        // Start the chat loop
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("You: ");
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) continue;

            // Handle commands
            if (input.startsWith("/")) {
                if (handleCommand(input, agent)) {
                    break; // /quit was entered
                }
                continue;
            }

            // Send message to agent
            try {
                System.out.println();
                System.out.print("Agent: ");
                String response = agent.chat(input);
                System.out.println(response);
                System.out.println();
            } catch (Exception e) {
                System.err.println();
                System.err.println("Error: " + e.getMessage());
                log.error("Agent error", e);
                System.out.println();
            }
        }

        System.out.println("Goodbye!");
        scanner.close();
    }

    /**
     * Handles slash commands. Returns true if the program should exit.
     * Package-private for testing.
     */
    static boolean handleCommand(String command, BedrockAgent agent) {
        switch (command.toLowerCase()) {
            case "/quit", "/exit", "/q" -> {
                return true;
            }
            case "/reset", "/clear" -> {
                agent.resetConversation();
                System.out.println("[Conversation cleared — starting fresh]");
                System.out.println();
            }
            case "/help", "/?" -> {
                System.out.println();
                System.out.println("Available commands:");
                System.out.println("  /reset  — clear conversation history");
                System.out.println("  /quit   — exit the program");
                System.out.println("  /help   — show this help");
                System.out.println();
                System.out.println("Available tools the agent can use:");
                System.out.println("  get_current_time    — ask for the current time in any timezone");
                System.out.println("  calculator          — evaluate math expressions");
                System.out.println("  get_current_weather — get current weather for any city");
                System.out.println("  s3_file_reader      — read files from S3");
                System.out.println();
                System.out.println("Knowledge base: ask questions about your S3 documents");
                System.out.println();
            }
            default -> {
                System.out.println("Unknown command: " + command + ". Type /help for available commands.");
                System.out.println();
            }
        }
        return false;
    }

    /** Prints the current configuration status at startup. */
    private static void printConfigStatus(AgentConfig config) {
        System.out.println("Configuration:");
        System.out.println("  Region:         " + config.getAwsRegion());
        System.out.println("  Model:          " + config.getModelId());
        System.out.println("  Knowledge Base: " + (config.isKnowledgeBaseConfigured()
                ? config.getKnowledgeBaseId()
                : "NOT CONFIGURED (set bedrock.knowledge.base.id in config.properties)"));
        System.out.println("  S3 Bucket:      " + (config.isS3Configured()
                ? config.getS3DefaultBucket()
                : "NOT CONFIGURED (set s3.default.bucket in config.properties)"));
        System.out.println("  Guardrail:      " + (config.isGuardrailConfigured()
                ? config.getGuardrailId() + " (version: " + config.getGuardrailVersion() + ")"
                : "NOT CONFIGURED (set bedrock.guardrail.id in config.properties)"));
        System.out.println();
    }
}
