import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class AgentBhat {
    public static final String BASE_URL = "http://localhost:11434/api/chat";
    public static final String MODEL = "huihui_ai/qwen2.5-abliterate:32b-instruct-q4_K_M";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    public record ChatMessage(String role, String content){};
    public record ChatRequest(String model, List<ChatMessage> messages, Boolean stream){};

    public static void main(String[] args) throws IOException, InterruptedException {
        Scanner scanner = new Scanner(System.in);
        List<ChatMessage> chatMessages = new ArrayList<>();

        chatMessages.add(new ChatMessage("system", "You are a strict code generator. Output ONLY valid source code." +
                "Based on the given prompt find the best possible filename, generate code and explanation. " +
                "If the user has already given a filename, use that" +
                "Output must be strictly in the following format" +
                "{filename: <filename>, code: <code>, explanation: <explanation>}"
        ));

        System.out.println("Agent ready. Ask your first question:");

        while (true) {
            System.out.print("\nUser > ");
            String userPrompt = scanner.nextLine();

            if (".exit".equalsIgnoreCase(userPrompt.trim())) {
                break;
            }

            chatMessages.add(new ChatMessage("user", userPrompt));

            String assistantMessage = callModel(chatMessages);

            chatMessages.add(new ChatMessage("assistant", assistantMessage));

            String[] parsedContent = parseContent(assistantMessage);
            writeFile(parsedContent);

            System.out.println("\n[Generated File]: " + parsedContent[0]);
            System.out.println("\n[Explanation]:\n" + parsedContent[2]);
        }

        scanner.close();
        System.out.println("Peace out!");
    }

    public static void writeFile(String[] data) {
        if (data[0] == null || data[0].isEmpty()) return;
        Path outputPath = Path.of(data[0]);
        try {
            Files.writeString(outputPath, data[1], StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String[] parseContent(String content) {
        String[] data = {"", "", ""};
        try {
            JsonNode root = OBJECT_MAPPER.readTree(content);
            String filename = root.has("filename") ? root.get("filename").asText() : "";
            String code = root.has("code") ? root.get("code").asText() : "";
            String explanation = root.has("explanation") ? root.get("explanation").asText() : "";
            data[0] = filename;
            data[1] = code;
            data[2] = explanation;
        } catch (Exception e) {
            System.err.println("\nParsing error! Check if model strayed from JSON format.");
            e.printStackTrace();
        }
        return data;
    }

    public static String getContent(String line) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(line);
            if(root.has("message") && root.get("message").has("content")) {
                return root.get("message").get("content").asText();
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    public static String callModel(List<ChatMessage> chatHistory) throws IOException, InterruptedException {
        ChatRequest chatRequest = new ChatRequest(MODEL, chatHistory, true);
        String payload = OBJECT_MAPPER.writeValueAsString(chatRequest);
        StringBuilder content = new StringBuilder();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<Stream<String>> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofLines());
        Stream<String> lines = response.body();

        char[] spinnerFrames = {'⠋', '⠙', '⠹', '⠸', '⠼', '⠴', '⠦', '⠧', '⠇', '⠏'};
        AtomicInteger counter = new AtomicInteger(0);

        lines.forEach(line -> {
            String data = getContent(line);
            content.append(data);
            int frameIndex = counter.getAndIncrement() % spinnerFrames.length;
            System.out.print("\rRazzleDazzling " + spinnerFrames[frameIndex] + " ");
        });

        System.out.print("\rRazzleDazzling Done!     \n");
        return content.toString();
    }
}