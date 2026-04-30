package com.bxv.sysmindmcp.llm;

import com.bxv.sysmindmcp.config.AppConfig;
import com.bxv.sysmindmcp.model.ModelListResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Service
public class LLMService {
        private static final Logger log = LoggerFactory.getLogger(LLMService.class);

        private final WebClient client;
        private final String model;
        private final Duration timeout;

        @Autowired
        public LLMService(AppConfig appConfig,
                        @Value("${llm.model:google/gemma-4-e4b}") String model) {
                this(WebClient.builder().baseUrl(appConfig.getUrl()).build(), model, appConfig.getTimeout());
        }

        LLMService(WebClient client, String model, Duration timeout) {
                this.client = client;
                this.model = model;
                this.timeout = timeout;
        }

        public Mono<String> ask(String prompt, String requestedModel) {
                String targetModel = (requestedModel != null && !requestedModel.trim().isEmpty()) ? requestedModel : this.model;
                int promptLength = prompt == null ? 0 : prompt.length();
                log.debug("Sending LLM chat completion request. model={}, promptLength={}", targetModel, promptLength);

                return client.post()
                                .uri("/v1/chat/completions")
                                .bodyValue(Map.of(
                                                "model", targetModel,
                                                "messages", List.of(
                                                                Map.of("role", "user", "content", prompt))))
                                .retrieve()
                                .bodyToMono(String.class)
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
