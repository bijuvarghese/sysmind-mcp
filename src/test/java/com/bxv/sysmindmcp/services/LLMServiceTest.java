package com.bxv.sysmindmcp.services;

import com.bxv.sysmindmcp.model.LLMResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class LLMServiceTest {

    @Test
    void askMapsTimeoutToGatewayTimeout() {
        WebClient client = WebClient.builder()
                .exchangeFunction(request -> Mono.never())
                .build();
        LLMService service = new LLMService(client, "default-model", Duration.ofMillis(10), "/chat");

        StepVerifier.create(service.ask("hello", null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ResponseStatusException.class);
                    ResponseStatusException exception = (ResponseStatusException) error;
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
                    assertThat(exception.getReason()).contains("timed out");
                })
                .verify();
    }

    @Test
    void askMapsUpstreamStatusToBadGateway() {
        WebClient client = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse
                        .create(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("upstream failure")
                        .build()))
                .build();
        LLMService service = new LLMService(client, "default-model", Duration.ofSeconds(1), "/chat");

        StepVerifier.create(service.ask("hello", null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ResponseStatusException.class);
                    ResponseStatusException exception = (ResponseStatusException) error;
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(exception.getReason()).contains("500");
                })
                .verify();
    }

    @Test
    void askUsesConfiguredChatCompletionsPath() {
        AtomicReference<ClientRequest> capturedRequest = new AtomicReference<>();
        WebClient client = WebClient.builder()
                .baseUrl("http://localhost")
                .exchangeFunction(request -> {
                    capturedRequest.set(request);
                    return Mono.just(ClientResponse
                            .create(HttpStatus.OK)
                            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .body(chatCompletionBody("ok"))
                            .build());
                })
                .build();
        LLMService service = new LLMService(client, "default-model", Duration.ofSeconds(1), "/custom/chat");

        StepVerifier.create(service.ask("hello", null))
                .assertNext(response -> assertThat(response.firstMessageContent()).isEqualTo("ok"))
                .verifyComplete();

        assertThat(capturedRequest.get().url().getPath()).isEqualTo("/custom/chat");
    }

    @Test
    void askReturnsAssistantMessageContent() {
        WebClient client = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse
                        .create(HttpStatus.OK)
                        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .body("""
                                {
                                  "id": "chatcmpl-1u8tylasex3cplqy230dcv",
                                  "object": "chat.completion",
                                  "created": 1777647372,
                                  "model": "google/gemma-4-e4b",
                                  "choices": [
                                    {
                                      "index": 0,
                                      "message": {
                                        "role": "assistant",
                                        "content": "Hello! How can I help you today?",
                                        "reasoning_content": "Thinking Process",
                                        "tool_calls": []
                                      },
                                      "logprobs": null,
                                      "finish_reason": "stop"
                                    }
                                  ],
                                  "usage": {
                                    "prompt_tokens": 17,
                                    "completion_tokens": 188,
                                    "total_tokens": 205,
                                    "completion_tokens_details": {
                                      "reasoning_tokens": 174
                                    }
                                  },
                                  "stats": {},
                                  "system_fingerprint": "google/gemma-4-e4b"
                                }
                                """)
                        .build()))
                .build();
        LLMService service = new LLMService(client, "default-model", Duration.ofSeconds(1), "/chat");

        StepVerifier.create(service.ask("hello", null))
                .assertNext(response -> {
                    assertThat(response).isInstanceOf(LLMResponse.class);
                    assertThat(response.getId()).isEqualTo("chatcmpl-1u8tylasex3cplqy230dcv");
                    assertThat(response.firstMessageContent()).isEqualTo("Hello! How can I help you today?");
                    assertThat(response.getChoices().get(0).getMessage().getReasoningContent())
                            .isEqualTo("Thinking Process");
                    assertThat(response.getUsage().getCompletionTokensDetails().getReasoningTokens()).isEqualTo(174);
                })
                .verifyComplete();
    }

    private String chatCompletionBody(String content) {
        return """
                {
                  "id": "chatcmpl-test",
                  "object": "chat.completion",
                  "created": 1777647372,
                  "model": "default-model",
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "%s"
                      },
                      "finish_reason": "stop"
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 1,
                    "completion_tokens": 1,
                    "total_tokens": 2
                  }
                }
                """.formatted(content);
    }
}
