package com.bxv.sysmindmcp.mcp;

import com.bxv.sysmindmcp.tools.ChromaStatusTool;
import com.bxv.sysmindmcp.tools.MachineStatusTool;
import com.bxv.sysmindmcp.tools.NewsTool;
import com.bxv.sysmindmcp.tools.ProcessDiagnosticsTool;
import lombok.AllArgsConstructor;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@AllArgsConstructor
public class SysMindMcpTools {
    private final NewsTool newsTool;
    private final ChromaStatusTool chromaStatusTool;
    private final MachineStatusTool machineStatusTool;
    private final ProcessDiagnosticsTool processDiagnosticsTool;

    @McpTool(
            name = "latest_news",
            description = "Fetch current news headlines from the configured RSS feed.",
            annotations = @McpTool.McpAnnotations(
                    title = "Latest News",
                    readOnlyHint = true,
                    destructiveHint = false,
                    openWorldHint = true))
    public Object latestNews(
            @McpArg(name = "query", description = "Optional news search query, such as a topic or location.")
            @McpToolParam(required = false, description = "Optional news search query, such as a topic or location.")
            String query,
            @McpArg(name = "language", description = "Optional Google News language/locale, for example en-US.")
            @McpToolParam(required = false, description = "Optional Google News language/locale, for example en-US.")
            String language,
            @McpArg(name = "country", description = "Optional country focus, for example US, CA, or IN.")
            @McpToolParam(required = false, description = "Optional country focus, for example US, CA, or IN.")
            String country,
            @McpArg(name = "ceid", description = "Optional Google News CEID value, for example US:en.")
            @McpToolParam(required = false, description = "Optional Google News CEID value, for example US:en.")
            String ceid) {
        return newsTool.execute(null, arguments(
                "query", query,
                "language", language,
                "country", country,
                "ceid", ceid));
    }

    @McpTool(
            name = "chroma_status",
            description = "Check whether the Chroma vector database is reachable.",
            annotations = @McpTool.McpAnnotations(
                    title = "Chroma Status",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public Object chromaStatus() {
        return chromaStatusTool.execute();
    }

    @McpTool(
            name = "machine_status",
            description = "Return the host computer name, OS, CPU, RAM, storage, and uptime details.",
            annotations = @McpTool.McpAnnotations(
                    title = "Machine Status",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public Object machineStatus() {
        return machineStatusTool.execute();
    }

    @McpTool(
            name = "process_diagnostics",
            description = "Return a read-only snapshot of host process diagnostics.",
            annotations = @McpTool.McpAnnotations(
                    title = "Process Diagnostics",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public Object processDiagnostics() {
        return processDiagnosticsTool.execute();
    }

    private Map<String, String> arguments(String... pairs) {
        Map<String, String> arguments = new HashMap<>();

        for (int i = 0; i < pairs.length; i += 2) {
            String value = pairs[i + 1];

            if (value != null && !value.isBlank()) {
                arguments.put(pairs[i], value.trim());
            }
        }

        return arguments;
    }
}
