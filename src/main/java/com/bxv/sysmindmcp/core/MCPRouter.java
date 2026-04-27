package com.bxv.sysmindmcp.core;

import com.bxv.sysmindmcp.llm.LLMService;
import com.bxv.sysmindmcp.tools.SystemTool;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class MCPRouter {
    private final ToolRegistry registry;
    private final LLMService llm;

    public String handle(String prompt) {
        String tools = buildToolsList();
        String decisionPrompt =
                "You are a system agent.\n\n" +
                        "Choose ONE tool:\n" +
                        tools +
                        "\nReturn JSON only like:\n" +
                        "{ \"tool\": \"...\" }\n\n" +
                        "User request:\n" + prompt;
        String response = llm.ask(prompt);
        String toolName = extractTool(response);
        SystemTool tool = registry.getTool(toolName);
        Object result = tool != null ? tool.execute() : "no tool found";

        String finalPrompt =
                "Explain this system data clearly:\n" +
                        "Tool: " + toolName + "\n" +
                        "Result: " + result;

        return llm.ask(finalPrompt);

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
