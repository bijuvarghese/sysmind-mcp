package com.bxv.sysmindmcp.chroma;

import com.bxv.sysmindmcp.config.ChromaConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChromaServiceTest {

    @Test
    void statusReturnsHealthyChromaDetails() {
        List<String> requestedPaths = new ArrayList<>();
        WebClient client = WebClient.builder()
                .baseUrl("http://localhost:8000")
                .exchangeFunction(request -> {
                    requestedPaths.add(request.url().getPath());
                    return Mono.just(responseFor(request));
                })
                .build();
        ChromaService service = new ChromaService(client, config());

        StepVerifier.create(service.status())
                .assertNext(status -> {
                    assertThat(status.healthy()).isTrue();
                    assertThat(status.url()).isEqualTo("http://localhost:8000");
                    assertThat(status.tenant()).isEqualTo("default_tenant");
                    assertThat(status.database()).isEqualTo("default_database");
                    assertThat(status.collection()).isEqualTo("sysmind");
                    assertThat(status.version()).isEqualTo("1.0.0");
                    assertThat(status.healthcheck()).contains("is_executor_ready");
                    assertThat(status.error()).isNull();
                })
                .verifyComplete();

        assertThat(requestedPaths).containsExactlyInAnyOrder("/api/v2/healthcheck", "/api/v2/version");
    }

    @Test
    void statusReturnsUnhealthyWhenChromaCannotBeReached() {
        WebClient client = WebClient.builder()
                .exchangeFunction(request -> Mono.error(new IllegalStateException("connection refused")))
                .build();
        ChromaService service = new ChromaService(client, config());

        StepVerifier.create(service.status())
                .assertNext(status -> {
                    assertThat(status.healthy()).isFalse();
                    assertThat(status.error()).contains("connection refused");
                })
                .verifyComplete();
    }

    private ClientResponse responseFor(ClientRequest request) {
        String path = request.url().getPath();
        if ("/api/v2/version".equals(path)) {
            return ClientResponse
                    .create(HttpStatus.OK)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body("\"1.0.0\"")
                    .build();
        }

        return ClientResponse
                .create(HttpStatus.OK)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body("{\"is_executor_ready\":true,\"is_log_client_ready\":true}")
                .build();
    }

    private ChromaConfig config() {
        ChromaConfig config = new ChromaConfig();
        config.setUrl("http://localhost:8000");
        config.setTimeout(Duration.ofSeconds(1));
        config.setTenant("default_tenant");
        config.setDatabase("default_database");
        config.setCollection("sysmind");
        return config;
    }
}
