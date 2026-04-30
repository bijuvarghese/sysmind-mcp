package com.bxv.sysmindmcp.core;

import com.bxv.sysmindmcp.tools.SystemTool;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
@Getter
public class ToolRegistry {
    private final List<SystemTool> tools;

    public SystemTool getTool(String name) {
        return tools.stream()
                .filter(t -> t.name().equals(name))
                .findFirst()
                .orElse(null);
    }

    public boolean hasTool(String name) {
        return tools.stream().anyMatch(t -> t.name().equals(name));
    }
}
