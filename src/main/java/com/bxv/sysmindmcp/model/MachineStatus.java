package com.bxv.sysmindmcp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private RuntimeDetails systemStatus;
    private String generatedAt;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CpuDetails {
        private int physicalCpuCores;
        private int logicalCpuCores;
        private long currentCpuSpeedMhz;
        private long maxCpuSpeedMhz;
        private double currentCpuUsagePercent;
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
        private String status;
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
