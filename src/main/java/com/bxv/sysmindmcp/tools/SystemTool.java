package com.bxv.sysmindmcp.tools;

import java.util.Map;

public interface SystemTool {
    String name();
    String version();
    String description();
    Object execute();

    default Object execute(String prompt) {
        return execute();
    }

    default Object execute(String prompt, Map<String, String> arguments) {
        return execute(prompt);
    }
}
