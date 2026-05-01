package com.bxv.sysmindmcp.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
public class ModelListResponse {
    private String object;
    private List<ModelInfo> data;
}
