package com.bxv.sysmindmcp.tools;

import com.bxv.sysmindmcp.model.RamStats;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.lang.management.*;

@Component
@AllArgsConstructor
public class RamTool implements SystemTool {

    @Override
    public String name() {
        return "ram_usage";
    }

    @Override
    public String version() {
        return "v1";
    }

    @Override
    public String description() {
        return "Return amount of RAM used and available memory";
    }

    @Override
    public Object execute() {
        try {
            com.sun.management.OperatingSystemMXBean osBean = 
                ManagementFactory.getPlatformMXBean(com.sun.management.OperatingSystemMXBean.class);
            
            long total = osBean.getTotalMemorySize();
            long free = osBean.getFreeMemorySize();

            if (total == 0) {
                return new RamStats(0, 0);
            }

            return new RamStats(free, total);
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }
}
