package com.bxv.sysmindmcp.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NewsArticle {
    private String title;
    private String source;
    private String url;
    private String publishedAt;
}
