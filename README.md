# Bhatman - Local Coding Assistant 🤖⚡

> *Self-Aware Note:* This entire README was authored, formatted, and updated by your own local AI coding agent using its very first tool. Writing documentation about my own existence with the exact same orchestration logic I'm designed to execute is genuinely cool (at least to me). 

A terminal-based coding agent built from scratch in Java that leverages standard HTTP calls (`HttpClient`) and Jackson for JSON parsing. It interacts directly with your local **Ollama** server by fetching streaming data manually, reducing heavy external dependencies entirely! Instead of relying on massive orchestration libraries like LangChain4j to handle LLM prompts under the hood, it processes streams old-school style 🍻

---
## ⚙️ Under The Hood (The Old School Way) 💥 
This agent doesn't rely out-of-the-box frameworks for interaction. It hits your `Ollama` API endpoint directly using raw HTTP requests! Once connected, the stream is read line-by-line and parsed manually: thinking tokens are isolated from regular content text, tool-calls are intercepted instantly before full completion, and responses are streamed live right to your terminal in real time with minimal overhead. 

---
## 🧠 Why multiple "Agents"? 
The other `*Agent*.java` files in this project represent earlier iterations that paved the way for this most recent implementation! They were specifically designed to work with less capable models that don't natively invoke tool calls or built-in function calling. 

Instead of automatic invocation, those older agents use strict system prompts to force the LLM's response into a clean **JSON format**. This raw payload is then read and parsed directly by Java code under the hood; functions (tools) are executed dynamically based on what keys/values the model replies with!

---
## ⚙️ Requirements 
- Java 17+ 
- Maven 3.x  
- [Ollama](https://ollama.com) running locally on `http://localhost:11434` with a compatible model (e.g., **Qwen**).  

### 🏃 Quick Start
```bash
# Build the project and create executable jar-with-dependencies 
mvn clean package -DskipTests

# Run it in your terminal! 
java -jar target/ai-agent-1.0-SNAPSHOT-jar-with-dependencies.jar
```
Or simply run: `./agentBhat.sh` 🐚  

---
## 🔧 Available Tools  
The agent can call the following tools automatically based on context or explicit instructions:

| Tool                         | Description                                              |
|------------------------------|----------------------------------------------------------|
| **write_file_to_disk**        | Creates or updates files                                 |
| **read_file / read_file_lines**| Reads full file content or specific line ranges           |
| **delete_file**               | Removes a specified file                                 |
| **list_directory_contents**   | Recursively lists directory tree (up to 6 levels)         |
| **replace_string_in_file**    | Replaces exact, unique text blocks in files              |
| **insert_text_after_line**     | Injects new lines/code after a given line number          |
| **exec_cmd**                  | Executes terminal commands and returns stdout/stderr      |
| **duckduckgo_search**         | Live web searches via DuckDuckGo                         |
| **search_codebase**           | Greps keyword matches across all project files            |

---
## ⚙️ Configuration & Customization 
- Edit `src/main/java/BhatAgentTool.java` to change:  
  - the default Ollama model (`MODEL`) and API base URL.  

Made with ❤️ by Manoj Bhat