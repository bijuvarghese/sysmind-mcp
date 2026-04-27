package com.bxv.sysmindmcp.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DiskStats {
    long free;
    long total;
    long used;
}
