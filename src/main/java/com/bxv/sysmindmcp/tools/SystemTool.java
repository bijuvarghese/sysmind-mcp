package com.bxv.sysmindmcp.tools;

public interface SystemTool {
    String name();
    String version();
    String description();
    Object execute();

    default Object execute(String prompt) {
        return execute();
    }
}
