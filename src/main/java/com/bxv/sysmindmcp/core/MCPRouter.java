package com.bxv.sysmindmcp.core;

import com.bxv.sysmindmcp.llm.LLMService;
import com.bxv.sysmindmcp.model.LLMResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@AllArgsConstructor
public class MCPRouter {
    private static final Logger log = LoggerFactory.getLogger(MCPRouter.class);
    private static final String NO_TOOL = "__no_tool__";

    private final ToolRegistry registry;
    private final LLMService llm;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Mono<LLMResponse> handle(String prompt, String model) {
        String tools = buildToolsList();

        String decisionPrompt = "You are a system agent.\n\n" +
                "Choose the best matching tool from the list below only when the user asks for data that tool provides.\n"
                +
                "If no tool applies, return {\"tool\":\"none\"}.\n" +
                tools +
                "\nReturn ONLY valid JSON. No explanation. No extra text.\n" +
                "Format:\n" +
                "{\"tool\":\"tool_name_or_none\"}\n\n" +
                "User request:\n" + prompt;

        int promptLength = prompt == null ? 0 : prompt.length();
        log.debug("Requesting tool decision. availableTools={}, promptLength={}", registry.getTools().size(),
                promptLength);

        return llm.ask(decisionPrompt, model)
                .map(response -> extractTool(response.firstMessageContent()))
                .flatMap(tool -> executeToolOrFallback(tool, prompt, model));
    }

    private Mono<LLMResponse> executeToolOrFallback(String tool, String prompt, String model) {
        if (NO_TOOL.equals(tool)) {
            log.warn("LLM tool decision was unavailable. Asking LLM without tool data.");
            return llm.ask(prompt, model);
        }

        if (!registry.hasTool(tool)) {
            log.warn("LLM selected unavailable tool '{}'. Asking LLM without tool data.", tool);
            return llm.ask(prompt, model);
        }

        return executeTool(tool, prompt)
                .flatMap(formattedPrompt -> llm.ask(formattedPrompt, model));
    }

    private Mono<String> executeTool(String tool, String prompt) {
        return Mono.fromCallable(() -> registry.getTool(tool).execute(prompt))
                .subscribeOn(Schedulers.boundedElastic()) // prevent blocking main thread
                .map(result -> formatPrompt(prompt, result));
    }

    private String formatPrompt(String prompt, Object result) {
        log.debug("Formatting tool result.");

        return "Answer the user in plain language using the available information.\n" +
                "Keep it concise and avoid implementation details.\n\n" +
                "User request:\n" + prompt + "\n\n" +
                "Available information:\n" + result;
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

    private String extractTool(String content) {
        try {
            if (content == null) {
                return NO_TOOL;
            }

            int start = content.indexOf("{");
            int end = content.lastIndexOf("}") + 1;

            if (start == -1 || end <= start) {
                return NO_TOOL;
            }

            String json = content.substring(start, end);
            JsonNode node = objectMapper.readTree(json);

            if (node.has("tool")) {
                String tool = node.get("tool").asText();
                if ("none".equalsIgnoreCase(tool)) {
                    return NO_TOOL;
                }

                log.debug("Extracted tool from LLM response. tool={}", tool);
                return tool;
            }

            return NO_TOOL;

        } catch (Exception e) {
            log.warn("Unable to parse tool decision from LLM response. Falling back to no-tool path. reason={}",
                    e.getMessage());
            return NO_TOOL;
        }
    }
}
