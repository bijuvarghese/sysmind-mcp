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
public class ProcessDiagnosticsResult {
    private long processCount;
    private List<TopProcess> topProcesses;
    private String generatedAt;
}
