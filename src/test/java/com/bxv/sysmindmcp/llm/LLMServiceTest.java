package com.bxv.sysmindmcp.llm;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
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
                            .body("ok")
                            .build());
                })
                .build();
        LLMService service = new LLMService(client, "default-model", Duration.ofSeconds(1), "/custom/chat");

        StepVerifier.create(service.ask("hello", null))
                .expectNext("ok")
                .verifyComplete();

        assertThat(capturedRequest.get().url().getPath()).isEqualTo("/custom/chat");
    }
}
