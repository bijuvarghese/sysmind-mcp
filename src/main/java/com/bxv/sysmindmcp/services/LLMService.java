package com.bxv.sysmindmcp.services;

import com.bxv.sysmindmcp.config.AppConfig;
import com.bxv.sysmindmcp.model.ChatCompletionRequest;
import com.bxv.sysmindmcp.model.LLMResponse;
import com.bxv.sysmindmcp.model.ModelListResponse;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Service
@Slf4j
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class LLMService {
        private final WebClient client;
        private final String model;
        private final Duration timeout;
        private final String chatCompletionsPath;

        @Autowired
        public LLMService(@Qualifier("llmWebClient") WebClient llmWebClient,
                        AppConfig appConfig,
                        @Value("${llm.model}") String model) {
                this(llmWebClient,
                                model,
                                appConfig.getTimeout(),
                                appConfig.getChatCompletionsPath());
        }

        public Mono<LLMResponse> ask(String prompt, String requestedModel) {
                String targetModel = (requestedModel != null && !requestedModel.trim().isEmpty()) ? requestedModel : this.model;
                int promptLength = prompt == null ? 0 : prompt.length();
                log.debug("Sending LLM chat completion request. model={}, promptLength={}", targetModel, promptLength);

                return client.post()
                                .uri(chatCompletionsPath)
                                .bodyValue(ChatCompletionRequest.userMessage(targetModel, prompt))
                                .retrieve()
                                .bodyToMono(LLMResponse.class)
                                .timeout(timeout)
                                .onErrorMap(this::shouldMapUpstreamError, this::toResponseStatusException);
        }

        public Mono<ModelListResponse> models() {
                log.debug("Fetching LLM model list.");
                return client.get()
                                .uri("/v1/models")
                                .retrieve()
                                .bodyToMono(ModelListResponse.class)
                                .timeout(timeout)
                                .onErrorMap(this::shouldMapUpstreamError, this::toResponseStatusException);
        }

        private boolean shouldMapUpstreamError(Throwable error) {
                return !(error instanceof ResponseStatusException);
        }

        private ResponseStatusException toResponseStatusException(Throwable error) {
                if (error instanceof TimeoutException) {
                        return new ResponseStatusException(
                                        HttpStatus.GATEWAY_TIMEOUT,
                                        "LLM request timed out after " + timeout.toMillis() + "ms.",
                                        error);
                }

                if (error instanceof WebClientResponseException responseException) {
                        return new ResponseStatusException(
                                        HttpStatus.BAD_GATEWAY,
                                        "LLM upstream returned status " + responseException.getStatusCode().value() + ".",
                                        error);
                }

                if (error instanceof WebClientRequestException) {
                        return new ResponseStatusException(
                                        HttpStatus.BAD_GATEWAY,
                                        "Unable to reach LLM upstream.",
                                        error);
                }

                return new ResponseStatusException(
                                HttpStatus.BAD_GATEWAY,
                                "LLM request failed.",
                                error);
        }
}
