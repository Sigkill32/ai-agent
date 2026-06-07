import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public class BhatAgentTool {

    public static final String BASE_URL = "http://localhost:11434/api/chat";
    public static final String MODEL = "huihui_ai/Qwen3.6-abliterated:27b";
//    public static final String MODEL = "huihui_ai/qwen2.5-abliterate:32b-instruct-q4_K_M";
//    public static final String MODEL = "huihui_ai/qwen2.5-coder-abliterate:7b";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private static final char[] SPINNER_FRAMES = {'⠋', '⠙', '⠹', '⠸', '⠼', '⠴', '⠦', '⠧', '⠇', '⠏'};

    public record ChatMessage(String role, String content, Object tool_calls, String name) {
        public ChatMessage(String role, String content) {
            this(role, content, null, null);
        }
    }
    public record Tool(String type, FunctionDef function) {}
    public record FunctionDef(String name, String description, Parameters parameters) {}
    public record Parameters(String type, Map<String, Object> properties, List<String> required) {}
    public record ChatRequest(String model, List<ChatMessage> messages, List<Tool> tools, Boolean stream) {}

    public static void main(String[] args) throws IOException {
        List<ChatMessage> chatHistory = new ArrayList<>();
        Scanner scanner = new Scanner(System.in);

        Tool writeFileTool = new Tool("function", new FunctionDef(
                "write_file_to_disk",
                "Creates or updates a local file with the specified content.",
                new Parameters(
                        "object",
                        Map.of(
                                "filename", Map.of("type", "string", "description", "The name of the file, e.g., script.py"),
                                "content", Map.of("type", "string", "description", "The raw code or text content to write inside the file.")
                        ),
                        List.of("filename", "content")
                )
        ));

        Tool commandExecTool = new Tool("function", new FunctionDef(
                "exec_cmd",
                "Runs a command on the terminal with the specified command",
                new Parameters(
                        "object",
                        Map.of(
                                "command", Map.of("type", "string", "description", "The command that needs to be run on the terminal")
                        ),
                        List.of("command")
                )
        ));

        Tool readFileTool = new Tool("function", new FunctionDef(
                "read_file",
                "Reads the file from filesystem from the specified path",
                new Parameters(
                        "object",
                        Map.of(
                                "path", Map.of("type", "string", "description", "The string path to the file to be read")
                        ),
                        List.of("path")
                )
        ));
        Tool deleteFileTool = new Tool("function", new FunctionDef(
                "delete_file",
                "Deletes the file specified in the path from file system",
                new Parameters(
                        "object",
                        Map.of(
                                "path", Map.of("type", "string", "description", "The string path to the file to be deleted")
                        ),
                        List.of("path")
                )
        ));

        Tool listDirectoryContents = new Tool("function", new FunctionDef(
                "list_directory_contents",
                "Recursively lists all the files in the current directory upto the 6 levels in depth",
                new Parameters(
                        "object",
                        Map.of(),
                        List.of()
                )
        ));

        Tool readFileLinesTool = new Tool("function", new FunctionDef(
                "read_file_lines",
                "Reads a specific range of lines from a local file. Use this to inspect code or files without overloading context.",
                new Parameters(
                        "object",
                        Map.of(
                                "filename", Map.of("type", "string", "description", "The name of the file to read, e.g., src/Main.java"),
                                "start_line", Map.of("type", "integer", "description", "The 1-based starting line number (inclusive)."),
                                "end_line", Map.of("type", "integer", "description", "The 1-based ending line number (inclusive).")
                        ),
                        List.of("filename", "start_line", "end_line")
                )
        ));

        Tool replaceStringTool = new Tool("function", new FunctionDef(
                "replace_string_in_file",
                "Surgically replaces an exact, unique block of text or code within a file with a new block of text.",
                new Parameters(
                        "object",
                        Map.of(
                                "filename", Map.of("type", "string", "description", "The path to the file to modify, e.g., src/Main.java"),
                                "old_string", Map.of("type", "string", "description", "The exact block of code/text currently in the file that you want to replace. Make sure it is unique enough to match precisely."),
                                "new_string", Map.of("type", "string", "description", "The new code or text that will replace the old_string.")
                        ),
                        List.of("filename", "old_string", "new_string")
                )
        ));

        Tool insertTextTool = new Tool("function", new FunctionDef(
                "insert_text_after_line",
                "Inserts a new block of text or code into a file immediately after a specified 1-based line number.",
                new Parameters(
                        "object",
                        Map.of(
                                "filename", Map.of("type", "string", "description", "The path to the file to modify, e.g., src/Main.java"),
                                "line_number", Map.of("type", "integer", "description", "The 1-based line number after which the new text will be added."),
                                "text_to_insert", Map.of("type", "string", "description", "The raw text or code block to inject into the file.")
                        ),
                        List.of("filename", "line_number", "text_to_insert")
                )
        ));

        // shit ain't workin. duckduckgo's showing captha. gots to find a workaround.
        Tool ddgSearchTool = new Tool("function", new FunctionDef(
                "duckduckgo_search",
                "Searches the web using DuckDuckGo for live information, error codes, code libraries, or documentation. Returns top links and text snippets.",
                new Parameters(
                        "object",
                        Map.of(
                                "query", Map.of("type", "string", "description", "The search keywords or query, e.g., 'Java HttpClient POST timeout'")
                        ),
                        List.of("query")
                )
        ));

        Tool searchCodebaseTool = new Tool("function", new FunctionDef(
                "search_codebase",
                "Scans all file contents across the project workspace for a specific keyword, variable, or code snippet. Returns file paths and matching line numbers.",
                new Parameters(
                        "object",
                        Map.of(
                                "search_query", Map.of("type", "string", "description", "The exact text or keyword to look for across the files.")
                        ),
                        List.of("search_query")
                )
        ));

        List<Tool> availableTools = List.of(
                writeFileTool,
                commandExecTool,
                readFileTool,
                deleteFileTool,
                listDirectoryContents,
                readFileLinesTool,
                replaceStringTool,
                insertTextTool,
                ddgSearchTool,
                searchCodebaseTool
        );

        chatHistory.add(new ChatMessage("system", "You are an expert coding agent. " +
                "You are responsible for invoking the right kind of tools while coding"));

        System.out.println("=== Bhatman Ready ===");
        System.out.println("Type your instructions below. Type '.exit' to quit.\n");

        while (true) {
            System.out.print("\nYou > ");
            String userInput = scanner.nextLine().trim();

            if (".exit".equalsIgnoreCase(userInput)) {
                System.out.println("Peace Out!");
                break;
            }

            if (userInput.isEmpty()) {
                continue;
            }

            chatHistory.add(new ChatMessage("user", userInput));
            runAgentTurn(chatHistory, availableTools, scanner);
        }
        scanner.close();
    }

    public static void runAgentTurn(List<ChatMessage> chatHistory, List<Tool> availableTools, Scanner scanner) throws IOException {
        boolean processing = true;

        while (processing) {
            AtomicBoolean stopSpinner = new AtomicBoolean(false);
            Thread spinnerThread = startSpinner(stopSpinner);

            JsonNode messageNode;
            try {
                messageNode = sendNetworkRequest(chatHistory, availableTools, stopSpinner, spinnerThread);
            } catch (Exception e) {
                stopSpinner.set(true);
                System.err.println("\n[Error communicating with Ollama]: " + e.getMessage());
                return;
            } finally {
                // Safe to call even if already stopped inside sendNetworkRequest
                stopSpinner.set(true);
                try { spinnerThread.join(); } catch (InterruptedException ignored) {}
            }

            JsonNode toolCalls = messageNode.get("tool_calls");
            String contentText = messageNode.get("content").asText();

            if (toolCalls != null && toolCalls.isArray() && !toolCalls.isEmpty()) {
                chatHistory.add(new ChatMessage("assistant", contentText, toolCalls, null));
                for (JsonNode call : toolCalls) {
                    extractAndExecuteTools(call, chatHistory, scanner);
                }
            } else {
                // Content was already printed live during streaming
                processing = false;
            }
        }
    }

    private static void extractAndExecuteTools(JsonNode call, List<ChatMessage> chatHistory, Scanner scanner) throws IOException {
        String functionName = call.get("function").get("name").asText();
        JsonNode argumentsNode = call.get("function").get("arguments");

        System.out.println("\n[Agent Executing Tool]: " + functionName);

        JsonNode arguments;
        try {
            arguments = argumentsNode.isTextual()
                    ? OBJECT_MAPPER.readTree(argumentsNode.asText())
                    : argumentsNode;
        } catch (Exception e) {
            System.err.println("Failed to parse tool arguments: " + e.getMessage());
            return;
        }

        if ("write_file_to_disk".equals(functionName)) {
            String filename = arguments.get("filename").asText();
            String content = arguments.get("content").asText();
            String toolResult = Tools.localWriteFile(filename, content);
            chatHistory.add(new ChatMessage("tool", toolResult, null, functionName));
        }

        if ("exec_cmd".equals(functionName)) {
            String command = arguments.get("command").asText();
            String toolResult = Tools.execCommand(command, scanner);
            System.out.println(toolResult);
            chatHistory.add(new ChatMessage("tool", toolResult, null, functionName));
        }

        if ("read_file".equals(functionName)) {
            String pathname = arguments.get("path").asText();
            String toolResult = Tools.localReadFile(pathname);
            chatHistory.add(new ChatMessage("tool", toolResult, null, functionName));
        }

        if("delete_file".equals(functionName)) {
            String pathname = arguments.get("path").asText();
            String toolResult = Tools.localDeleteFile(pathname);
            chatHistory.add(new ChatMessage("tool", toolResult, null, functionName));
        }

        if("list_directory_contents".equals(functionName)) {
            String toolResult = Arrays.toString(Tools.listDirectoryContents().toArray(new String[0]));
            chatHistory.add((new ChatMessage("tool", toolResult, null, functionName)));
        }

        if ("read_file_lines".equals(functionName)) {
            String filename = arguments.get("filename").asText();
            int startLine = arguments.get("start_line").asInt();
            int endLine = arguments.get("end_line").asInt();

            String toolResult = Tools.readFileLines(filename, startLine, endLine);

            System.out.println("\n[File Context Retrieved (" + filename + " lines " + startLine + "-" + endLine + ")]:\n" + toolResult);

            chatHistory.add(new ChatMessage("tool", toolResult, null, functionName));
        }

        if ("replace_string_in_file".equals(functionName)) {
            String filename = arguments.get("filename").asText();
            String oldString = arguments.get("old_string").asText();
            String newString = arguments.get("new_string").asText();

            System.out.println("\n[Agent Patching File]: " + filename);
            String toolResult = Tools.replaceStringInFile(filename, oldString, newString);
            System.out.println(toolResult);

            chatHistory.add(new ChatMessage("tool", toolResult, null, functionName));
        }

        if ("insert_text_after_line".equals(functionName)) {
            String filename = arguments.get("filename").asText();
            int lineNumber = arguments.get("line_number").asInt();
            String textToInsert = arguments.get("text_to_insert").asText();

            System.out.println("\n[Agent Inserting Code]: After line " + lineNumber + " in " + filename);
            String toolResult = Tools.insertTextAfterLine(filename, lineNumber, textToInsert);
            System.out.println(toolResult);

            chatHistory.add(new ChatMessage("tool", toolResult, null, functionName));
        }

        if ("duckduckgo_search".equals(functionName)) {
            String query = arguments.get("query").asText();

            System.out.println("\n[Agent Querying DuckDuckGo]: " + query);
            String toolResult = Tools.duckduckgoSearch(query);
            System.out.println("[Search complete. Extracted top search engine snippets.]");

            chatHistory.add(new ChatMessage("tool", toolResult, null, functionName));
        }

        if ("search_codebase".equals(functionName)) {
            String searchQuery = arguments.get("search_query").asText();

            System.out.println("\n[Agent Grepping Codebase for]: " + searchQuery);
            String toolResult = Tools.searchCodebase(searchQuery);
            System.out.println("[Search Finished. Found matches returned to history context.]");

            chatHistory.add(new ChatMessage("tool", toolResult, null, functionName));
        }
    }

    private static JsonNode sendNetworkRequest(
            List<ChatMessage> chatHistory,
            List<Tool> availableTools,
            AtomicBoolean stopSpinner,
            Thread spinnerThread) throws IOException, InterruptedException {

        ChatRequest chatRequest = new ChatRequest(MODEL, chatHistory, availableTools, true);
        String payload = OBJECT_MAPPER.writeValueAsString(chatRequest);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<Stream<String>> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofLines());

        StringBuilder fullContent = new StringBuilder();
        StringBuilder fullThinking = new StringBuilder();
        JsonNode toolCallsNode = null;
        boolean spinnerStopped = false;
        boolean printedThinkingHeader = false;
        boolean printedContentHeader = false;

        try (Stream<String> lines = response.body()) {
            for (var it = lines.iterator(); it.hasNext(); ) {
                String line = it.next();
                if (line.isBlank()) continue;

                JsonNode chunk = OBJECT_MAPPER.readTree(line);
                JsonNode msg = chunk.get("message");
                if (msg == null) continue;

                // Stop spinner on first token
                if (!spinnerStopped) {
                    stopSpinner.set(true);
                    spinnerThread.join();
                    spinnerStopped = true;
                }

                JsonNode thinkingChunk = msg.get("thinking");
                if (thinkingChunk != null && !thinkingChunk.isNull() && !thinkingChunk.asText().isEmpty()) {
                    if (!printedThinkingHeader) {
                        System.out.print("\n[Thinking]: ");
                        printedThinkingHeader = true;
                    }
                    System.out.print(thinkingChunk.asText());
                    System.out.flush();
                    fullThinking.append(thinkingChunk.asText());
                }

                JsonNode contentChunk = msg.get("content");
                if (contentChunk != null && !contentChunk.isNull() && !contentChunk.asText().isEmpty()) {
                    if (!printedContentHeader) {
                        if (printedThinkingHeader) System.out.println();
                        System.out.print("\nBhatman > ");
                        printedContentHeader = true;
                    }
                    System.out.print(contentChunk.asText());
                    System.out.flush();
                    fullContent.append(contentChunk.asText());
                }

                JsonNode toolCalls = msg.get("tool_calls");
                if (toolCalls != null && !toolCalls.isNull() && toolCalls.isArray() && !toolCalls.isEmpty()) {
                    toolCallsNode = toolCalls;
                }

                if (chunk.has("done") && chunk.get("done").asBoolean()) break;
            }
        }

        if (printedContentHeader) System.out.println();

        ObjectNode assembled = OBJECT_MAPPER.createObjectNode();
        assembled.put("role", "assistant");
        assembled.put("content", fullContent.toString());
        if (!fullThinking.isEmpty()) assembled.put("thinking", fullThinking.toString());
        if (toolCallsNode != null) assembled.set("tool_calls", toolCallsNode);

        return assembled;
    }

    private static Thread startSpinner(AtomicBoolean stopSpinner) {
        Thread spinner = new Thread(() -> {
            int i = 0;
            System.out.print("\nThinking ");
            while (!stopSpinner.get()) {
                System.out.print("\rThinking " + SPINNER_FRAMES[i % SPINNER_FRAMES.length] + " ");
                System.out.flush();
                i++;
                try {
                    Thread.sleep(80);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            System.out.print("\r" + " ".repeat(20) + "\r");
            System.out.flush();
        });
        spinner.start();
        return spinner;
    }
}
