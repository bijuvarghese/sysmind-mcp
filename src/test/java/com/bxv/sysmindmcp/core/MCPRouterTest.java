package com.bxv.sysmindmcp.core;

import com.bxv.sysmindmcp.llm.LLMService;
import com.bxv.sysmindmcp.tools.SystemTool;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

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

        String decisionResponse = """
                {"choices":[{"message":{"content":"{\\"tool\\":\\"ram_usage\\"}"}}]}
                """;
        when(llm.ask(org.mockito.ArgumentMatchers.contains("Choose the best matching tool"), eq("model-a")))
                .thenReturn(Mono.just(decisionResponse));
        when(llm.ask(org.mockito.ArgumentMatchers.contains("Tool: ram_usage"), eq("model-a")))
                .thenReturn(Mono.just("RAM usage is healthy."));

        StepVerifier.create(router.handle("How much memory is available?", "model-a"))
                .expectNext("RAM usage is healthy.")
                .verifyComplete();

        verify(llm).ask(org.mockito.ArgumentMatchers.contains("User request:\nHow much memory is available?"), eq("model-a"));
        verify(llm).ask(org.mockito.ArgumentMatchers.contains("Result: free=10,total=20,used=10"), eq("model-a"));
    }

    @Test
    void handleAsksLlmWithoutToolDataWhenSelectedToolIsUnknown() {
        LLMService llm = mock(LLMService.class);
        SystemTool diskTool = tool("disk_usage", "Return disk usage", "disk-result");
        MCPRouter router = new MCPRouter(new ToolRegistry(List.of(diskTool)), llm);

        when(llm.ask(org.mockito.ArgumentMatchers.contains("Choose the best matching tool"), eq(null)))
                .thenReturn(Mono.just("{\"tool\":\"not_registered\"}"));
        when(llm.ask("Check something unsupported", null))
                .thenReturn(Mono.just("Direct LLM response."));

        StepVerifier.create(router.handle("Check something unsupported", null))
                .expectNext("Direct LLM response.")
                .verifyComplete();

        verify(llm).ask("Check something unsupported", null);
    }

    @Test
    void handleAsksLlmWithoutToolDataWhenToolDecisionCannotBeParsed() {
        LLMService llm = mock(LLMService.class);
        SystemTool diskTool = tool("disk_usage", "Return disk usage", "disk-result");
        MCPRouter router = new MCPRouter(new ToolRegistry(List.of(diskTool)), llm);

        when(llm.ask(org.mockito.ArgumentMatchers.contains("Choose the best matching tool"), eq(null)))
                .thenReturn(Mono.just("not-json"));
        when(llm.ask("Explain the system status", null))
                .thenReturn(Mono.just("Direct LLM response."));

        StepVerifier.create(router.handle("Explain the system status", null))
                .expectNext("Direct LLM response.")
                .verifyComplete();

        verify(llm).ask("Explain the system status", null);
    }

    @Test
    void handleAsksLlmWithoutToolDataWhenNoToolApplies() {
        LLMService llm = mock(LLMService.class);
        SystemTool diskTool = tool("disk_usage", "Return disk usage", "disk-result");
        MCPRouter router = new MCPRouter(new ToolRegistry(List.of(diskTool)), llm);

        when(llm.ask(org.mockito.ArgumentMatchers.contains("If no tool applies"), eq(null)))
                .thenReturn(Mono.just("{\"tool\":\"none\"}"));
        when(llm.ask("Tell me a joke", null))
                .thenReturn(Mono.just("Direct LLM response."));

        StepVerifier.create(router.handle("Tell me a joke", null))
                .expectNext("Direct LLM response.")
                .verifyComplete();

        verify(llm).ask("Tell me a joke", null);
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
}
