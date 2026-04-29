package com.bxv.sysmindmcp.core;

import com.bxv.sysmindmcp.llm.LLMService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class MCPRouter {
    private final ToolRegistry registry;
    private final LLMService llm;

    public Mono<String> handle(String prompt, String model) {
        String tools = buildToolsList();
        String decisionPrompt = "You are a system agent.\n\n" +
                "Choose ONE tool:\n" +
                tools +
                "\nReturn JSON only like:\n" +
                "{ \"tool\": \"...\" }\n\n" +
                "User request:\n" + prompt;
        return llm.ask(decisionPrompt, model)
                .map(this::extractTool)
                .flatMap(tool -> Mono.fromCallable(() -> registry.getTool(tool).execute())
                        .map(result -> formatPrompt(tool, result)))
                .flatMap(formattedPrompt -> llm.ask(formattedPrompt, model));

    }

    private String formatPrompt(String tool, Object result) {
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
            int start = response.indexOf("{");
            int end = response.lastIndexOf("}") + 1;
            String json = response.substring(start, end);

            return json.contains("disk") ? "disk_usage"
                    : json.contains("memory") ? "memory_usage"
                            : json.contains("cpu") ? "cpu_usage"
                                    : "disk_usage";

        } catch (Exception e) {
            return "disk_usage";
        }
    }

}
