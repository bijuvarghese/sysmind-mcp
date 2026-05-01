package com.bxv.sysmindmcp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RamStats {

    private long free;
    private long total;
    private long used;

    public RamStats(long free, long total) {
        this.free = free;
        this.total = total;
        this.used = total - free;
    }
}
