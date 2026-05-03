package com.bxv.sysmindmcp.chroma;

import com.bxv.sysmindmcp.config.ChromaConfig;
import com.bxv.sysmindmcp.model.ChromaStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class ChromaService {
    private final WebClient client;
    private final ChromaConfig config;

    public ChromaService(@Qualifier("chromaWebClient") WebClient client, ChromaConfig config) {
        this.client = client;
        this.config = config;
    }

    public Mono<ChromaStatus> status() {
        return Mono.zip(get("/api/v2/healthcheck"), get("/api/v2/version"))
                .map(response -> ChromaStatus.healthy(
                        config.getUrl(),
                        config.getTenant(),
                        config.getDatabase(),
                        config.getCollection(),
                        normalize(response.getT2()),
                        normalize(response.getT1())))
                .timeout(config.getTimeout())
                .onErrorResume(error -> Mono.just(ChromaStatus.unhealthy(
                        config.getUrl(),
                        config.getTenant(),
                        config.getDatabase(),
                        config.getCollection(),
                        error.getMessage())));
    }

    private Mono<String> get(String path) {
        return client.get()
                .uri(path)
                .retrieve()
                .bodyToMono(String.class);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }

        return trimmed;
    }
}
