package com.bxv.sysmindmcp.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@AllArgsConstructor
public class NewsResult {
    private Instant fetchedAt;
    private String feedUrl;
    private List<NewsArticle> articles;
    private String error;
}
