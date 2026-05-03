package com.bxv.sysmindmcp.core;

import com.bxv.sysmindmcp.services.LLMService;
import com.bxv.sysmindmcp.model.LLMResponse;
import com.bxv.sysmindmcp.model.NewsArticle;
import com.bxv.sysmindmcp.model.NewsResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class MCPRouter {
    private static final Logger log = LoggerFactory.getLogger(MCPRouter.class);
    private static final String NO_TOOL = "__no_tool__";
    private static final DateTimeFormatter LOCAL_NEWS_TIME_FORMAT = DateTimeFormatter
            .ofPattern("MMM d, yyyy h:mm a z", Locale.US);

    private final ToolRegistry registry;
    private final LLMService llm;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Mono<LLMResponse> handle(String prompt, String model) {
        String decisionPrompt = """
                You are a system agent.

                Choose the best matching tool from the list below only when the user asks for data that tool provides.
                If no tool applies, return {"tool":"none","arguments":{}}.
                For latest_news, infer URL arguments from the user request:
                - query: concise Google News search query, without URL encoding
                - when:1h → last hour
                - when:1d → last day
                - when:7d → last 7 days
                - when:1m → last month
                - append this to query in this format for eg: query+when:1d
                - language: result language/locale like en-US, fr-CA, hi-IN
                - country: country focus like US, CA, IN
                - ceid: country and language code like US:en, CA:fr, IN:hi

                %s
                Return ONLY valid JSON. No explanation. No extra text.
                Format:
                {"tool":"tool_name_or_none","arguments":{}}

                User request:
                %s""".formatted(buildToolsList(), prompt);

        int promptLength = prompt == null ? 0 : prompt.length();
        log.debug("Requesting tool decision. availableTools={}, promptLength={}", registry.getTools().size(),
                promptLength);

        return llm.ask(decisionPrompt, model)
                .map(response -> extractToolDecision(response.firstMessageContent()))
                .flatMap(decision -> executeToolOrFallback(decision, prompt, model));
    }

    private Mono<LLMResponse> executeToolOrFallback(ToolDecision decision, String prompt, String model) {
        if (NO_TOOL.equals(decision.tool())) {
            log.warn("LLM tool decision was unavailable. Asking LLM without tool data.");
            return llm.ask(prompt, model);
        }

        if (!registry.hasTool(decision.tool())) {
            log.warn("LLM selected unavailable tool '{}'. Asking LLM without tool data.", decision.tool());
            return llm.ask(prompt, model);
        }

        return executeTool(decision, prompt)
                .flatMap(formattedPrompt -> llm.ask(formattedPrompt, model));
    }

    private Mono<String> executeTool(ToolDecision decision, String prompt) {
        return Mono.fromCallable(() -> registry.getTool(decision.tool()).execute(prompt, decision.arguments()))
                .subscribeOn(Schedulers.boundedElastic()) // prevent blocking main thread
                .map(result -> formatPrompt(prompt, result));
    }

    private String formatPrompt(String prompt, Object result) {
        log.debug("Formatting tool result.");

        return """
                Answer the user in plain language using the available information.
                Keep it concise. Use at most 5 bullet points.
                For news answers, include the published date/time and RSS feed shown in each headline line.
                Do not include reasoning, chain-of-thought, XML, article URLs, or implementation details.
                Return only the final answer.

                User request:
                %s

                Available information:
                %s"""
                .formatted(prompt, formatToolResult(result));
    }

    private String formatToolResult(Object result) {
        if (result instanceof NewsResult newsResult) {
            return formatNewsResult(newsResult);
        }

        return String.valueOf(result);
    }

    private String formatNewsResult(NewsResult newsResult) {
        if (newsResult.getError() != null && !newsResult.getError().isBlank()) {
            if (newsResult.getFeedUrl() != null && !newsResult.getFeedUrl().isBlank()) {
                return "News lookup failed for " + newsResult.getFeedUrl() + ": " + newsResult.getError();
            }

            return "News lookup failed: " + newsResult.getError();
        }

        if (newsResult.getArticles() == null || newsResult.getArticles().isEmpty()) {
            return "No news articles were found.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Fetched at: ").append(localTimestamp(newsResult.getFetchedAt())).append("\n");
        sb.append("Headlines:\n");

        newsResult.getArticles().stream()
                .limit(5)
                .forEach(article -> appendArticle(sb, article, newsResult.getFeedUrl(), newsResult.getFetchedAt()));

        return sb.toString().trim();
    }

    private void appendArticle(StringBuilder sb, NewsArticle article, String feedUrl, Object fetchedAt) {
        sb.append("- [published: ").append(articleTimestamp(article, fetchedAt)).append("] ")
                .append(article.getTitle());

        if (article.getSource() != null && !article.getSource().isBlank()) {
            sb.append(" (").append(article.getSource()).append(")");
        }

        if (feedUrl != null && !feedUrl.isBlank()) {
            sb.append(" [rss: ").append(feedUrl).append("]");
        }

        sb.append("\n");
    }

    private String articleTimestamp(NewsArticle article, Object fetchedAt) {
        if (article.getPublishedAt() != null && !article.getPublishedAt().isBlank()) {
            return localTimestamp(article.getPublishedAt());
        }

        return localTimestamp(fetchedAt);
    }

    private String localTimestamp(Object value) {
        if (value instanceof Instant instant) {
            return formatLocal(instant);
        }

        if (value instanceof ZonedDateTime zonedDateTime) {
            return formatLocal(zonedDateTime.toInstant());
        }

        if (value instanceof String text && !text.isBlank()) {
            try {
                return formatLocal(ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
            } catch (DateTimeParseException ignored) {
                try {
                    return formatLocal(Instant.parse(text));
                } catch (DateTimeParseException ignoredAgain) {
                    return text;
                }
            }
        }

        return String.valueOf(value);
    }

    private String formatLocal(Instant instant) {
        return LOCAL_NEWS_TIME_FORMAT.format(instant.atZone(ZoneId.systemDefault()));
    }

    private String buildToolsList() {
        return registry.getTools().stream()
                .map(tool -> "- " + tool.name() + ": " + tool.description())
                .collect(Collectors.joining("\n"));
    }

    private ToolDecision extractToolDecision(String content) {
        if (content == null) {
            return ToolDecision.none();
        }

        try {
            int start = content.indexOf('{');
            int end = content.lastIndexOf('}');

            if (start == -1 || end <= start) {
                return ToolDecision.none();
            }

            JsonNode node = objectMapper.readTree(content.substring(start, end + 1));
            String tool = node.path("tool").asText();

            if (tool.isBlank() || "none".equalsIgnoreCase(tool)) {
                return ToolDecision.none();
            }

            log.debug("Extracted tool from LLM response. tool={}", tool);
            return new ToolDecision(tool, extractArguments(node.path("arguments")));
        } catch (Exception e) {
            log.warn("Unable to parse tool decision from LLM response. Falling back to no-tool path. reason={}",
                    e.getMessage());
            return ToolDecision.none();
        }
    }

    private Map<String, String> extractArguments(JsonNode argumentsNode) {
        if (!argumentsNode.isObject()) {
            return Map.of();
        }

        Map<String, String> arguments = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = argumentsNode.fields();

        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode value = field.getValue();

            if (value.isTextual() && !value.asText().isBlank()) {
                arguments.put(field.getKey(), value.asText().trim());
            }
        }

        return arguments;
    }

    private record ToolDecision(String tool, Map<String, String> arguments) {
        private static ToolDecision none() {
            return new ToolDecision(NO_TOOL, Map.of());
        }
    }
}
