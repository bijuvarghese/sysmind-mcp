package com.bxv.sysmindmcp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TopProcess {
    private long pid;
    private String name;
    private String command;
    private long cpuPercent;
    private long memoryMb;
    private long diskReadMb;
    private long diskWriteMb;
    private String energyImpact;
    private String runtime;
    private String user;
    private String category;
    private String risk;
    private String explanation;
}
