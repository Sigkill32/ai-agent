import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.util.Scanner;
import java.util.stream.Stream;

public class Main {
    public static final String BASE_URL = "http://localhost:11434/api/generate";
    public static final String MODEL = "huihui_ai/qwen2.5-abliterate:32b-instruct-q4_K_M";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    public static void main(String[] args) throws IOException, InterruptedException {
        Scanner scanner = new Scanner(System.in);
        System.out.println("What do you want to build?");
        String userPrompt = scanner.nextLine();

        Path outputPath = createFile(userPrompt);

        if (!Files.exists(outputPath)) {
            writeFile(userPrompt, outputPath);
        } else {
            System.out.println("\nLoaded previous data\n");
        }

        while (true) {
            System.out.println("\n\nWaiting for more instructions (type '.exit' to quit):");
            userPrompt = scanner.nextLine();
            if (".exit".equalsIgnoreCase(userPrompt.trim())) {
                scanner.close();
                return;
            }

            String prevContent = Files.readString(outputPath);
            String modedPrompt = userPrompt + "\n\nHere is the existing file content to modify:\n" + prevContent;
            writeFile(modedPrompt, outputPath);
        }
    }

    private static String parseResponseToken(String jsonLine) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(jsonLine);
            return root.has("response") ? root.get("response").asText() : "";
        } catch (Exception e) {
            return "";
        }
    }

    public static Path createFile(String prompt) throws JsonProcessingException {
        String systemPrompt = "You are an expert programmer. Find the perfect filename if the user hasn't entered one but if the user has, use the same file name. Output ONLY valid filename. Do NOT include explanations, introduction, markdown blocks, or wrap-up text. Task: ";
        String finalPrompt = systemPrompt + prompt;

        OllamaModel ollamaModel = new OllamaModel(MODEL, finalPrompt, false);
        String payload = OBJECT_MAPPER.writeValueAsString(ollamaModel);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            String filename = parseResponseToken(response.body()).trim().replaceAll("[\\r\\n]", "");

            if (filename.isEmpty()) {
                filename = "output.txt";
            }

            Path outputFilePath = Path.of(filename);
            System.out.println("Target file established: " + outputFilePath.toAbsolutePath());
            return outputFilePath;
        } catch (Exception e) {
            System.err.println("Failed to resolve filename, defaulting to 'output.txt'");
            return Path.of("output.txt");
        }
    }

    public static void writeFile(String prompt, Path outputFilePath) throws JsonProcessingException {
        String strictSystemPrompt = "You are a strict code generator. Output ONLY valid source code. "
                + "Do NOT include explanations, introduction, markdown blocks (like ```python), or wrap-up text.";
        String fullPrompt = strictSystemPrompt + "\n\nTask: " + prompt;

        OllamaModel ollamaModel = new OllamaModel(MODEL, fullPrompt, true);
        String payload = OBJECT_MAPPER.writeValueAsString(ollamaModel);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        StringBuilder contentBuffer = new StringBuilder();

        try {
            HttpResponse<Stream<String>> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofLines());
            try (Stream<String> lines = response.body()) {
                lines.forEach(line -> {
                    String token = parseResponseToken(line);
                    System.out.print(token);
                    System.out.flush();
                    contentBuffer.append(token);
                });
            }

            String finalCode = contentBuffer.toString();
            if (finalCode.contains("```")) {
                finalCode = finalCode.replaceAll("```[a-zA-Z]*\\n", "").replaceAll("```", "");
            }

            Files.writeString(outputFilePath, finalCode,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

        } catch (Exception e) {
            System.err.println("\nError while generating or writing code.");
            e.printStackTrace();
        }
    }
}