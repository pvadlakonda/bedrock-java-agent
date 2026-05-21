# Bedrock AI Agent (Java)

A Java-based AI Agent that uses **Amazon Bedrock** (Claude 3 Haiku) with:
- **Tool use** — calculator, current time, weather, S3 file reader
- **Knowledge Base (RAG)** — retrieves context from your S3 documents via Bedrock Knowledge Base
- **Conversation memory** — maintains history across turns in a session
- **Guardrails** — content filtering, PII redaction, and topic controls via Bedrock Guardrails

---

## Prerequisites

- Java 21+
- Maven 3.8+
- AWS account with Bedrock access enabled
- AWS credentials configured (`~/.aws/credentials` or environment variables)

### Enable Bedrock Model Access

1. Go to **AWS Console → Bedrock → Model access**
2. Request access to **Amazon Nova Pro**
3. Wait for approval (usually instant)

---

## Quick Start

### 1. Configure

Edit `src/main/resources/config.properties`:

```properties
aws.region=us-east-1
bedrock.model.id=anthropic.claude-3-haiku-20240307-v1:0

# Optional — set after creating a Knowledge Base
bedrock.knowledge.base.id=YOUR_KB_ID

# Optional — for the S3 file reader tool
s3.default.bucket=your-bucket-name

# Optional — set after creating a Guardrail
bedrock.guardrail.id=YOUR_GUARDRAIL_ID
bedrock.guardrail.version=DRAFT
```

### 2. Build

```bash
mvn package -q
```

### 3. Run

```bash
java -jar target/bedrock-agent-1.0-SNAPSHOT.jar
```

---

## Setting Up a Knowledge Base (RAG from S3)

1. **Create an S3 bucket** and upload your documents (PDF, TXT, DOCX, etc.)

2. **Create a Knowledge Base** in the AWS Console:
   - Go to **Bedrock → Knowledge Bases → Create**
   - Choose your S3 bucket as the data source
   - Select an embedding model (Amazon Titan Embeddings v2 — cheapest)
   - Choose a vector store (OpenSearch Serverless — AWS manages it)
   - Click **Create and Sync**

3. **Copy the Knowledge Base ID** (looks like `ABCDEF1234`)

4. **Set it in config.properties**:
   ```properties
   bedrock.knowledge.base.id=ABCDEF1234
   ```

Now when you ask questions, the agent will automatically retrieve relevant passages from your documents.

---

## Setting Up Guardrails

Bedrock Guardrails evaluate both user inputs and model responses against your configured policies before your code sees them.

**What you can enforce:**
- **Content filters** — block hate speech, violence, sexual content, and insults with configurable thresholds (None / Low / Medium / High)
- **Denied topics** — define topics the agent must never discuss (e.g. "competitor products", "legal advice")
- **Word/phrase blocklist** — exact words or phrases to always block
- **PII redaction** — automatically detect and mask names, emails, phone numbers, SSNs, credit card numbers, etc.
- **Grounding checks** — ensure responses are grounded in the provided context (useful with RAG)

### Steps

1. **Create a Guardrail** in the AWS Console:
   - Go to **Bedrock → Guardrails → Create guardrail**
   - Configure the policies you need
   - Click **Create guardrail** and note the **Guardrail ID** (looks like `abc123def456`)

2. **Set it in `config.properties`**:
   ```properties
   bedrock.guardrail.id=abc123def456
   bedrock.guardrail.version=DRAFT
   ```
   Once you're happy with the guardrail, publish a numbered version in the console and switch to e.g. `bedrock.guardrail.version=1`.

3. **That's it** — the agent attaches the guardrail to every `ConverseRequest` automatically. When a guardrail blocks a response, Bedrock replaces it with a canned message and the agent surfaces that to the user.

> Leave `bedrock.guardrail.id=YOUR_GUARDRAIL_ID` to disable guardrails entirely.

---

## Example Conversations

```
You: What time is it in Tokyo?
Agent: The current time in Tokyo (Asia/Tokyo) is Wednesday, April 29, 2026 at 14:32:15 JST

You: What is (1500 * 0.08) + 1500?
Agent: Let me calculate that for you. (1500 * 0.08) + 1500 = 1620.0

You: What's the weather like in Paris?
Agent: Current weather in Paris:
  Conditions:    Partly cloudy
  Temperature:   18.4°C (feels like 17.1°C)
  Humidity:      62%
  Wind:          14.0 km/h from the SW

You: Read the file reports/q1-summary.txt from S3
Agent: [reads and returns the file contents]

You: What does our refund policy say?  ← answered from Knowledge Base
Agent: Based on the documents in the knowledge base, your refund policy states...
```

---

## Project Structure

```
bedrock-agent/
├── pom.xml
├── src/main/
│   ├── java/com/example/agent/
│   │   ├── Main.java                          # CLI entry point
│   │   ├── BedrockAgent.java                  # Core agent loop + guardrail wiring
│   │   ├── config/
│   │   │   └── AgentConfig.java               # Config loader (incl. guardrail settings)
│   │   ├── tools/
│   │   │   ├── Tool.java                      # Tool interface
│   │   │   ├── ToolRegistry.java              # Tool registry
│   │   │   ├── GetCurrentTimeTool.java        # Time tool
│   │   │   ├── CalculatorTool.java            # Math tool
│   │   │   ├── WeatherTool.java               # Weather tool (Open-Meteo, no API key)
│   │   │   └── S3FileReaderTool.java          # S3 reader tool
│   │   └── knowledge/
│   │       └── KnowledgeBaseService.java      # KB retrieval (RAG)
│   └── resources/
│       ├── config.properties                  # Configuration (region, model, KB, guardrail)
│       └── logback.xml                        # Logging config
```

---

## Adding a Custom Tool

1. Implement the `Tool` interface:

```java
public class MyTool implements Tool {
    @Override public String getName() { return "my_tool"; }
    @Override public String getDescription() { return "Does something useful"; }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        props.putObject("input").put("type", "string").put("description", "The input");
        schema.putArray("required").add("input");
        return schema;
    }

    @Override
    public String execute(ObjectNode input) {
        String value = input.get("input").asText();
        return "Processed: " + value;
    }
}
```

2. Register it in `Main.java`:

```java
toolRegistry.register(new MyTool());
```

That's it — the agent will automatically use it when appropriate.

---

## AWS Permissions Required

Your IAM user/role needs:

```json
{
  "Effect": "Allow",
  "Action": [
    "bedrock:InvokeModel",
    "bedrock:Converse",
    "bedrock:ApplyGuardrail",
    "bedrock-agent-runtime:Retrieve",
    "s3:GetObject",
    "s3:ListBucket"
  ],
  "Resource": "*"
}
```

---

## Cost Estimate (Amazon Nova Pro)

| Operation | Cost |
|-----------|------|
| Input tokens | $0.00025 / 1K tokens |
| Output tokens | $0.00125 / 1K tokens |
| KB retrieval | ~$0.0004 / query |
| Guardrail (text units) | ~$0.00075 / text unit |
| Weather tool | Free (Open-Meteo, no API key) |

A typical conversation turn costs **less than $0.001**.
