package com.bxv.sysmindmcp.llm;

import com.bxv.sysmindmcp.config.AppConfig;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@AllArgsConstructor
@Service
public class LLMService {
    // private AppConfig appConfig;
    private final WebClient client = WebClient.create("http://127.0.0.1:1234");

    public Mono<String> ask(String prompt) {
        return client.post()
                .uri("/v1/chat/completions")
                .bodyValue(Map.of(
                        "model", "gemma-4b",
                        "messages", List.of(
                                Map.of("role", "user", "content", prompt)
                        )
                ))
                .retrieve()
                .bodyToMono(String.class);
    }
}
