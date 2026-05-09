package com.bxv.sysmindmcp.tools;

import com.bxv.sysmindmcp.model.ProcessDiagnosticsResult;
import com.bxv.sysmindmcp.model.TopProcess;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Component
public class ProcessDiagnosticsTool implements SystemTool {
    @Override
    public String name() {
        return "process_diagnostics";
    }

    @Override
    public String version() {
        return "v1";
    }

    @Override
    public String description() {
        return "Fetch the process diagnostics";
    }

    @Override
    public Object execute() {
        return fetchProcessDiagnostics();
    }

    private Object fetchProcessDiagnostics() {
        List<ProcessHandle> processes = ProcessHandle.allProcesses().toList();
        List<TopProcess> topProcesses = processes.stream()
                .sorted(Comparator.comparing(ProcessDiagnosticsTool::cpuDuration).reversed())
                .limit(10)
                .map(this::toTopProcess)
                .toList();

        return ProcessDiagnosticsResult.builder()
                .processCount(processes.size())
                .topProcesses(topProcesses)
                .generatedAt(Instant.now().toString())
                .build();
    }

    private TopProcess toTopProcess(ProcessHandle process) {
        ProcessHandle.Info info = process.info();
        String command = info.commandLine()
                .or(() -> info.command())
                .orElse("unknown");

        return TopProcess.builder()
                .pid(process.pid())
                .name(processName(command))
                .command(command)
                .runtime(info.totalCpuDuration().map(ProcessDiagnosticsTool::formatDuration).orElse("unknown"))
                .user(info.user().orElse("unknown"))
                .category("process")
                .risk("unknown")
                .explanation("Sorted by total CPU time reported by the JVM process API.")
                .build();
    }

    private static Duration cpuDuration(ProcessHandle process) {
        return process.info().totalCpuDuration().orElse(Duration.ZERO);
    }

    private static String processName(String command) {
        int slash = Math.max(command.lastIndexOf('/'), command.lastIndexOf('\\'));
        return slash >= 0 && slash + 1 < command.length() ? command.substring(slash + 1) : command;
    }

    private static String formatDuration(Duration duration) {
        long seconds = duration.toSeconds();
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainingSeconds = seconds % 60;
        return "%d:%02d:%02d".formatted(hours, minutes, remainingSeconds);
    }
}
