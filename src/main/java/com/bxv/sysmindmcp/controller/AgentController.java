package com.bxv.sysmindmcp.controller;

import com.bxv.sysmindmcp.core.MCPRouter;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/agent")
@AllArgsConstructor
public class AgentController {

    private final MCPRouter router;

    @PostMapping
    public Mono<String> run(@RequestBody Map<String, String> body) {
        return router.handle(body.get("prompt"));
    }


}
