import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Stream;

public class Tools {
    public static String localWriteFile(String filename, String content) {
        try {
            Path path = Path.of(filename);
            Files.writeString(path, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            System.out.println("-> Successfully updated local file at: " + path.toAbsolutePath());
            return "Success: File '" + filename + "' has been successfully created/updated on disk.";
        } catch (IOException e) {
            System.err.println("-> Failed to write file: " + e.getMessage());
            return "Error: Failed to write file due to: " + e.getMessage();
        }
    }

    private static String execute(String[] command) {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        try {
            Process process = processBuilder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            StringBuilder output = new StringBuilder("");
            while ((line = reader.readLine()) != null) {
                output.append(line);
                System.out.println(line);
            }

            int exitCode = process.waitFor();
            System.out.println("\nProcess exited with code: " + exitCode);
            return output.toString();

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return e.toString();
        }
    }

    public static String execCommand(String command, Scanner scanner) {
        System.out.print("AgentBhat wants to run `" + command + "`. Do you want to continue (Y/N): ");
        String shouldContinue = scanner.nextLine();
        return switch (shouldContinue.toUpperCase()) {
            case "Y" -> { execute(command.split(" ")); yield ""; }
            case "N" -> "Rejected by user";
            default -> "Something went wrong";
        };
    }

    public static String localReadFile(String path) {
        Path filePath = Path.of(path);
        try {
            return Files.readString(filePath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String localDeleteFile(String path) {
        Path filePath = Path.of(path);
        try {
            Files.deleteIfExists(filePath);
            return "File "+ path +" was deleted successfully";
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static ArrayList<String> listDirectoryContents() {
        ArrayList<String> currentFiles = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(Path.of("."), 6)) {
            stream.forEach(file -> {
                currentFiles.add(file.toString());
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
        return currentFiles;
    }

    public static String readFileLines(String filename, int startLine, int endLine) {
        Path path = Path.of(filename);
        if (!Files.exists(path)) {
            return "Error: File '" + filename + "' does not exist.";
        }
        if (startLine < 1 || endLine < startLine) {
            return "Error: Invalid line range. Line numbers must be 1-based, and start_line must be <= end_line.";
        }

        try (java.util.stream.Stream<String> lines = Files.lines(path)) {
            StringBuilder contentBuilder = new StringBuilder();
            long skipCount = startLine - 1;
            long limitCount = (endLine - startLine) + 1;

            List<String> targetedLines = lines.skip(skipCount)
                    .limit(limitCount)
                    .toList();

            if (targetedLines.isEmpty()) {
                return "Warning: The specified line range falls outside the actual file boundaries.";
            }

            int currentLineNum = startLine;
            for (String line : targetedLines) {
                contentBuilder.append(currentLineNum).append(": ").append(line).append("\n");
                currentLineNum++;
            }

            return contentBuilder.toString();
        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    public static String replaceStringInFile(String filename, String oldString, String newString) {
        Path path = Path.of(filename);
        if (!Files.exists(path)) {
            return "Error: File '" + filename + "' does not exist.";
        }

        try {
            String content = Files.readString(path);

            if (!content.contains(oldString)) {
                return "Error: The specified 'old_string' was not found exactly as provided in '" + filename + "'. Replacement failed. Double check your syntax, formatting, or indentation.";
            }

            int firstIdx = content.indexOf(oldString);
            int lastIdx = content.lastIndexOf(oldString);
            boolean isMultiple = firstIdx != lastIdx;

            String updatedContent = content.replace(oldString, newString);
            Files.writeString(path, updatedContent, StandardOpenOption.TRUNCATE_EXISTING);

            if (isMultiple) {
                return "Success: Replaced multiple instances of the specified text block in " + filename;
            } else {
                return "Success: Successfully updated " + filename;
            }

        } catch (IOException e) {
            return "Error reading or writing file: " + e.getMessage();
        }
    }

    public static String insertTextAfterLine(String filename, int lineNumber, String textToInsert) {
        Path path = Path.of(filename);
        if (!Files.exists(path)) {
            return "Error: File '" + filename + "' does not exist.";
        }
        if (lineNumber < 0) {
            return "Error: Line number must be 0 or greater (0 inserts text at the absolute top of the file).";
        }

        try {
            List<String> lines = new ArrayList<>(Files.readAllLines(path));

            if (lineNumber > lines.size()) {
                return "Error: Line number " + lineNumber + " exceeds the total lines in the file (" + lines.size() + "). Append failed.";
            }

            if (lineNumber == 0) {
                lines.add(0, textToInsert);
            } else {
                lines.add(lineNumber, textToInsert);
            }

            Files.write(path, lines, StandardOpenOption.TRUNCATE_EXISTING);
            return "Success: Inserted text after line " + lineNumber + " in " + filename;

        } catch (IOException e) {
            return "Error modifying file: " + e.getMessage();
        }
    }

    public static String duckduckgoSearch(String query) {
        try {
            String encodedQuery = java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://searx.be/search?q=" + encodedQuery + "&format=json&engines=google,bing,duckduckgo"))
                    .header("User-Agent", "Mozilla/5.0 (compatible; AgentBhat/1.0)")
                    .GET()
                    .build();

            HttpResponse<String> response = BhatAgentTool.HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return "Error: SearXNG returned HTTP " + response.statusCode();
            }

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(response.body());
            com.fasterxml.jackson.databind.JsonNode results = root.path("results");

            if (results.isEmpty()) {
                return "Search completed but no results found for: " + query;
            }

            StringBuilder summary = new StringBuilder("--- Search Results for: \"" + query + "\" ---\n\n");
            int count = 0;
            for (com.fasterxml.jackson.databind.JsonNode result : results) {
                if (count++ >= 5) break;
                summary.append("Title: ").append(result.path("title").asText()).append("\n")
                        .append("URL: ").append(result.path("url").asText()).append("\n")
                        .append("Snippet: ").append(result.path("content").asText("No description available.")).append("\n\n");
            }

            return summary.toString();

        } catch (Exception e) {
            return "Error calling SearXNG: " + e.getMessage();
        }
    }

    public static String searchCodebase(String searchQuery) {
        StringBuilder resultsBuilder = new StringBuilder("--- Codebase Search Results for: \"" + searchQuery + "\" ---\n");
        int matchCount = 0;

        try (java.util.stream.Stream<Path> stream = Files.walk(Path.of("."), 6)) {

            List<Path> targetFiles = stream.filter(Files::isRegularFile).filter(path -> {
                String strPath = path.toString();
                return !strPath.contains("/.git/") &&
                        !strPath.contains("/.idea/") &&
                        !strPath.contains("/target/") &&
                        !strPath.contains("/node_modules/");
            }).toList();

            for (Path file : targetFiles) {
                try {
                    List<String> lines = Files.readAllLines(file);
                    for (int i = 0; i < lines.size(); i++) {
                        String lineText = lines.get(i);

                        if (lineText.toLowerCase().contains(searchQuery.toLowerCase())) {
                            int oneBasedLineNum = i + 1;
                            resultsBuilder.append("File: ").append(file.toString())
                                    .append(" [Line ").append(oneBasedLineNum).append("]: ")
                                    .append(lineText.trim()).append("\n");
                            matchCount++;
                        }
                    }
                } catch (IOException e) {
                    
                }
            }

        } catch (IOException e) {
            return "Error walking directory structure: " + e.getMessage();
        }

        if (matchCount == 0) {
            return "No occurrences of \"" + searchQuery + "\" were found anywhere in the codebase.";
        }

        return resultsBuilder.toString();
    }
}
