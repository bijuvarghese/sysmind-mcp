package com.bxv.sysmindmcp.controller;

import com.bxv.sysmindmcp.core.MCPRouter;
import com.bxv.sysmindmcp.services.LLMService;
import com.bxv.sysmindmcp.model.LLMResponse;
import com.bxv.sysmindmcp.model.ModelInfo;
import com.bxv.sysmindmcp.model.ModelListResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentControllerTest {

    @Test
    void agentEndpointPassesPromptAndModelToRouter() {
        MCPRouter router = mock(MCPRouter.class);
        LLMService llmService = mock(LLMService.class);
        WebTestClient client = WebTestClient.bindToController(new AgentController(router, llmService)).build();

        when(router.handle("status please", "model-a")).thenReturn(Mono.just(response("agent response")));

        client.post()
                .uri("/agent")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"prompt":"status please","model":"model-a"}
                """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("chat.completion")
                .jsonPath("$.choices[0].message.content").isEqualTo("agent response");

        verify(router).handle(eq("status please"), eq("model-a"));
    }

    @Test
    void agentEndpointReturnsBadRequestWhenPromptIsMissing() {
        MCPRouter router = mock(MCPRouter.class);
        LLMService llmService = mock(LLMService.class);
        WebTestClient client = WebTestClient.bindToController(new AgentController(router, llmService)).build();

        client.post()
                .uri("/agent")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"model":"model-a"}
                        """)
                .exchange()
                .expectStatus().isBadRequest();

        verify(router, never()).handle(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void agentEndpointReturnsBadRequestWhenPromptIsBlank() {
        MCPRouter router = mock(MCPRouter.class);
        LLMService llmService = mock(LLMService.class);
        WebTestClient client = WebTestClient.bindToController(new AgentController(router, llmService)).build();

        client.post()
                .uri("/agent")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"prompt":"   ","model":"model-a"}
                        """)
                .exchange()
                .expectStatus().isBadRequest();

        verify(router, never()).handle(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void modelsEndpointReturnsModelListFromLlmService() {
        MCPRouter router = mock(MCPRouter.class);
        LLMService llmService = mock(LLMService.class);
        WebTestClient client = WebTestClient.bindToController(new AgentController(router, llmService)).build();

        ModelInfo modelInfo = new ModelInfo();
        modelInfo.setId("local-model");
        modelInfo.setObject("model");
        modelInfo.setOwnedBy("local");

        ModelListResponse response = new ModelListResponse();
        response.setObject("list");
        response.setData(List.of(modelInfo));

        when(llmService.models()).thenReturn(Mono.just(response));

        client.get()
                .uri("/v1/models")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("application/json")
                .expectBody()
                .jsonPath("$.object").isEqualTo("list")
                .jsonPath("$.data[0].id").isEqualTo("local-model")
                .jsonPath("$.data[0].owned_by").isEqualTo("local");
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
