package com.bxv.sysmindmcp.controller;

import com.bxv.sysmindmcp.core.MCPRouter;
import com.bxv.sysmindmcp.llm.LLMService;
import com.bxv.sysmindmcp.model.ModelListResponse;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@AllArgsConstructor
public class AgentController {

    private final MCPRouter router;
    private final LLMService llmService;

    @PostMapping("/agent")
    public Mono<String> run(@RequestBody Map<String, String> body) {
        return router.handle(body.get("prompt"));
    }

    @GetMapping(value = "/v1/models", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ModelListResponse> models() {
        return llmService.models();
    }
}
