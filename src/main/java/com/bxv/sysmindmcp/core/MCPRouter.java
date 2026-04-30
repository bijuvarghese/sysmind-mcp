package com.bxv.sysmindmcp.core;

import com.bxv.sysmindmcp.llm.LLMService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@AllArgsConstructor
public class MCPRouter {

    private final ToolRegistry registry;
    private final LLMService llm;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Mono<String> handle(String prompt, String model) {
        String tools = buildToolsList();

        String decisionPrompt = "You are a system agent.\n\n" +
                "Choose ONE tool from the list below:\n" +
                tools +
                "\nReturn ONLY valid JSON. No explanation. No extra text.\n" +
                "Format:\n" +
                "{\"tool\":\"tool_name\"}\n\n" +
                "User request:\n" + prompt;

        System.out.println("User Prompt: " + decisionPrompt);

        return llm.ask(decisionPrompt, model)
                .map(this::extractTool)
                .flatMap(tool -> executeToolOrFallback(tool, prompt, model));
    }

    private Mono<String> executeToolOrFallback(String tool, String prompt, String model) {
        if (!registry.hasTool(tool)) {
            System.out.println("Invalid tool from LLM: " + tool + ", asking LLM without tool data");
            return llm.ask(prompt, model);
        }

        return executeTool(tool)
                .flatMap(formattedPrompt -> llm.ask(formattedPrompt, model));
    }

    private Mono<String> executeTool(String tool) {
        return Mono.fromCallable(() -> registry.getTool(tool).execute())
                .subscribeOn(Schedulers.boundedElastic()) // prevent blocking main thread
                .map(result -> formatPrompt(tool, result));
    }

    private String formatPrompt(String tool, Object result) {
        System.out.println("Executing Tool: " + tool);

        return "Explain this system data clearly:\n" +
                "Tool: " + tool + "\n" +
                "Result: " + result;
    }

    private String buildToolsList() {
        StringBuilder sb = new StringBuilder();

        registry.getTools().forEach(tool -> {
            sb.append("- ")
                    .append(tool.name())
                    .append(": ")
                    .append(tool.description())
                    .append("\n");
        });

        return sb.toString();
    }

    private String extractTool(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            String content = response;

            if (root.has("choices") && root.get("choices").isArray() && root.get("choices").size() > 0) {
                JsonNode message = root.get("choices").get(0).get("message");
                if (message != null && message.has("content")) {
                    content = message.get("content").asText();
                }
            }

            int start = content.indexOf("{");
            int end = content.lastIndexOf("}") + 1;

            if (start == -1 || end <= start) {
                return "disk_usage";
            }

            String json = content.substring(start, end);
            JsonNode node = objectMapper.readTree(json);

            if (node.has("tool")) {
                String tool = node.get("tool").asText();
                System.out.println("Extracted tool: " + tool);
                return tool;
            }

            return "disk_usage";

        } catch (Exception e) {
            System.out.println("Error parsing tool: " + e.getMessage());
            return "disk_usage";
        }
    }
}
