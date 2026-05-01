package com.bxv.sysmindmcp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DiskStats {
    private long free;
    private long total;
    private long used;
}
