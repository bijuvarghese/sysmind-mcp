package com.bxv.sysmindmcp.tools;

import com.bxv.sysmindmcp.model.MachineStatus;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

@Component
@AllArgsConstructor
public class MachineStatusTool implements SystemTool {
    private static final double BYTES_PER_GB = 1024D * 1024D * 1024D;
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    @Override
    public String name() {
        return "machine_status";
    }

    @Override
    public String version() {
        return "v1";
    }

    @Override
    public String description() {
        return "Return host CPU, memory, storage, operating system, and uptime details.";
    }

    @Override
    public Object execute() {
        try {
            com.sun.management.OperatingSystemMXBean osBean =
                    ManagementFactory.getPlatformMXBean(com.sun.management.OperatingSystemMXBean.class);
            CpuSnapshot cpu = cpuSnapshot(osBean);
            StorageSnapshot storage = storageSnapshot();
            RuntimeSnapshot runtime = runtimeSnapshot();

            return MachineStatus.builder()
                    .computerName(computerName())
                    .operatingSystem(operatingSystem())
                    .machineType(System.getProperty("os.arch", "Unknown"))
                    .processor(cpu.processorName())
                    .processorDetails(MachineStatus.CpuDetails.builder()
                            .physicalCpuCores(cpu.physicalCores())
                            .logicalCpuCores(cpu.logicalCores())
                            .currentCpuSpeedMhz(cpu.currentSpeedMhz())
                            .maxCpuSpeedMhz(cpu.maxSpeedMhz())
                            .currentCpuUsagePercent(round(percent(osBean.getCpuLoad())))
                            .build())
                    .memoryDetails(memoryDetails(osBean))
                    .storageDetails(storageDetails(storage))
                    .systemStatus(MachineStatus.RuntimeDetails.builder()
                            .lastStarted(DATE_TIME_FORMATTER.format(runtime.bootTime()))
                            .runningFor(formatDuration(runtime.uptime()))
                            .uptimeSeconds(runtime.uptime().toSeconds())
                            .build())
                    .generatedAt(DATE_TIME_FORMATTER.format(Instant.now()))
                    .build();
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }

    private MachineStatus.MemoryDetails memoryDetails(com.sun.management.OperatingSystemMXBean osBean) {
        long total = Math.max(0L, safelyRead(osBean::getTotalMemorySize));
        long available = Math.max(0L, safelyRead(osBean::getFreeMemorySize));
        long used = Math.max(0L, total - available);
        long swapTotal = Math.max(0L, safelyRead(osBean::getTotalSwapSpaceSize));
        long swapFree = Math.max(0L, safelyRead(osBean::getFreeSwapSpaceSize));
        long swapUsed = Math.max(0L, swapTotal - swapFree);
        double usagePercent = percent(total, used);

        return MachineStatus.MemoryDetails.builder()
                .totalBytes(total)
                .availableBytes(available)
                .usedBytes(used)
                .totalGb(gb(total))
                .availableGb(gb(available))
                .usedGb(gb(used))
                .usagePercent(round(usagePercent))
                .swapPageFileTotalBytes(swapTotal)
                .swapPageFileUsedBytes(swapUsed)
                .swapPageFileTotalGb(gb(swapTotal))
                .swapPageFileUsedGb(gb(swapUsed))
                .swapPageFileUsagePercent(round(percent(swapTotal, swapUsed)))
                .status(memoryStatus(usagePercent))
                .build();
    }

    private MachineStatus.StorageDetails storageDetails(StorageSnapshot storage) {
        double usagePercent = percent(storage.totalBytes(), storage.usedBytes());

        return MachineStatus.StorageDetails.builder()
                .totalBytes(storage.totalBytes())
                .freeBytes(storage.freeBytes())
                .usedBytes(storage.usedBytes())
                .totalGb(gb(storage.totalBytes()))
                .freeGb(gb(storage.freeBytes()))
                .usedGb(gb(storage.usedBytes()))
                .usagePercent(round(usagePercent))
                .status(storageStatus(usagePercent))
                .build();
    }

    private CpuSnapshot cpuSnapshot(com.sun.management.OperatingSystemMXBean osBean) {
        int logicalCores = Math.max(1, osBean.getAvailableProcessors());
        String osName = System.getProperty("os.name", "").toLowerCase();

        if (osName.contains("win")) {
            return windowsCpuSnapshot(logicalCores);
        }
        if (osName.contains("mac") || osName.contains("darwin")) {
            return macCpuSnapshot(logicalCores);
        }
        return linuxCpuSnapshot(logicalCores);
    }

    private CpuSnapshot windowsCpuSnapshot(int fallbackLogicalCores) {
        Optional<String> output = runCommand(Duration.ofSeconds(2),
                "powershell.exe",
                "-NoProfile",
                "-Command",
                "$cpu=Get-CimInstance Win32_Processor | Select-Object -First 1; @($cpu.Name,$cpu.NumberOfCores,$cpu.NumberOfLogicalProcessors,$cpu.CurrentClockSpeed,$cpu.MaxClockSpeed) -join \"`n\"");

        if (output.isPresent()) {
            String[] lines = output.get().lines().map(String::trim).toArray(String[]::new);
            return new CpuSnapshot(
                    valueAt(lines, 0, processorFromEnvironment()),
                    parseInt(valueAt(lines, 1, ""), fallbackLogicalCores),
                    parseInt(valueAt(lines, 2, ""), fallbackLogicalCores),
                    parseLong(valueAt(lines, 3, ""), 0L),
                    parseLong(valueAt(lines, 4, ""), 0L));
        }

        return new CpuSnapshot(processorFromEnvironment(), fallbackLogicalCores, fallbackLogicalCores, 0L, 0L);
    }

    private CpuSnapshot macCpuSnapshot(int fallbackLogicalCores) {
        String processor = runCommand(Duration.ofSeconds(1), "sysctl", "-n", "machdep.cpu.brand_string")
                .orElse(processorFromEnvironment());
        int physicalCores = runCommand(Duration.ofSeconds(1), "sysctl", "-n", "hw.physicalcpu")
                .map(value -> parseInt(value, fallbackLogicalCores))
                .orElse(fallbackLogicalCores);
        int logicalCores = runCommand(Duration.ofSeconds(1), "sysctl", "-n", "hw.logicalcpu")
                .map(value -> parseInt(value, fallbackLogicalCores))
                .orElse(fallbackLogicalCores);
        long currentSpeed = sysctlHzToMhz("hw.cpufrequency");
        long maxSpeed = sysctlHzToMhz("hw.cpufrequency_max");

        return new CpuSnapshot(processor, physicalCores, logicalCores, currentSpeed, maxSpeed);
    }

    private CpuSnapshot linuxCpuSnapshot(int fallbackLogicalCores) {
        String processor = firstCpuInfoValue("model name").orElse(processorFromEnvironment());
        long currentSpeed = firstCpuInfoValue("cpu MHz")
                .map(value -> Math.round(parseDouble(value, 0D)))
                .orElse(0L);
        long maxSpeed = readLong(Path.of("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq"))
                .map(khz -> Math.round(khz / 1000D))
                .orElse(0L);
        int physicalCores = lscpuPhysicalCores().orElse(fallbackLogicalCores);

        return new CpuSnapshot(processor, physicalCores, fallbackLogicalCores, currentSpeed, maxSpeed);
    }

    private Optional<Integer> lscpuPhysicalCores() {
        Optional<String> output = runCommand(Duration.ofSeconds(1), "lscpu");
        if (output.isEmpty()) {
            return Optional.empty();
        }

        int coresPerSocket = parseLscpuInt(output.get(), "Core(s) per socket:");
        int sockets = parseLscpuInt(output.get(), "Socket(s):");
        if (coresPerSocket > 0 && sockets > 0) {
            return Optional.of(coresPerSocket * sockets);
        }
        return Optional.empty();
    }

    private int parseLscpuInt(String output, String label) {
        return output.lines()
                .map(String::trim)
                .filter(line -> line.startsWith(label))
                .map(line -> line.substring(label.length()).trim())
                .findFirst()
                .map(value -> parseInt(value, 0))
                .orElse(0);
    }

    private Optional<String> firstCpuInfoValue(String key) {
        try {
            Path cpuInfo = Path.of("/proc/cpuinfo");
            if (!Files.isReadable(cpuInfo)) {
                return Optional.empty();
            }
            String prefix = key.toLowerCase() + ":";
            return Files.readAllLines(cpuInfo).stream()
                    .map(String::trim)
                    .filter(line -> line.toLowerCase().replace("\t", "").startsWith(prefix))
                    .map(line -> line.substring(line.indexOf(':') + 1).trim())
                    .filter(value -> !value.isBlank())
                    .findFirst();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private long sysctlHzToMhz(String key) {
        return runCommand(Duration.ofSeconds(1), "sysctl", "-n", key)
                .map(value -> Math.round(parseDouble(value, 0D) / 1_000_000D))
                .orElse(0L);
    }

    private String processorFromEnvironment() {
        return Optional.ofNullable(System.getenv("PROCESSOR_IDENTIFIER"))
                .or(() -> Optional.ofNullable(System.getenv("PROCESSOR_ARCHITECTURE")))
                .filter(value -> !value.isBlank())
                .orElse("Unknown");
    }

    private StorageSnapshot storageSnapshot() {
        long total = 0L;
        long free = 0L;

        File[] roots = File.listRoots();
        if (roots != null) {
            for (File root : roots) {
                long rootTotal = Math.max(0L, root.getTotalSpace());
                if (rootTotal > 0) {
                    total += rootTotal;
                    free += Math.max(0L, root.getFreeSpace());
                }
            }
        }

        long used = Math.max(0L, total - free);
        return new StorageSnapshot(total, free, used);
    }

    private RuntimeSnapshot runtimeSnapshot() {
        return detectedBootTime()
                .map(bootTime -> new RuntimeSnapshot(bootTime, Duration.between(bootTime, Instant.now())))
                .orElseGet(() -> {
                    RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
                    Instant started = Instant.ofEpochMilli(runtime.getStartTime());
                    return new RuntimeSnapshot(started, Duration.ofMillis(runtime.getUptime()));
                });
    }

    private Optional<Instant> detectedBootTime() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("win")) {
            return runCommand(Duration.ofSeconds(2),
                    "powershell.exe",
                    "-NoProfile",
                    "-Command",
                    "(Get-CimInstance Win32_OperatingSystem).LastBootUpTime.ToUniversalTime().ToString('o')")
                    .flatMap(this::parseInstant);
        }
        if (osName.contains("mac") || osName.contains("darwin")) {
            return runCommand(Duration.ofSeconds(1), "sysctl", "-n", "kern.boottime")
                    .flatMap(this::parseMacBootTime);
        }
        return linuxBootTime();
    }

    private Optional<Instant> linuxBootTime() {
        try {
            Path stat = Path.of("/proc/stat");
            if (!Files.isReadable(stat)) {
                return Optional.empty();
            }
            return Files.readAllLines(stat).stream()
                    .filter(line -> line.startsWith("btime "))
                    .findFirst()
                    .map(line -> line.substring("btime ".length()).trim())
                    .map(value -> Instant.ofEpochSecond(parseLong(value, 0L)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<Instant> parseMacBootTime(String output) {
        int secondsIndex = output.indexOf("sec = ");
        if (secondsIndex < 0) {
            return Optional.empty();
        }
        int start = secondsIndex + "sec = ".length();
        int end = output.indexOf(',', start);
        if (end < 0) {
            end = output.length();
        }
        long epochSeconds = parseLong(output.substring(start, end).trim(), 0L);
        return epochSeconds > 0 ? Optional.of(Instant.ofEpochSecond(epochSeconds)) : Optional.empty();
    }

    private Optional<Instant> parseInstant(String value) {
        try {
            return Optional.of(Instant.parse(value.trim()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String computerName() {
        return Optional.ofNullable(System.getenv("COMPUTERNAME"))
                .or(() -> Optional.ofNullable(System.getenv("HOSTNAME")))
                .filter(value -> !value.isBlank())
                .orElseGet(() -> {
                    try {
                        return java.net.InetAddress.getLocalHost().getHostName();
                    } catch (Exception e) {
                        return "Unknown";
                    }
                });
    }

    private String operatingSystem() {
        return "%s %s".formatted(
                System.getProperty("os.name", "Unknown"),
                System.getProperty("os.version", "")).trim();
    }

    static String formatDuration(Duration duration) {
        long seconds = Math.max(0L, duration.toSeconds());
        long days = seconds / 86_400;
        long hours = (seconds % 86_400) / 3_600;
        long minutes = (seconds % 3_600) / 60;
        long remainingSeconds = seconds % 60;

        return "%d days, %02d:%02d:%02d".formatted(days, hours, minutes, remainingSeconds);
    }

    private String memoryStatus(double usagePercent) {
        if (usagePercent >= 90D) {
            return "Critical - Memory usage is very high";
        }
        if (usagePercent >= 80D) {
            return "Warning - Memory usage is high";
        }
        return "Good - Memory is healthy";
    }

    private String storageStatus(double usagePercent) {
        if (usagePercent >= 95D) {
            return "Critical - Storage is almost full";
        }
        if (usagePercent >= 85D) {
            return "Warning - Storage is running low";
        }
        return "Good - Plenty of space available";
    }

    private long safelyRead(LongSupplier supplier) {
        try {
            return supplier.getAsLong();
        } catch (Throwable e) {
            return 0L;
        }
    }

    private Optional<String> runCommand(Duration timeout, String... command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return Optional.empty();
            }
            if (process.exitValue() != 0) {
                return Optional.empty();
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return output.isBlank() ? Optional.empty() : Optional.of(output);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<Long> readLong(Path path) {
        try {
            if (!Files.isReadable(path)) {
                return Optional.empty();
            }
            return Optional.of(parseLong(Files.readString(path).trim(), 0L));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String valueAt(String[] lines, int index, String fallback) {
        if (index >= lines.length || lines[index].isBlank()) {
            return fallback;
        }
        return lines[index];
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private double gb(long bytes) {
        return round(bytes / BYTES_PER_GB);
    }

    private double percent(long total, long used) {
        if (total <= 0L) {
            return 0D;
        }
        return (used * 100D) / total;
    }

    private double percent(double value) {
        if (Double.isNaN(value) || value < 0D) {
            return 0D;
        }
        return value * 100D;
    }

    private double round(double value) {
        return Math.round(value * 100D) / 100D;
    }

    private record CpuSnapshot(
            String processorName,
            int physicalCores,
            int logicalCores,
            long currentSpeedMhz,
            long maxSpeedMhz) {
    }

    private record StorageSnapshot(long totalBytes, long freeBytes, long usedBytes) {
    }

    private record RuntimeSnapshot(Instant bootTime, Duration uptime) {
        private RuntimeSnapshot {
            if (uptime.isNegative()) {
                uptime = Duration.ZERO;
            }
        }
    }
}
