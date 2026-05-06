package com.bxv.sysmindmcp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MachineStatus {
    private String computerName;
    private String operatingSystem;
    private String machineType;
    private String processor;
    private CpuDetails processorDetails;
    private MemoryDetails memoryDetails;
    private StorageDetails storageDetails;
    private TemperatureDetails systemTemperature;
    private PowerDetails powerDetails;
    private ProcessDetails processDetails;
    private NetworkDetails networkDetails;
    private ThermalDetails thermalDetails;
    private GpuDetails gpuDetails;
    private SystemDetails systemDetails;
    private JvmDetails jvmDetails;
    private RuntimeDetails systemStatus;
    private String generatedAt;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CpuDetails {
        private int totalCpuCores;
        private String coreSummary;
        private int physicalCpuCores;
        private int logicalCpuCores;
        private int performanceCpuCores;
        private int efficiencyCpuCores;
        private long currentCpuSpeedMhz;
        private long maxCpuSpeedMhz;
        private double currentCpuUsagePercent;
        private Double loadAverageOneMinute;
        private Double loadAverageFiveMinutes;
        private Double loadAverageFifteenMinutes;
        private List<Double> perCoreUsagePercent;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MemoryDetails {
        private long totalBytes;
        private long availableBytes;
        private long usedBytes;
        private double totalGb;
        private double availableGb;
        private double usedGb;
        private double usagePercent;
        private long swapPageFileTotalBytes;
        private long swapPageFileUsedBytes;
        private double swapPageFileTotalGb;
        private double swapPageFileUsedGb;
        private double swapPageFileUsagePercent;
        private Long cachedBytes;
        private Double cachedGb;
        private Long wiredBytes;
        private Double wiredGb;
        private Long compressedBytes;
        private Double compressedGb;
        private String pressure;
        private String status;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TemperatureDetails {
        private Double temperatureCelsius;
        private Double temperatureFahrenheit;
        private String source;
        private String status;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class StorageDetails {
        private long totalBytes;
        private long freeBytes;
        private long usedBytes;
        private double totalGb;
        private double freeGb;
        private double usedGb;
        private double usagePercent;
        private List<VolumeDetails> volumes;
        private Double readBytesPerSecond;
        private Double writeBytesPerSecond;
        private String throughputSource;
        private String smartHealth;
        private String status;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class VolumeDetails {
        private String path;
        private String name;
        private String type;
        private long totalBytes;
        private long freeBytes;
        private long usedBytes;
        private double totalGb;
        private double freeGb;
        private double usedGb;
        private double usagePercent;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PowerDetails {
        private Double batteryPercent;
        private Boolean charging;
        private String powerSource;
        private Integer cycleCount;
        private String health;
        private String condition;
        private String status;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ProcessDetails {
        private long processCount;
        private List<ProcessInfo> topCpuProcesses;
        private List<ProcessInfo> topMemoryProcesses;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ProcessInfo {
        private long pid;
        private String command;
        private Double cpuPercent;
        private Long memoryBytes;
        private Double memoryMb;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class NetworkDetails {
        private List<String> hostAddresses;
        private List<NetworkInterfaceDetails> interfaces;
        private String activeInterface;
        private String wifiSsid;
        private String publicIp;
        private Double downloadBytesPerSecond;
        private Double uploadBytesPerSecond;
        private List<String> dnsServers;
        private String defaultGateway;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class NetworkInterfaceDetails {
        private String name;
        private String displayName;
        private String macAddress;
        private boolean up;
        private boolean loopback;
        private List<String> addresses;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ThermalDetails {
        private Integer fanSpeedRpm;
        private String thermalPressure;
        private List<TemperatureDetails> sensors;
        private Double cpuTemperatureCelsius;
        private Double gpuTemperatureCelsius;
        private String status;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class GpuDetails {
        private List<String> models;
        private Double utilizationPercent;
        private Long memoryTotalBytes;
        private Long memoryUsedBytes;
        private Double memoryTotalGb;
        private Double memoryUsedGb;
        private String driverInfo;
        private String metalInfo;
        private String status;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SystemDetails {
        private String loggedInUser;
        private String timezone;
        private String locale;
        private String kernelVersion;
        private String bootTime;
        private List<String> sleepWakeHistory;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class JvmDetails {
        private long processId;
        private String uptime;
        private long uptimeSeconds;
        private long heapUsedBytes;
        private long heapMaxBytes;
        private long nonHeapUsedBytes;
        private Double processCpuUsagePercent;
        private Long openFileDescriptorCount;
        private int threadCount;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RuntimeDetails {
        private String lastStarted;
        private String runningFor;
        private long uptimeSeconds;
    }
}
