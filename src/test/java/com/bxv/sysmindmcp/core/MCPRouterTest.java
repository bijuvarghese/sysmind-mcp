package com.bxv.sysmindmcp.core;

import com.bxv.sysmindmcp.llm.LLMService;
import com.bxv.sysmindmcp.model.LLMResponse;
import com.bxv.sysmindmcp.model.NewsArticle;
import com.bxv.sysmindmcp.model.NewsResult;
import com.bxv.sysmindmcp.tools.SystemTool;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MCPRouterTest {

    @Test
    void handleExecutesToolSelectedFromChatCompletionResponse() {
        LLMService llm = mock(LLMService.class);
        SystemTool ramTool = tool("ram_usage", "Return RAM usage", "free=10,total=20,used=10");
        SystemTool diskTool = tool("disk_usage", "Return disk usage", "free=20,total=40,used=20");
        MCPRouter router = new MCPRouter(new ToolRegistry(List.of(ramTool, diskTool)), llm);

        when(llm.ask(org.mockito.ArgumentMatchers.contains("Choose the best matching tool"), eq("model-a")))
                .thenReturn(Mono.just(response("{\"tool\":\"ram_usage\"}")));
        when(llm.ask(org.mockito.ArgumentMatchers.contains("Answer the user in plain language"), eq("model-a")))
                .thenReturn(Mono.just(response("RAM usage is healthy.")));

        StepVerifier.create(router.handle("How much memory is available?", "model-a"))
                .assertNext(response -> assertThat(response.firstMessageContent()).isEqualTo("RAM usage is healthy."))
                .verifyComplete();

        verify(llm).ask(org.mockito.ArgumentMatchers.contains("Choose the best matching tool"), eq("model-a"));
        verify(llm).ask(org.mockito.ArgumentMatchers.contains("Available information:\nfree=10,total=20,used=10"), eq("model-a"));
    }

    @Test
    void handleAsksLlmWithoutToolDataWhenSelectedToolIsUnknown() {
        LLMService llm = mock(LLMService.class);
        SystemTool diskTool = tool("disk_usage", "Return disk usage", "disk-result");
        MCPRouter router = new MCPRouter(new ToolRegistry(List.of(diskTool)), llm);

        when(llm.ask(org.mockito.ArgumentMatchers.contains("Choose the best matching tool"), eq(null)))
                .thenReturn(Mono.just(response("{\"tool\":\"not_registered\"}")));
        when(llm.ask("Check something unsupported", null))
                .thenReturn(Mono.just(response("Direct LLM response.")));

        StepVerifier.create(router.handle("Check something unsupported", null))
                .assertNext(response -> assertThat(response.firstMessageContent()).isEqualTo("Direct LLM response."))
                .verifyComplete();

        verify(llm).ask("Check something unsupported", null);
    }

    @Test
    void handleAsksLlmWithoutToolDataWhenToolDecisionCannotBeParsed() {
        LLMService llm = mock(LLMService.class);
        SystemTool diskTool = tool("disk_usage", "Return disk usage", "disk-result");
        MCPRouter router = new MCPRouter(new ToolRegistry(List.of(diskTool)), llm);

        when(llm.ask(org.mockito.ArgumentMatchers.contains("Choose the best matching tool"), eq(null)))
                .thenReturn(Mono.just(response("not-json")));
        when(llm.ask("Explain the system status", null))
                .thenReturn(Mono.just(response("Direct LLM response.")));

        StepVerifier.create(router.handle("Explain the system status", null))
                .assertNext(response -> assertThat(response.firstMessageContent()).isEqualTo("Direct LLM response."))
                .verifyComplete();

        verify(llm).ask("Explain the system status", null);
    }

    @Test
    void handleAsksLlmWithoutToolDataWhenNoToolApplies() {
        LLMService llm = mock(LLMService.class);
        SystemTool diskTool = tool("disk_usage", "Return disk usage", "disk-result");
        MCPRouter router = new MCPRouter(new ToolRegistry(List.of(diskTool)), llm);

        when(llm.ask(org.mockito.ArgumentMatchers.contains("If no tool applies"), eq(null)))
                .thenReturn(Mono.just(response("{\"tool\":\"none\"}")));
        when(llm.ask("Tell me a joke", null))
                .thenReturn(Mono.just(response("Direct LLM response.")));

        StepVerifier.create(router.handle("Tell me a joke", null))
                .assertNext(response -> assertThat(response.firstMessageContent()).isEqualTo("Direct LLM response."))
                .verifyComplete();

        verify(llm).ask("Tell me a joke", null);
    }

    @Test
    void handlePassesLlmToolArgumentsToSelectedTool() {
        LLMService llm = mock(LLMService.class);
        SystemTool newsTool = new SystemTool() {
            @Override
            public String name() {
                return "latest_news";
            }

            @Override
            public String version() {
                return "v1";
            }

            @Override
            public String description() {
                return "Return news";
            }

            @Override
            public Object execute() {
                return "no-args";
            }

            @Override
            public Object execute(String prompt, Map<String, String> arguments) {
                return arguments.get("query") + "|" + arguments.get("language") + "|" + arguments.get("country");
            }
        };
        MCPRouter router = new MCPRouter(new ToolRegistry(List.of(newsTool)), llm);

        when(llm.ask(org.mockito.ArgumentMatchers.contains("For latest_news, infer URL arguments"), eq("model-a")))
                .thenReturn(Mono.just(response("""
                        {"tool":"latest_news","arguments":{"query":"AI policy India","language":"hi-IN","country":"IN"}}
                        """)));
        when(llm.ask(org.mockito.ArgumentMatchers.contains("Available information:\nAI policy India|hi-IN|IN"), eq("model-a")))
                .thenReturn(Mono.just(response("Here are the latest headlines.")));

        StepVerifier.create(router.handle("latest Hindi news about AI policy in India", "model-a"))
                .assertNext(response -> assertThat(response.firstMessageContent()).isEqualTo("Here are the latest headlines."))
                .verifyComplete();

        verify(llm).ask(org.mockito.ArgumentMatchers.contains("Available information:\nAI policy India|hi-IN|IN"), eq("model-a"));
    }

    @Test
    void handleCompactsNewsResultsBeforeAskingForFinalAnswer() {
        LLMService llm = mock(LLMService.class);
        String expectedLocalTime = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a z", Locale.US)
                .format(Instant.parse("2026-05-01T12:00:00Z").atZone(ZoneId.systemDefault()));
        NewsResult newsResult = new NewsResult(
                Instant.parse("2026-05-01T12:00:00Z"),
                "https://example.com/rss/search?q=kerala",
                List.of(
                        new NewsArticle("Kerala headline one", "Example Wire", "https://example.com/one", "Fri, 01 May 2026 12:00:00 GMT"),
                        new NewsArticle("Kerala headline two", "Example Wire", "https://example.com/two", "Fri, 01 May 2026 11:00:00 GMT")),
                null);
        SystemTool newsTool = tool("latest_news", "Return news", newsResult);
        MCPRouter router = new MCPRouter(new ToolRegistry(List.of(newsTool)), llm);

        when(llm.ask(org.mockito.ArgumentMatchers.contains("Choose the best matching tool"), eq("model-a")))
                .thenReturn(Mono.just(response("{\"tool\":\"latest_news\",\"arguments\":{\"query\":\"Kerala news\"}}")));
        when(llm.ask(org.mockito.ArgumentMatchers.contains("Kerala headline one"), eq("model-a")))
                .thenReturn(Mono.just(response("Kerala headline summary.")));

        StepVerifier.create(router.handle("latest news from Kerala", "model-a"))
                .assertNext(response -> assertThat(response.firstMessageContent()).isEqualTo("Kerala headline summary."))
                .verifyComplete();

        verify(llm).ask(org.mockito.ArgumentMatchers.contains(
                "Headlines:\n- [published: " + expectedLocalTime + "] Kerala headline one"), eq("model-a"));
        verify(llm).ask(org.mockito.ArgumentMatchers.contains("[rss: https://example.com/rss/search?q=kerala]"), eq("model-a"));
        verify(llm).ask(org.mockito.ArgumentMatchers.contains("include the published date/time and RSS feed"), eq("model-a"));
        verify(llm).ask(org.mockito.ArgumentMatchers.contains("Do not include reasoning"), eq("model-a"));
    }

    @Test
    void registryFindsRegisteredToolsByName() {
        SystemTool ramTool = tool("ram_usage", "Return RAM usage", "ram-result");
        ToolRegistry registry = new ToolRegistry(List.of(ramTool));

        assertThat(registry.hasTool("ram_usage")).isTrue();
        assertThat(registry.hasTool("disk_usage")).isFalse();
        assertThat(registry.getTool("ram_usage")).isSameAs(ramTool);
        assertThat(registry.getTool("disk_usage")).isNull();
    }

    private static SystemTool tool(String name, String description, Object result) {
        return new SystemTool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String version() {
                return "v1";
            }

            @Override
            public String description() {
                return description;
            }

            @Override
            public Object execute() {
                return result;
            }
        };
    }

    private static LLMResponse response(String content) {
        LLMResponse.Message message = new LLMResponse.Message();
        message.setRole("assistant");
        message.setContent(content);

        LLMResponse.Choice choice = new LLMResponse.Choice();
        choice.setIndex(0);
        choice.setMessage(message);
        choice.setFinishReason("stop");

        LLMResponse response = new LLMResponse();
        response.setObject("chat.completion");
        response.setChoices(List.of(choice));
        return response;
    }
}
