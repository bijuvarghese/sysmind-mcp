package com.bxv.sysmindmcp.tools;

import com.bxv.sysmindmcp.model.DiskStats;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.View;

import java.nio.file.*;

@Component
@AllArgsConstructor
public class DiskTool implements SystemTool {
    private final View error;

    @Override
    public String name() {
        return "disk_usage";
    }

    @Override
    public String version() {
        return "v1";
    }

    @Override
    public String description() {
        return "Return amount of disk used and available space";
    }

    @Override
    public Object execute() {
        try {
            FileStore fileStore = FileSystems.getDefault()
                    .getFileStores()
                    .iterator()
                    .next();
            long free = fileStore.getUnallocatedSpace();
            long total = fileStore.getTotalSpace();
            long used = total - free;
            return new DiskStats(free, total, used);
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }

    }
}
