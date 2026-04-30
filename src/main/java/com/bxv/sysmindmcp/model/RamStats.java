package com.bxv.sysmindmcp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class RamStats {

    private long free;
    private long total;
    private long used;

    public RamStats(long free, long total) {
        this.free = free;
        this.total = total;
        this.used = total - free;
    }

    public RamStats() {
    }
}