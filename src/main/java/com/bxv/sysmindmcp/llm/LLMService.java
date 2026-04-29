package com.bxv.sysmindmcp.llm;

import com.bxv.sysmindmcp.config.AppConfig;
import com.bxv.sysmindmcp.model.ModelListResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Service
public class LLMService {
        private final WebClient client;
        private final String model;

        public LLMService(AppConfig appConfig,
                        @Value("${llm.model:google/gemma-4-e4b}") String model) {
                this.client = WebClient.create(appConfig.getLLMUrl());
                this.model = model;
        }

        public Mono<String> ask(String prompt, String requestedModel) {
                String targetModel = (requestedModel != null && !requestedModel.trim().isEmpty()) ? requestedModel : this.model;
                return client.post()
                                .uri("/v1/chat/completions")
                                .bodyValue(Map.of(
                                                "model", targetModel,
                                                "messages", List.of(
                                                                Map.of("role", "user", "content", prompt))))
                                .retrieve()
                                .bodyToMono(String.class);
        }

        public Mono<ModelListResponse> models() {
                var response = client.get()
                                .uri("/v1/models")
                                .retrieve()
                                .bodyToMono(ModelListResponse.class);
                System.out.println(response);
                return response;
        }
}