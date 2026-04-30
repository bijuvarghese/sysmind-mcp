package com.bxv.sysmindmcp.tools;

import com.bxv.sysmindmcp.model.DiskStats;
import com.bxv.sysmindmcp.model.RamStats;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SystemToolsTest {

    @Test
    void diskToolReturnsConsistentDiskStats() {
        Object result = new DiskTool().execute();

        assertThat(result).isInstanceOf(DiskStats.class);

        DiskStats stats = (DiskStats) result;
        assertThat(stats.getTotal()).isGreaterThan(0);
        assertThat(stats.getFree()).isGreaterThanOrEqualTo(0);
        assertThat(stats.getUsed()).isEqualTo(stats.getTotal() - stats.getFree());
    }

    @Test
    void ramToolReturnsConsistentRamStats() {
        Object result = new RamTool().execute();

        assertThat(result).isInstanceOf(RamStats.class);

        RamStats stats = (RamStats) result;
        assertThat(stats.getTotal()).isGreaterThanOrEqualTo(0);
        assertThat(stats.getFree()).isGreaterThanOrEqualTo(0);
        assertThat(stats.getUsed()).isEqualTo(stats.getTotal() - stats.getFree());
    }
}
