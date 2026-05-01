package com.bxv.sysmindmcp.controller;

import com.bxv.sysmindmcp.core.MCPRouter;
import com.bxv.sysmindmcp.llm.LLMService;
import com.bxv.sysmindmcp.model.AgentRequest;
import com.bxv.sysmindmcp.model.LLMResponse;
import com.bxv.sysmindmcp.model.ModelListResponse;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@AllArgsConstructor
public class AgentController {

    private final MCPRouter router;
    private final LLMService llmService;

    @PostMapping("/agent")
    public Mono<LLMResponse> run(@Valid @RequestBody AgentRequest request) {
        return router.handle(request.prompt(), request.model());
    }

    @GetMapping(value = "/v1/models", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ModelListResponse> models() {
        return llmService.models();
    }
}
