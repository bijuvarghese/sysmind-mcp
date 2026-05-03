package com.bxv.sysmindmcp.tools;

import com.bxv.sysmindmcp.chroma.ChromaService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class ChromaStatusTool implements SystemTool {
    private final ChromaService chromaService;

    @Override
    public String name() {
        return "chroma_status";
    }

    @Override
    public String version() {
        return "v1";
    }

    @Override
    public String description() {
        return "Check whether the Chroma vector database is reachable";
    }

    @Override
    public Object execute() {
        return chromaService.status().block();
    }
}
