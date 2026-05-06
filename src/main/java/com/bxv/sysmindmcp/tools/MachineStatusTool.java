package com.bxv.sysmindmcp.tools;

import com.bxv.sysmindmcp.model.MachineStatus;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.File;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.file.FileStore;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Component
@AllArgsConstructor
public class MachineStatusTool implements SystemTool {
    private static final double BYTES_PER_GB = 1024D * 1024D * 1024D;
    private static final Pattern TEMPERATURE_PATTERN =
            Pattern.compile("(-?\\d+(?:\\.\\d+)?)\\s*(?:°\\s*)?[Cc]\\b");
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
            LoadAverages loadAverages = loadAverages(osBean);

            return MachineStatus.builder()
                    .computerName(computerName())
                    .operatingSystem(operatingSystem())
                    .machineType(System.getProperty("os.arch", "Unknown"))
                    .processor(cpu.processorName())
                    .processorDetails(MachineStatus.CpuDetails.builder()
                            .totalCpuCores(cpu.totalCores())
                            .coreSummary(formatCoreSummary(cpu.totalCores(), cpu.performanceCores(), cpu.efficiencyCores()))
                            .physicalCpuCores(cpu.physicalCores())
                            .logicalCpuCores(cpu.logicalCores())
                            .performanceCpuCores(cpu.performanceCores())
                            .efficiencyCpuCores(cpu.efficiencyCores())
                            .currentCpuSpeedMhz(cpu.currentSpeedMhz())
                            .maxCpuSpeedMhz(cpu.maxSpeedMhz())
                            .currentCpuUsagePercent(round(percent(osBean.getCpuLoad())))
                            .loadAverageOneMinute(loadAverages.oneMinute())
                            .loadAverageFiveMinutes(loadAverages.fiveMinutes())
                            .loadAverageFifteenMinutes(loadAverages.fifteenMinutes())
                            .perCoreUsagePercent(perCoreUsagePercent())
                            .build())
                    .memoryDetails(memoryDetails(osBean))
                    .storageDetails(storageDetails(storage))
                    .systemTemperature(temperatureDetails())
                    .powerDetails(powerDetails())
                    .processDetails(processDetails())
                    .networkDetails(networkDetails())
                    .thermalDetails(thermalDetails())
                    .gpuDetails(gpuDetails())
                    .systemDetails(systemDetails(runtime))
                    .jvmDetails(jvmDetails())
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
        MemoryExtras memoryExtras = memoryExtras();

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
                .cachedBytes(memoryExtras.cachedBytes())
                .cachedGb(memoryExtras.cachedBytes() == null ? null : gb(memoryExtras.cachedBytes()))
                .wiredBytes(memoryExtras.wiredBytes())
                .wiredGb(memoryExtras.wiredBytes() == null ? null : gb(memoryExtras.wiredBytes()))
                .compressedBytes(memoryExtras.compressedBytes())
                .compressedGb(memoryExtras.compressedBytes() == null ? null : gb(memoryExtras.compressedBytes()))
                .pressure(memoryExtras.pressure())
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
                .volumes(storage.volumes())
                .readBytesPerSecond(storage.readBytesPerSecond())
                .writeBytesPerSecond(storage.writeBytesPerSecond())
                .throughputSource(storage.throughputSource())
                .smartHealth(storage.smartHealth())
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
                    0,
                    0,
                    parseLong(valueAt(lines, 3, ""), 0L),
                    parseLong(valueAt(lines, 4, ""), 0L));
        }

        return new CpuSnapshot(processorFromEnvironment(), fallbackLogicalCores, fallbackLogicalCores, 0, 0, 0L, 0L);
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
        CoreTypeSnapshot coreTypes = macCoreTypes();
        long currentSpeed = sysctlHzToMhz("hw.cpufrequency");
        long maxSpeed = sysctlHzToMhz("hw.cpufrequency_max");

        return new CpuSnapshot(
                processor,
                physicalCores,
                logicalCores,
                coreTypes.performanceCores(),
                coreTypes.efficiencyCores(),
                currentSpeed,
                maxSpeed);
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

        return new CpuSnapshot(processor, physicalCores, fallbackLogicalCores, 0, 0, currentSpeed, maxSpeed);
    }

    private CoreTypeSnapshot macCoreTypes() {
        int performanceCores = 0;
        int efficiencyCores = 0;

        for (int perfLevel = 0; perfLevel < 4; perfLevel++) {
            String prefix = "hw.perflevel" + perfLevel;
            Optional<Integer> cores = runCommand(Duration.ofSeconds(1), "sysctl", "-n", prefix + ".physicalcpu")
                    .map(value -> parseInt(value, 0))
                    .filter(value -> value > 0);
            if (cores.isEmpty()) {
                continue;
            }

            String name = runCommand(Duration.ofSeconds(1), "sysctl", "-n", prefix + ".name")
                    .orElse("")
                    .toLowerCase();
            if (name.contains("performance")) {
                performanceCores += cores.get();
            } else if (name.contains("efficiency")) {
                efficiencyCores += cores.get();
            } else if (perfLevel == 0) {
                performanceCores += cores.get();
            } else if (perfLevel == 1) {
                efficiencyCores += cores.get();
            }
        }

        return new CoreTypeSnapshot(performanceCores, efficiencyCores);
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
        List<MachineStatus.VolumeDetails> volumes = new ArrayList<>();

        File[] roots = File.listRoots();
        if (roots != null) {
            for (File root : roots) {
                long rootTotal = Math.max(0L, root.getTotalSpace());
                if (rootTotal > 0) {
                    long rootFree = Math.max(0L, root.getFreeSpace());
                    total += rootTotal;
                    free += rootFree;
                    volumes.add(volumeDetails(root, rootTotal, rootFree));
                }
            }
        }

        long used = Math.max(0L, total - free);
        DiskThroughput throughput = diskThroughput();
        return new StorageSnapshot(
                total,
                free,
                used,
                volumes,
                throughput.readBytesPerSecond(),
                throughput.writeBytesPerSecond(),
                throughput.source(),
                smartHealth());
    }

    private MachineStatus.VolumeDetails volumeDetails(File root, long totalBytes, long freeBytes) {
        long usedBytes = Math.max(0L, totalBytes - freeBytes);
        String name = root.getName();
        String type = "Unknown";
        try {
            FileStore fileStore = Files.getFileStore(root.toPath());
            name = fileStore.name().isBlank() ? root.getPath() : fileStore.name();
            type = fileStore.type().isBlank() ? "Unknown" : fileStore.type();
        } catch (Exception ignored) {
            name = name == null || name.isBlank() ? root.getPath() : name;
        }

        return MachineStatus.VolumeDetails.builder()
                .path(root.getPath())
                .name(name)
                .type(type)
                .totalBytes(totalBytes)
                .freeBytes(freeBytes)
                .usedBytes(usedBytes)
                .totalGb(gb(totalBytes))
                .freeGb(gb(freeBytes))
                .usedGb(gb(usedBytes))
                .usagePercent(round(percent(totalBytes, usedBytes)))
                .build();
    }

    private LoadAverages loadAverages(com.sun.management.OperatingSystemMXBean osBean) {
        Double oneMinute = osBean.getSystemLoadAverage() >= 0D ? round(osBean.getSystemLoadAverage()) : null;
        Optional<String> uptime = runCommand(Duration.ofSeconds(1), "uptime");
        if (uptime.isEmpty()) {
            return new LoadAverages(oneMinute, null, null);
        }

        String output = uptime.get();
        int loadIndex = output.indexOf("load average:");
        if (loadIndex < 0) {
            loadIndex = output.indexOf("load averages:");
        }
        if (loadIndex < 0) {
            return new LoadAverages(oneMinute, null, null);
        }

        String[] values = output.substring(output.indexOf(':', loadIndex) + 1)
                .trim()
                .split(",");
        if (values.length < 3) {
            return new LoadAverages(oneMinute, null, null);
        }
        return new LoadAverages(
                round(parseDouble(values[0], oneMinute == null ? 0D : oneMinute)),
                round(parseDouble(values[1], 0D)),
                round(parseDouble(values[2], 0D)));
    }

    private List<Double> perCoreUsagePercent() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("win")) {
            return windowsPerCoreUsage();
        }
        if (osName.contains("linux")) {
            return linuxPerCoreUsage();
        }
        return List.of();
    }

    private List<Double> windowsPerCoreUsage() {
        return runCommand(Duration.ofSeconds(3),
                "powershell.exe",
                "-NoProfile",
                "-Command",
                "Get-Counter '\\Processor(*)\\% Processor Time' | Select -ExpandProperty CounterSamples | Where-Object {$_.InstanceName -ne '_total'} | Sort-Object InstanceName | ForEach-Object {[math]::Round($_.CookedValue,2)}")
                .map(output -> output.lines()
                        .map(line -> parseDouble(line.trim(), Double.NaN))
                        .filter(value -> !Double.isNaN(value))
                        .map(this::round)
                        .toList())
                .orElseGet(List::of);
    }

    private List<Double> linuxPerCoreUsage() {
        List<CpuTimes> first = linuxCpuTimes();
        if (first.isEmpty()) {
            return List.of();
        }
        try {
            Thread.sleep(150L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        }
        List<CpuTimes> second = linuxCpuTimes();
        int count = Math.min(first.size(), second.size());
        List<Double> usage = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long totalDelta = second.get(i).total() - first.get(i).total();
            long idleDelta = second.get(i).idle() - first.get(i).idle();
            if (totalDelta > 0L) {
                usage.add(round(((totalDelta - idleDelta) * 100D) / totalDelta));
            }
        }
        return usage;
    }

    private List<CpuTimes> linuxCpuTimes() {
        try {
            Path stat = Path.of("/proc/stat");
            if (!Files.isReadable(stat)) {
                return List.of();
            }
            return Files.readAllLines(stat).stream()
                    .filter(line -> line.startsWith("cpu") && line.length() > 3 && Character.isDigit(line.charAt(3)))
                    .map(line -> line.trim().split("\\s+"))
                    .filter(parts -> parts.length >= 8)
                    .map(parts -> {
                        long user = parseLong(parts[1], 0L);
                        long nice = parseLong(parts[2], 0L);
                        long system = parseLong(parts[3], 0L);
                        long idle = parseLong(parts[4], 0L);
                        long iowait = parseLong(parts[5], 0L);
                        long irq = parseLong(parts[6], 0L);
                        long softirq = parseLong(parts[7], 0L);
                        long steal = parts.length > 8 ? parseLong(parts[8], 0L) : 0L;
                        return new CpuTimes(user + nice + system + idle + iowait + irq + softirq + steal, idle + iowait);
                    })
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private MemoryExtras memoryExtras() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("mac") || osName.contains("darwin")) {
            return macMemoryExtras();
        }
        if (osName.contains("linux")) {
            return linuxMemoryExtras();
        }
        return new MemoryExtras(null, null, null, "Unknown");
    }

    private MemoryExtras macMemoryExtras() {
        long pageSize = runCommand(Duration.ofSeconds(1), "pagesize")
                .map(value -> parseLong(value, 4096L))
                .orElse(4096L);
        Optional<String> vmStat = runCommand(Duration.ofSeconds(1), "vm_stat");
        Long cached = null;
        Long wired = null;
        Long compressed = null;
        if (vmStat.isPresent()) {
            cached = macVmStatPages(vmStat.get(), "Pages purgeable:") * pageSize;
            wired = macVmStatPages(vmStat.get(), "Pages wired down:") * pageSize;
            compressed = macVmStatPages(vmStat.get(), "Pages occupied by compressor:") * pageSize;
        }
        String pressure = runCommand(Duration.ofSeconds(2), "memory_pressure")
                .map(output -> output.lines()
                        .filter(line -> line.toLowerCase().contains("system-wide memory free percentage"))
                        .findFirst()
                        .orElse(output.lines().findFirst().orElse("Unknown")))
                .orElse("Unknown");
        return new MemoryExtras(zeroToNull(cached), zeroToNull(wired), zeroToNull(compressed), pressure);
    }

    private long macVmStatPages(String output, String label) {
        return output.lines()
                .map(String::trim)
                .filter(line -> line.startsWith(label))
                .map(line -> line.substring(label.length()).replace(".", "").trim())
                .findFirst()
                .map(value -> parseLong(value, 0L))
                .orElse(0L);
    }

    private MemoryExtras linuxMemoryExtras() {
        try {
            Path memInfo = Path.of("/proc/meminfo");
            if (!Files.isReadable(memInfo)) {
                return new MemoryExtras(null, null, null, "Unknown");
            }
            List<String> lines = Files.readAllLines(memInfo);
            Long cached = (memInfoKb(lines, "Cached:") + memInfoKb(lines, "SReclaimable:")) * 1024L;
            Long compressed = memInfoKb(lines, "Zswap:") * 1024L;
            return new MemoryExtras(zeroToNull(cached), null, zeroToNull(compressed), "Unknown");
        } catch (Exception e) {
            return new MemoryExtras(null, null, null, "Unknown");
        }
    }

    private long memInfoKb(List<String> lines, String label) {
        return lines.stream()
                .map(String::trim)
                .filter(line -> line.startsWith(label))
                .map(line -> line.substring(label.length()).trim().split("\\s+")[0])
                .findFirst()
                .map(value -> parseLong(value, 0L))
                .orElse(0L);
    }

    private DiskThroughput diskThroughput() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("linux")) {
            return linuxDiskThroughput();
        }
        if (osName.contains("mac") || osName.contains("darwin")) {
            return macDiskThroughput();
        }
        return new DiskThroughput(null, null, "Unknown");
    }

    private DiskThroughput linuxDiskThroughput() {
        DiskCounters first = linuxDiskCounters();
        if (first == null) {
            return new DiskThroughput(null, null, "Unknown");
        }
        try {
            Thread.sleep(200L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new DiskThroughput(null, null, "Unknown");
        }
        DiskCounters second = linuxDiskCounters();
        if (second == null) {
            return new DiskThroughput(null, null, "Unknown");
        }
        double seconds = 0.2D;
        return new DiskThroughput(
                round((second.readBytes() - first.readBytes()) / seconds),
                round((second.writeBytes() - first.writeBytes()) / seconds),
                "/proc/diskstats");
    }

    private DiskCounters linuxDiskCounters() {
        try {
            Path diskStats = Path.of("/proc/diskstats");
            if (!Files.isReadable(diskStats)) {
                return null;
            }
            long readSectors = 0L;
            long writeSectors = 0L;
            for (String line : Files.readAllLines(diskStats)) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 14 || parts[2].startsWith("loop") || parts[2].startsWith("ram")) {
                    continue;
                }
                readSectors += parseLong(parts[5], 0L);
                writeSectors += parseLong(parts[9], 0L);
            }
            return new DiskCounters(readSectors * 512L, writeSectors * 512L);
        } catch (Exception e) {
            return null;
        }
    }

    private DiskThroughput macDiskThroughput() {
        return runCommand(Duration.ofSeconds(2), "iostat", "-Id", "-w", "1", "-c", "2")
                .flatMap(output -> output.lines()
                        .map(String::trim)
                        .filter(line -> line.matches(".*\\d+.*"))
                        .reduce((first, second) -> second)
                        .map(line -> {
                            String[] parts = line.split("\\s+");
                            if (parts.length < 3) {
                                return new DiskThroughput(null, null, "iostat");
                            }
                            double kilobytesPerTransfer = parseDouble(parts[0], 0D);
                            double transfersPerSecond = parseDouble(parts[1], 0D);
                            double megabytesPerSecond = parseDouble(parts[2], 0D);
                            double totalBytes = megabytesPerSecond > 0D
                                    ? megabytesPerSecond * 1024D * 1024D
                                    : kilobytesPerTransfer * transfersPerSecond * 1024D;
                            return new DiskThroughput(round(totalBytes), null, "iostat");
                        }))
                .orElseGet(() -> new DiskThroughput(null, null, "Unknown"));
    }

    private String smartHealth() {
        Optional<String> smartctl = runCommand(Duration.ofSeconds(2), "smartctl", "-H", "/dev/disk0");
        if (smartctl.isPresent()) {
            return smartctl.get().lines()
                    .filter(line -> line.toLowerCase().contains("health") || line.toLowerCase().contains("passed"))
                    .findFirst()
                    .orElse("Available via smartctl");
        }
        return "Unknown";
    }

    private MachineStatus.TemperatureDetails temperatureDetails() {
        Optional<TemperatureSnapshot> snapshot = temperatureSnapshot();
        if (snapshot.isEmpty()) {
            return MachineStatus.TemperatureDetails.builder()
                    .source("Unknown")
                    .status("Unknown - Temperature sensor is unavailable")
                    .build();
        }

        double celsius = round(snapshot.get().temperatureCelsius());
        return MachineStatus.TemperatureDetails.builder()
                .temperatureCelsius(celsius)
                .temperatureFahrenheit(round((celsius * 9D / 5D) + 32D))
                .source(snapshot.get().source())
                .status(temperatureStatus(celsius))
                .build();
    }

    private Optional<TemperatureSnapshot> temperatureSnapshot() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("win")) {
            return windowsTemperature();
        }
        if (osName.contains("mac") || osName.contains("darwin")) {
            return macTemperature();
        }
        return linuxTemperature();
    }

    private Optional<TemperatureSnapshot> macTemperature() {
        Optional<Double> osxCpuTemp = runCommand(Duration.ofSeconds(2), "osx-cpu-temp")
                .flatMap(this::parseTemperatureWithUnit);
        if (osxCpuTemp.isPresent()) {
            return osxCpuTemp.map(value -> new TemperatureSnapshot(value, "osx-cpu-temp"));
        }

        Optional<Double> iStats = runCommand(Duration.ofSeconds(2), "istats", "cpu", "temp", "--value-only")
                .flatMap(this::parsePlainTemperature);
        if (iStats.isPresent()) {
            return iStats.map(value -> new TemperatureSnapshot(value, "iStats CPU sensor"));
        }

        return runCommand(Duration.ofSeconds(3), "powermetrics", "--samplers", "smc", "-n", "1", "-i", "1")
                .flatMap(this::parseTemperatureLine)
                .map(value -> new TemperatureSnapshot(value, "powermetrics SMC"));
    }

    private Optional<TemperatureSnapshot> linuxTemperature() {
        Optional<Double> thermalZoneTemperature = maxTemperature(Path.of("/sys/class/thermal"), "thermal_zone", "temp");
        if (thermalZoneTemperature.isPresent()) {
            return thermalZoneTemperature.map(value -> new TemperatureSnapshot(value, "Linux thermal zone"));
        }

        return maxTemperature(Path.of("/sys/class/hwmon"), "hwmon", "temp")
                .map(value -> new TemperatureSnapshot(value, "Linux hwmon"));
    }

    private Optional<TemperatureSnapshot> windowsTemperature() {
        Optional<String> output = runCommand(Duration.ofSeconds(2),
                "powershell.exe",
                "-NoProfile",
                "-Command",
                "$t=Get-CimInstance -Namespace root/wmi MSAcpi_ThermalZoneTemperature | Select-Object -First 1 -ExpandProperty CurrentTemperature; if ($t) { [math]::Round(($t / 10) - 273.15, 2) }");
        return output.flatMap(this::parsePlainTemperature)
                .map(value -> new TemperatureSnapshot(value, "Windows ACPI thermal zone"));
    }

    private MachineStatus.PowerDetails powerDetails() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("mac") || osName.contains("darwin")) {
            return macPowerDetails();
        }
        if (osName.contains("linux")) {
            return linuxPowerDetails();
        }
        if (osName.contains("win")) {
            return windowsPowerDetails();
        }
        return MachineStatus.PowerDetails.builder()
                .powerSource("Unknown")
                .status("Unknown - Power data is unavailable")
                .build();
    }

    private MachineStatus.PowerDetails macPowerDetails() {
        Optional<String> pmset = runCommand(Duration.ofSeconds(1), "pmset", "-g", "batt");
        Double batteryPercent = null;
        Boolean charging = null;
        String powerSource = "Unknown";
        if (pmset.isPresent()) {
            String output = pmset.get();
            powerSource = output.lines()
                    .filter(line -> line.contains("Now drawing from"))
                    .map(line -> substringBetween(line, "'", "'").orElse("Unknown"))
                    .findFirst()
                    .orElse("Unknown");
            batteryPercent = firstPercent(output).orElse(null);
            String lower = output.toLowerCase();
            if (lower.contains("charging")) {
                charging = true;
            } else if (lower.contains("discharging")) {
                charging = false;
            }
        }

        Optional<String> profiler = runCommand(Duration.ofSeconds(3), "system_profiler", "SPPowerDataType");
        Integer cycleCount = profiler.flatMap(output -> firstLabeledInt(output, "Cycle Count:")).orElse(null);
        String condition = profiler.flatMap(output -> firstLabeledValue(output, "Condition:")).orElse("Unknown");
        String health = profiler.flatMap(output -> firstLabeledValue(output, "Battery Health:")).orElse(condition);
        return MachineStatus.PowerDetails.builder()
                .batteryPercent(batteryPercent)
                .charging(charging)
                .powerSource(powerSource)
                .cycleCount(cycleCount)
                .health(health)
                .condition(condition)
                .status(powerStatus(batteryPercent, charging, condition))
                .build();
    }

    private MachineStatus.PowerDetails linuxPowerDetails() {
        Optional<Path> battery = firstDirectory(Path.of("/sys/class/power_supply"), "BAT");
        if (battery.isEmpty()) {
            return MachineStatus.PowerDetails.builder()
                    .powerSource("AC Power")
                    .status("Good - No battery detected")
                    .build();
        }

        Double batteryPercent = readLong(battery.get().resolve("capacity")).map(Long::doubleValue).orElse(null);
        String status = readString(battery.get().resolve("status")).orElse("Unknown");
        Boolean charging = status.equalsIgnoreCase("Charging") ? true : status.equalsIgnoreCase("Discharging") ? false : null;
        String health = readString(battery.get().resolve("health")).orElse("Unknown");
        Integer cycleCount = readLong(battery.get().resolve("cycle_count")).map(Long::intValue).orElse(null);
        return MachineStatus.PowerDetails.builder()
                .batteryPercent(batteryPercent)
                .charging(charging)
                .powerSource(Boolean.FALSE.equals(charging) ? "Battery Power" : "AC Power")
                .cycleCount(cycleCount)
                .health(health)
                .condition(status)
                .status(powerStatus(batteryPercent, charging, health))
                .build();
    }

    private MachineStatus.PowerDetails windowsPowerDetails() {
        Optional<String> output = runCommand(Duration.ofSeconds(2),
                "powershell.exe",
                "-NoProfile",
                "-Command",
                "$b=Get-CimInstance Win32_Battery | Select-Object -First 1; if ($b) { @($b.EstimatedChargeRemaining,$b.BatteryStatus,$b.Status) -join \"`n\" }");
        if (output.isEmpty()) {
            return MachineStatus.PowerDetails.builder()
                    .powerSource("Unknown")
                    .status("Unknown - Battery data is unavailable")
                    .build();
        }
        String[] lines = output.get().lines().map(String::trim).toArray(String[]::new);
        Double batteryPercent = parseDouble(valueAt(lines, 0, ""), Double.NaN);
        int batteryStatus = parseInt(valueAt(lines, 1, ""), 0);
        Boolean charging = batteryStatus == 2 ? false : batteryStatus > 0 ? true : null;
        String condition = valueAt(lines, 2, "Unknown");
        return MachineStatus.PowerDetails.builder()
                .batteryPercent(Double.isNaN(batteryPercent) ? null : batteryPercent)
                .charging(charging)
                .powerSource(Boolean.FALSE.equals(charging) ? "Battery Power" : "AC Power")
                .health(condition)
                .condition(condition)
                .status(powerStatus(Double.isNaN(batteryPercent) ? null : batteryPercent, charging, condition))
                .build();
    }

    private MachineStatus.ProcessDetails processDetails() {
        return MachineStatus.ProcessDetails.builder()
                .processCount(processCount())
                .topCpuProcesses(topProcesses(true))
                .topMemoryProcesses(topProcesses(false))
                .build();
    }

    private long processCount() {
        try {
            return ProcessHandle.allProcesses().count();
        } catch (Exception e) {
            return runCommand(Duration.ofSeconds(2), "ps", "-axo", "pid=")
                    .map(output -> output.lines().filter(line -> !line.isBlank()).count())
                    .orElse(0L);
        }
    }

    private List<MachineStatus.ProcessInfo> topProcesses(boolean byCpu) {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("win")) {
            return windowsTopProcesses(byCpu);
        }
        return unixTopProcesses(byCpu);
    }

    private List<MachineStatus.ProcessInfo> unixTopProcesses(boolean byCpu) {
        return runCommand(Duration.ofSeconds(2), "ps", "-axo", "pid=,pcpu=,rss=,comm=")
                .map(output -> output.lines()
                        .map(this::parseUnixProcess)
                        .flatMap(Optional::stream)
                        .sorted(byCpu
                                ? Comparator.comparing(MachineStatus.ProcessInfo::getCpuPercent, Comparator.nullsLast(Comparator.reverseOrder()))
                                : Comparator.comparing(MachineStatus.ProcessInfo::getMemoryBytes, Comparator.nullsLast(Comparator.reverseOrder())))
                        .limit(5)
                        .toList())
                .orElseGet(List::of);
    }

    private Optional<MachineStatus.ProcessInfo> parseUnixProcess(String line) {
        String[] parts = line.trim().split("\\s+", 4);
        if (parts.length < 4) {
            return Optional.empty();
        }
        long pid = parseLong(parts[0], -1L);
        if (pid < 0L) {
            return Optional.empty();
        }
        double cpu = parseDouble(parts[1], Double.NaN);
        long memoryBytes = parseLong(parts[2], 0L) * 1024L;
        return Optional.of(MachineStatus.ProcessInfo.builder()
                .pid(pid)
                .command(parts[3])
                .cpuPercent(Double.isNaN(cpu) ? null : round(cpu))
                .memoryBytes(memoryBytes)
                .memoryMb(round(memoryBytes / (1024D * 1024D)))
                .build());
    }

    private List<MachineStatus.ProcessInfo> windowsTopProcesses(boolean byCpu) {
        String sort = byCpu ? "CPU" : "WorkingSet64";
        return runCommand(Duration.ofSeconds(3),
                "powershell.exe",
                "-NoProfile",
                "-Command",
                "Get-Process | Sort-Object " + sort + " -Descending | Select-Object -First 5 | ForEach-Object { \"$($_.Id)|$($_.ProcessName)|$([math]::Round([double]($_.CPU),2))|$($_.WorkingSet64)\" }")
                .map(output -> output.lines()
                        .map(this::parseDelimitedProcess)
                        .flatMap(Optional::stream)
                        .toList())
                .orElseGet(List::of);
    }

    private Optional<MachineStatus.ProcessInfo> parseDelimitedProcess(String line) {
        String[] parts = line.split("\\|", 4);
        if (parts.length < 4) {
            return Optional.empty();
        }
        long pid = parseLong(parts[0], -1L);
        if (pid < 0L) {
            return Optional.empty();
        }
        double cpu = parseDouble(parts[2], Double.NaN);
        long memoryBytes = parseLong(parts[3], 0L);
        return Optional.of(MachineStatus.ProcessInfo.builder()
                .pid(pid)
                .command(parts[1])
                .cpuPercent(Double.isNaN(cpu) ? null : round(cpu))
                .memoryBytes(memoryBytes)
                .memoryMb(round(memoryBytes / (1024D * 1024D)))
                .build());
    }

    private MachineStatus.NetworkDetails networkDetails() {
        List<MachineStatus.NetworkInterfaceDetails> interfaces = networkInterfaces();
        NetworkThroughput throughput = networkThroughput();
        return MachineStatus.NetworkDetails.builder()
                .hostAddresses(interfaces.stream()
                        .flatMap(networkInterface -> networkInterface.getAddresses().stream())
                        .distinct()
                        .toList())
                .interfaces(interfaces)
                .activeInterface(activeInterface(interfaces))
                .wifiSsid(wifiSsid())
                .publicIp(publicIp())
                .downloadBytesPerSecond(throughput.downloadBytesPerSecond())
                .uploadBytesPerSecond(throughput.uploadBytesPerSecond())
                .dnsServers(dnsServers())
                .defaultGateway(defaultGateway())
                .build();
    }

    private List<MachineStatus.NetworkInterfaceDetails> networkInterfaces() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            if (networkInterfaces == null) {
                return List.of();
            }
            List<MachineStatus.NetworkInterfaceDetails> details = new ArrayList<>();
            for (NetworkInterface networkInterface : Collections.list(networkInterfaces)) {
                List<String> addresses = Collections.list(networkInterface.getInetAddresses()).stream()
                        .map(InetAddress::getHostAddress)
                        .map(address -> address.contains("%") ? address.substring(0, address.indexOf('%')) : address)
                        .toList();
                details.add(MachineStatus.NetworkInterfaceDetails.builder()
                        .name(networkInterface.getName())
                        .displayName(networkInterface.getDisplayName())
                        .macAddress(formatMacAddress(networkInterface.getHardwareAddress()))
                        .up(networkInterface.isUp())
                        .loopback(networkInterface.isLoopback())
                        .addresses(addresses)
                        .build());
            }
            return details;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String activeInterface(List<MachineStatus.NetworkInterfaceDetails> interfaces) {
        return interfaces.stream()
                .filter(MachineStatus.NetworkInterfaceDetails::isUp)
                .filter(networkInterface -> !networkInterface.isLoopback())
                .filter(networkInterface -> !networkInterface.getAddresses().isEmpty())
                .map(MachineStatus.NetworkInterfaceDetails::getName)
                .findFirst()
                .orElse("Unknown");
    }

    private String wifiSsid() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("mac") || osName.contains("darwin")) {
            return runCommand(Duration.ofSeconds(2), "networksetup", "-getairportnetwork", "en0")
                    .map(output -> output.contains(":") ? output.substring(output.indexOf(':') + 1).trim() : output)
                    .orElse("Unknown");
        }
        if (osName.contains("linux")) {
            return runCommand(Duration.ofSeconds(1), "iwgetid", "-r").orElse("Unknown");
        }
        if (osName.contains("win")) {
            return runCommand(Duration.ofSeconds(2), "netsh", "wlan", "show", "interfaces")
                    .flatMap(output -> firstLabeledValue(output, "SSID"))
                    .orElse("Unknown");
        }
        return "Unknown";
    }

    private String publicIp() {
        return runCommand(Duration.ofSeconds(2), "curl", "-fsS", "--max-time", "2", "https://ifconfig.me")
                .filter(value -> value.matches("[0-9a-fA-F:.]+"))
                .orElse("Unknown");
    }

    private NetworkThroughput networkThroughput() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("linux")) {
            return linuxNetworkThroughput();
        }
        if (osName.contains("mac") || osName.contains("darwin")) {
            return macNetworkThroughput();
        }
        return new NetworkThroughput(null, null);
    }

    private NetworkThroughput linuxNetworkThroughput() {
        NetworkCounters first = linuxNetworkCounters();
        if (first == null) {
            return new NetworkThroughput(null, null);
        }
        try {
            Thread.sleep(200L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new NetworkThroughput(null, null);
        }
        NetworkCounters second = linuxNetworkCounters();
        if (second == null) {
            return new NetworkThroughput(null, null);
        }
        return new NetworkThroughput(
                round((second.rxBytes() - first.rxBytes()) / 0.2D),
                round((second.txBytes() - first.txBytes()) / 0.2D));
    }

    private NetworkCounters linuxNetworkCounters() {
        try {
            Path dev = Path.of("/proc/net/dev");
            if (!Files.isReadable(dev)) {
                return null;
            }
            long rx = 0L;
            long tx = 0L;
            for (String line : Files.readAllLines(dev)) {
                if (!line.contains(":")) {
                    continue;
                }
                String name = line.substring(0, line.indexOf(':')).trim();
                if (name.equals("lo")) {
                    continue;
                }
                String[] parts = line.substring(line.indexOf(':') + 1).trim().split("\\s+");
                if (parts.length >= 16) {
                    rx += parseLong(parts[0], 0L);
                    tx += parseLong(parts[8], 0L);
                }
            }
            return new NetworkCounters(rx, tx);
        } catch (Exception e) {
            return null;
        }
    }

    private NetworkThroughput macNetworkThroughput() {
        NetworkCounters first = macNetworkCounters();
        if (first == null) {
            return new NetworkThroughput(null, null);
        }
        try {
            Thread.sleep(200L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new NetworkThroughput(null, null);
        }
        NetworkCounters second = macNetworkCounters();
        if (second == null) {
            return new NetworkThroughput(null, null);
        }
        return new NetworkThroughput(
                round((second.rxBytes() - first.rxBytes()) / 0.2D),
                round((second.txBytes() - first.txBytes()) / 0.2D));
    }

    private NetworkCounters macNetworkCounters() {
        return runCommand(Duration.ofSeconds(1), "netstat", "-ibn")
                .map(output -> {
                    long rx = 0L;
                    long tx = 0L;
                    for (String line : output.lines().toList()) {
                        String[] parts = line.trim().split("\\s+");
                        if (parts.length < 10 || parts[0].equals("Name") || parts[0].startsWith("lo")) {
                            continue;
                        }
                        rx += parseLong(parts[6], 0L);
                        tx += parseLong(parts[9], 0L);
                    }
                    return new NetworkCounters(rx, tx);
                })
                .orElse(null);
    }

    private List<String> dnsServers() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("linux")) {
            try {
                Path resolv = Path.of("/etc/resolv.conf");
                if (Files.isReadable(resolv)) {
                    return Files.readAllLines(resolv).stream()
                            .map(String::trim)
                            .filter(line -> line.startsWith("nameserver "))
                            .map(line -> line.substring("nameserver ".length()).trim())
                            .toList();
                }
            } catch (Exception ignored) {
                return List.of();
            }
        }
        if (osName.contains("mac") || osName.contains("darwin")) {
            return runCommand(Duration.ofSeconds(1), "scutil", "--dns")
                    .map(output -> output.lines()
                            .map(String::trim)
                            .filter(line -> line.startsWith("nameserver["))
                            .map(line -> line.substring(line.indexOf(':') + 1).trim())
                            .distinct()
                            .toList())
                    .orElseGet(List::of);
        }
        if (osName.contains("win")) {
            return runCommand(Duration.ofSeconds(2), "powershell.exe", "-NoProfile", "-Command", "Get-DnsClientServerAddress -AddressFamily IPv4 | Select -ExpandProperty ServerAddresses")
                    .map(output -> output.lines().map(String::trim).filter(value -> !value.isBlank()).distinct().toList())
                    .orElseGet(List::of);
        }
        return List.of();
    }

    private String defaultGateway() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("mac") || osName.contains("darwin")) {
            return runCommand(Duration.ofSeconds(1), "route", "-n", "get", "default")
                    .flatMap(output -> firstLabeledValue(output, "gateway"))
                    .orElse("Unknown");
        }
        if (osName.contains("linux")) {
            return runCommand(Duration.ofSeconds(1), "ip", "route", "show", "default")
                    .map(output -> Arrays.stream(output.split("\\s+")).toList())
                    .filter(parts -> parts.size() > 2 && parts.contains("via"))
                    .map(parts -> parts.get(parts.indexOf("via") + 1))
                    .orElse("Unknown");
        }
        if (osName.contains("win")) {
            return runCommand(Duration.ofSeconds(2), "powershell.exe", "-NoProfile", "-Command", "(Get-NetRoute -DestinationPrefix '0.0.0.0/0' | Sort-Object RouteMetric | Select-Object -First 1).NextHop")
                    .orElse("Unknown");
        }
        return "Unknown";
    }

    private MachineStatus.ThermalDetails thermalDetails() {
        List<MachineStatus.TemperatureDetails> sensors = thermalSensors();
        Double cpuTemperature = sensors.stream()
                .filter(sensor -> sensor.getSource() != null && sensor.getSource().toLowerCase().contains("cpu"))
                .map(MachineStatus.TemperatureDetails::getTemperatureCelsius)
                .filter(value -> value != null)
                .findFirst()
                .orElseGet(() -> temperatureDetails().getTemperatureCelsius());
        Double gpuTemperature = sensors.stream()
                .filter(sensor -> sensor.getSource() != null && sensor.getSource().toLowerCase().contains("gpu"))
                .map(MachineStatus.TemperatureDetails::getTemperatureCelsius)
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);
        return MachineStatus.ThermalDetails.builder()
                .fanSpeedRpm(fanSpeedRpm().orElse(null))
                .thermalPressure(thermalPressure())
                .sensors(sensors)
                .cpuTemperatureCelsius(cpuTemperature)
                .gpuTemperatureCelsius(gpuTemperature)
                .status(cpuTemperature == null ? "Unknown - Thermal data is unavailable" : temperatureStatus(cpuTemperature))
                .build();
    }

    private List<MachineStatus.TemperatureDetails> thermalSensors() {
        Optional<TemperatureSnapshot> snapshot = temperatureSnapshot();
        return snapshot
                .map(value -> List.of(MachineStatus.TemperatureDetails.builder()
                        .temperatureCelsius(round(value.temperatureCelsius()))
                        .temperatureFahrenheit(round((value.temperatureCelsius() * 9D / 5D) + 32D))
                        .source(value.source())
                        .status(temperatureStatus(value.temperatureCelsius()))
                        .build()))
                .orElseGet(List::of);
    }

    private Optional<Integer> fanSpeedRpm() {
        Optional<Integer> istats = runCommand(Duration.ofSeconds(2), "istats", "fan", "--value-only")
                .flatMap(output -> output.lines()
                        .map(line -> parseDouble(line.trim(), Double.NaN))
                        .filter(value -> !Double.isNaN(value))
                        .map(value -> (int) Math.round(value))
                        .findFirst());
        if (istats.isPresent()) {
            return istats;
        }

        Optional<Integer> linuxFan = maxFanSpeed(Path.of("/sys/class/hwmon"));
        if (linuxFan.isPresent()) {
            return linuxFan;
        }

        return runCommand(Duration.ofSeconds(3), "powermetrics", "--samplers", "smc", "-n", "1", "-i", "1")
                .flatMap(output -> output.lines()
                        .filter(line -> line.toLowerCase().contains("fan"))
                        .map(line -> {
                            Matcher matcher = Pattern.compile("(\\d+)\\s*rpm", Pattern.CASE_INSENSITIVE).matcher(line);
                            return matcher.find() ? parseInt(matcher.group(1), 0) : 0;
                        })
                        .filter(value -> value > 0)
                        .findFirst());
    }

    private Optional<Integer> maxFanSpeed(Path root) {
        try {
            if (!Files.isDirectory(root)) {
                return Optional.empty();
            }
            try (Stream<Path> directories = Files.list(root)) {
                return directories
                        .filter(Files::isDirectory)
                        .flatMap(directory -> fanFiles(directory).stream())
                        .map(this::readLong)
                        .flatMap(Optional::stream)
                        .map(Long::intValue)
                        .max(Comparator.naturalOrder());
            }
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private List<Path> fanFiles(Path directory) {
        try (Stream<Path> files = Files.list(directory)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().matches("fan\\d+_input"))
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private String thermalPressure() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("mac") || osName.contains("darwin")) {
            return runCommand(Duration.ofSeconds(1), "pmset", "-g", "therm")
                    .map(output -> output.lines().map(String::trim).filter(value -> !value.isBlank()).toList().toString())
                    .orElse("Unknown");
        }
        if (osName.contains("linux")) {
            return firstDirectory(Path.of("/sys/class/thermal"), "thermal_zone")
                    .flatMap(path -> readString(path.resolve("type")).map(type -> type + ": " + readString(path.resolve("temp")).orElse("Unknown")))
                    .orElse("Unknown");
        }
        return "Unknown";
    }

    private MachineStatus.GpuDetails gpuDetails() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("mac") || osName.contains("darwin")) {
            return macGpuDetails();
        }
        if (osName.contains("linux")) {
            return linuxGpuDetails();
        }
        if (osName.contains("win")) {
            return windowsGpuDetails();
        }
        return MachineStatus.GpuDetails.builder().models(List.of()).status("Unknown").build();
    }

    private MachineStatus.GpuDetails macGpuDetails() {
        Optional<String> output = runCommand(Duration.ofSeconds(3), "system_profiler", "SPDisplaysDataType");
        List<String> models = output
                .map(value -> value.lines()
                        .map(String::trim)
                        .filter(line -> line.startsWith("Chipset Model:"))
                        .map(line -> line.substring("Chipset Model:".length()).trim())
                        .toList())
                .orElseGet(List::of);
        String metalInfo = output.flatMap(value -> firstLabeledValue(value, "Metal Support:")).orElse("Unknown");
        return MachineStatus.GpuDetails.builder()
                .models(models)
                .metalInfo(metalInfo)
                .driverInfo("Integrated with macOS")
                .status(models.isEmpty() ? "Unknown - GPU data is unavailable" : "Available")
                .build();
    }

    private MachineStatus.GpuDetails linuxGpuDetails() {
        List<String> models = runCommand(Duration.ofSeconds(2), "lspci")
                .map(output -> output.lines()
                        .filter(line -> line.toLowerCase().contains("vga") || line.toLowerCase().contains("3d controller"))
                        .map(String::trim)
                        .toList())
                .orElseGet(List::of);
        Optional<String> nvidia = runCommand(Duration.ofSeconds(2), "nvidia-smi", "--query-gpu=utilization.gpu,memory.total,memory.used,driver_version,name", "--format=csv,noheader,nounits");
        if (nvidia.isPresent()) {
            String[] parts = nvidia.get().lines().findFirst().orElse("").split(",");
            Double utilization = parts.length > 0 ? parseDouble(parts[0], Double.NaN) : Double.NaN;
            long totalMb = parts.length > 1 ? parseLong(parts[1].trim(), 0L) : 0L;
            long usedMb = parts.length > 2 ? parseLong(parts[2].trim(), 0L) : 0L;
            String driver = parts.length > 3 ? parts[3].trim() : "Unknown";
            List<String> nvidiaModels = parts.length > 4 ? List.of(parts[4].trim()) : models;
            return MachineStatus.GpuDetails.builder()
                    .models(nvidiaModels)
                    .utilizationPercent(Double.isNaN(utilization) ? null : round(utilization))
                    .memoryTotalBytes(totalMb * 1024L * 1024L)
                    .memoryUsedBytes(usedMb * 1024L * 1024L)
                    .memoryTotalGb(gb(totalMb * 1024L * 1024L))
                    .memoryUsedGb(gb(usedMb * 1024L * 1024L))
                    .driverInfo(driver)
                    .status("Available")
                    .build();
        }
        return MachineStatus.GpuDetails.builder()
                .models(models)
                .driverInfo("Unknown")
                .status(models.isEmpty() ? "Unknown - GPU data is unavailable" : "Available")
                .build();
    }

    private MachineStatus.GpuDetails windowsGpuDetails() {
        Optional<String> output = runCommand(Duration.ofSeconds(2),
                "powershell.exe",
                "-NoProfile",
                "-Command",
                "Get-CimInstance Win32_VideoController | ForEach-Object { \"$($_.Name)|$($_.AdapterRAM)|$($_.DriverVersion)\" }");
        if (output.isEmpty()) {
            return MachineStatus.GpuDetails.builder().models(List.of()).status("Unknown").build();
        }
        List<String> models = output.get().lines()
                .map(line -> line.split("\\|", 3)[0].trim())
                .filter(value -> !value.isBlank())
                .toList();
        String[] first = output.get().lines().findFirst().orElse("").split("\\|", 3);
        long memoryBytes = first.length > 1 ? parseLong(first[1], 0L) : 0L;
        String driver = first.length > 2 ? first[2] : "Unknown";
        return MachineStatus.GpuDetails.builder()
                .models(models)
                .memoryTotalBytes(zeroToNull(memoryBytes))
                .memoryTotalGb(memoryBytes > 0L ? gb(memoryBytes) : null)
                .driverInfo(driver)
                .status(models.isEmpty() ? "Unknown" : "Available")
                .build();
    }

    private MachineStatus.SystemDetails systemDetails(RuntimeSnapshot runtime) {
        return MachineStatus.SystemDetails.builder()
                .loggedInUser(System.getProperty("user.name", "Unknown"))
                .timezone(ZoneId.systemDefault().toString())
                .locale(Locale.getDefault().toLanguageTag())
                .kernelVersion(kernelVersion())
                .bootTime(DATE_TIME_FORMATTER.format(runtime.bootTime()))
                .sleepWakeHistory(sleepWakeHistory())
                .build();
    }

    private String kernelVersion() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("win")) {
            return System.getProperty("os.version", "Unknown");
        }
        return runCommand(Duration.ofSeconds(1), "uname", "-r")
                .orElse(System.getProperty("os.version", "Unknown"));
    }

    private List<String> sleepWakeHistory() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("mac") || osName.contains("darwin")) {
            return runCommand(Duration.ofSeconds(2), "pmset", "-g", "log")
                    .map(output -> output.lines()
                            .filter(line -> line.contains(" Sleep ") || line.contains(" Wake "))
                            .skip(Math.max(0, output.lines().filter(line -> line.contains(" Sleep ") || line.contains(" Wake ")).count() - 5))
                            .map(String::trim)
                            .toList())
                    .orElseGet(List::of);
        }
        return List.of();
    }

    private MachineStatus.JvmDetails jvmDetails() {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memory.getHeapMemoryUsage();
        MemoryUsage nonHeap = memory.getNonHeapMemoryUsage();
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        com.sun.management.OperatingSystemMXBean osBean =
                ManagementFactory.getPlatformMXBean(com.sun.management.OperatingSystemMXBean.class);
        double processCpuLoad = osBean == null ? Double.NaN : osBean.getProcessCpuLoad();
        return MachineStatus.JvmDetails.builder()
                .processId(ProcessHandle.current().pid())
                .uptime(formatDuration(Duration.ofMillis(runtime.getUptime())))
                .uptimeSeconds(Duration.ofMillis(runtime.getUptime()).toSeconds())
                .heapUsedBytes(heap.getUsed())
                .heapMaxBytes(heap.getMax())
                .nonHeapUsedBytes(nonHeap.getUsed())
                .processCpuUsagePercent(Double.isNaN(processCpuLoad) || processCpuLoad < 0D ? null : round(processCpuLoad * 100D))
                .openFileDescriptorCount(openFileDescriptorCount())
                .threadCount(threads.getThreadCount())
                .build();
    }

    private Optional<Double> maxTemperature(Path root, String directoryPrefix, String fileNamePrefix) {
        try {
            if (!Files.isDirectory(root)) {
                return Optional.empty();
            }
            try (Stream<Path> directories = Files.list(root)) {
                return directories
                        .filter(Files::isDirectory)
                        .filter(path -> path.getFileName().toString().startsWith(directoryPrefix))
                        .flatMap(directory -> temperatureFiles(directory, fileNamePrefix))
                        .map(this::readTemperatureFile)
                        .flatMap(Optional::stream)
                        .max(Comparator.naturalOrder());
            }
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Stream<Path> temperatureFiles(Path directory, String fileNamePrefix) {
        try (Stream<Path> files = Files.list(directory)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String fileName = path.getFileName().toString();
                        return fileName.equals(fileNamePrefix)
                                || (fileName.startsWith(fileNamePrefix) && fileName.endsWith("_input"));
                    })
                    .toList()
                    .stream();
        } catch (Exception e) {
            return Stream.empty();
        }
    }

    private Optional<Double> readTemperatureFile(Path path) {
        try {
            String value = Files.readString(path).trim();
            double temperature = parseDouble(value, Double.NaN);
            if (Double.isNaN(temperature)) {
                return Optional.empty();
            }
            if (Math.abs(temperature) > 1_000D) {
                temperature = temperature / 1_000D;
            }
            return validTemperature(temperature) ? Optional.of(temperature) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
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

    private String temperatureStatus(double celsius) {
        if (celsius >= 90D) {
            return "Critical - System temperature is very high";
        }
        if (celsius >= 80D) {
            return "Warning - System temperature is high";
        }
        return "Good - System temperature is normal";
    }

    static String formatCoreSummary(int totalCores, int performanceCores, int efficiencyCores) {
        int safeTotalCores = Math.max(0, totalCores);
        int safePerformanceCores = Math.max(0, performanceCores);
        int safeEfficiencyCores = Math.max(0, efficiencyCores);
        if (safePerformanceCores > 0 || safeEfficiencyCores > 0) {
            int displayTotal = safeTotalCores > 0 ? safeTotalCores : safePerformanceCores + safeEfficiencyCores;
            return "%d (%d Performance and %d Efficiency)"
                    .formatted(displayTotal, safePerformanceCores, safeEfficiencyCores);
        }
        return String.valueOf(safeTotalCores);
    }

    private String powerStatus(Double batteryPercent, Boolean charging, String condition) {
        if (batteryPercent != null && batteryPercent <= 10D && !Boolean.TRUE.equals(charging)) {
            return "Critical - Battery is very low";
        }
        if (batteryPercent != null && batteryPercent <= 20D && !Boolean.TRUE.equals(charging)) {
            return "Warning - Battery is low";
        }
        if (condition != null && !condition.equalsIgnoreCase("Unknown") && !condition.equalsIgnoreCase("Normal")) {
            return "Warning - Battery condition is " + condition;
        }
        return "Good - Power status is healthy";
    }

    private Optional<Path> firstDirectory(Path root, String prefix) {
        try {
            if (!Files.isDirectory(root)) {
                return Optional.empty();
            }
            try (Stream<Path> paths = Files.list(root)) {
                return paths
                        .filter(Files::isDirectory)
                        .filter(path -> path.getFileName().toString().startsWith(prefix))
                        .findFirst();
            }
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<String> readString(Path path) {
        try {
            if (!Files.isReadable(path)) {
                return Optional.empty();
            }
            String value = Files.readString(path).trim();
            return value.isBlank() ? Optional.empty() : Optional.of(value);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<Double> firstPercent(String value) {
        Matcher matcher = Pattern.compile("(\\d+(?:\\.\\d+)?)%").matcher(value);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(round(parseDouble(matcher.group(1), 0D)));
    }

    private Optional<String> substringBetween(String value, String startMarker, String endMarker) {
        int start = value.indexOf(startMarker);
        if (start < 0) {
            return Optional.empty();
        }
        int valueStart = start + startMarker.length();
        int end = value.indexOf(endMarker, valueStart);
        if (end < 0) {
            return Optional.empty();
        }
        String result = value.substring(valueStart, end).trim();
        return result.isBlank() ? Optional.empty() : Optional.of(result);
    }

    private Optional<String> firstLabeledValue(String output, String label) {
        String normalizedLabel = label.endsWith(":") ? label : label + ":";
        return output.lines()
                .map(String::trim)
                .filter(line -> line.startsWith(normalizedLabel))
                .map(line -> line.substring(normalizedLabel.length()).trim())
                .filter(value -> !value.isBlank())
                .findFirst();
    }

    private Optional<Integer> firstLabeledInt(String output, String label) {
        return firstLabeledValue(output, label)
                .map(value -> value.replaceAll("[^0-9-]", ""))
                .filter(value -> !value.isBlank())
                .map(value -> parseInt(value, 0));
    }

    private String formatMacAddress(byte[] hardwareAddress) {
        if (hardwareAddress == null || hardwareAddress.length == 0) {
            return "Unknown";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < hardwareAddress.length; i++) {
            if (i > 0) {
                builder.append(":");
            }
            builder.append("%02x".formatted(hardwareAddress[i] & 0xff));
        }
        return builder.toString();
    }

    private Long zeroToNull(Long value) {
        return value == null || value == 0L ? null : value;
    }

    private Long openFileDescriptorCount() {
        java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof com.sun.management.UnixOperatingSystemMXBean unixBean) {
            return unixBean.getOpenFileDescriptorCount();
        }
        return null;
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

    private Optional<Double> parseTemperatureWithUnit(String output) {
        Matcher matcher = TEMPERATURE_PATTERN.matcher(output);
        if (!matcher.find()) {
            return Optional.empty();
        }

        double temperature = parseDouble(matcher.group(1), Double.NaN);
        return validTemperature(temperature) ? Optional.of(temperature) : Optional.empty();
    }

    private Optional<Double> parsePlainTemperature(String output) {
        double temperature = parseDouble(output.lines().findFirst().orElse("").trim(), Double.NaN);
        return validTemperature(temperature) ? Optional.of(temperature) : Optional.empty();
    }

    private Optional<Double> parseTemperatureLine(String output) {
        return output.lines()
                .filter(line -> line.toLowerCase().contains("temperature"))
                .map(this::parseTemperatureWithUnit)
                .flatMap(Optional::stream)
                .findFirst();
    }

    private boolean validTemperature(double temperatureCelsius) {
        return !Double.isNaN(temperatureCelsius) && temperatureCelsius > -50D && temperatureCelsius < 150D;
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
            int performanceCores,
            int efficiencyCores,
            long currentSpeedMhz,
            long maxSpeedMhz) {
        private int totalCores() {
            int typedCores = Math.max(0, performanceCores) + Math.max(0, efficiencyCores);
            return physicalCores > 0 ? physicalCores : typedCores;
        }
    }

    private record CoreTypeSnapshot(int performanceCores, int efficiencyCores) {
    }

    private record LoadAverages(Double oneMinute, Double fiveMinutes, Double fifteenMinutes) {
    }

    private record CpuTimes(long total, long idle) {
    }

    private record MemoryExtras(Long cachedBytes, Long wiredBytes, Long compressedBytes, String pressure) {
    }

    private record DiskCounters(long readBytes, long writeBytes) {
    }

    private record DiskThroughput(Double readBytesPerSecond, Double writeBytesPerSecond, String source) {
    }

    private record StorageSnapshot(
            long totalBytes,
            long freeBytes,
            long usedBytes,
            List<MachineStatus.VolumeDetails> volumes,
            Double readBytesPerSecond,
            Double writeBytesPerSecond,
            String throughputSource,
            String smartHealth) {
    }

    private record NetworkCounters(long rxBytes, long txBytes) {
    }

    private record NetworkThroughput(Double downloadBytesPerSecond, Double uploadBytesPerSecond) {
    }

    private record TemperatureSnapshot(double temperatureCelsius, String source) {
    }

    private record RuntimeSnapshot(Instant bootTime, Duration uptime) {
        private RuntimeSnapshot {
            if (uptime.isNegative()) {
                uptime = Duration.ZERO;
            }
        }
    }
}
