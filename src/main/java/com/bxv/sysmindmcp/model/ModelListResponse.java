package com.bxv.sysmindmcp.model;

import lombok.Data;
import java.util.List;

@Data
public class ModelListResponse {
    private String object;
    private List<ModelInfo> data;
}