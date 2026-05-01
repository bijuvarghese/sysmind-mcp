package com.bxv.sysmindmcp.tools;

import com.bxv.sysmindmcp.model.DiskStats;
import com.bxv.sysmindmcp.model.NewsResult;
import com.bxv.sysmindmcp.model.RamStats;
import org.junit.jupiter.api.Test;

import java.net.URI;

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

    @Test
    void newsToolReturnsArticlesFromRssFeed() throws Exception {
        String rss = """
                <?xml version="1.0" encoding="UTF-8" ?>
                <rss version="2.0">
                  <channel>
                    <title>Test News</title>
                    <item>
                      <title>First headline</title>
                      <source>Example Wire</source>
                      <link>https://example.com/first</link>
                      <pubDate>Thu, 30 Apr 2026 10:00:00 GMT</pubDate>
                    </item>
                  </channel>
                </rss>
                """;
        URI feedUri = URI.create("https://example.com/rss");
        Object result = new NewsTool(feedUri, uri -> new NewsTool.FeedResponse(200, rss)).execute();

        assertThat(result).isInstanceOf(NewsResult.class);

        NewsResult news = (NewsResult) result;
        assertThat(news.getError()).isNull();
        assertThat(news.getFeedUrl()).isEqualTo(feedUri.toString());
        assertThat(news.getArticles()).hasSize(1);
        assertThat(news.getArticles().getFirst().getTitle()).isEqualTo("First headline");
        assertThat(news.getArticles().getFirst().getSource()).isEqualTo("Example Wire");
        assertThat(news.getArticles().getFirst().getUrl()).isEqualTo("https://example.com/first");
    }
}
