package com.bxv.sysmindmcp.tools;

import com.bxv.sysmindmcp.model.DiskStats;
import com.bxv.sysmindmcp.model.NewsResult;
import com.bxv.sysmindmcp.model.RamStats;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SystemToolsTest {
    private static final String LOCATION_FEED_TEMPLATE = "https://example.com/rss/search?q={query}";


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
        Object result = new NewsTool(feedUri, LOCATION_FEED_TEMPLATE, uri -> new NewsTool.FeedResponse(200, rss)).execute();

        assertThat(result).isInstanceOf(NewsResult.class);

        NewsResult news = (NewsResult) result;
        assertThat(news.getError()).isNull();
        assertThat(news.getFeedUrl()).isEqualTo(feedUri.toString());
        assertThat(news.getArticles()).hasSize(1);
        assertThat(news.getArticles().getFirst().getTitle()).isEqualTo("First headline");
        assertThat(news.getArticles().getFirst().getSource()).isEqualTo("Example Wire");
        assertThat(news.getArticles().getFirst().getUrl()).isEqualTo("https://example.com/first");
    }

    @Test
    void newsToolUsesLocationSpecificFeedWhenPromptNamesAPlace() {
        AtomicReference<URI> requestedUri = new AtomicReference<>();
        NewsTool tool = new NewsTool(URI.create("https://example.com/rss"), LOCATION_FEED_TEMPLATE, uri -> {
            requestedUri.set(uri);
            return new NewsTool.FeedResponse(200, emptyRss());
        });

        Object result = tool.execute("what is the latest news in New York today?");

        assertThat(result).isInstanceOf(NewsResult.class);
        assertThat(requestedUri.get().toString()).contains("example.com/rss/search");
        assertThat(requestedUri.get().toString()).contains("q=New+York+news");
    }

    @Test
    void newsToolResolvesSearchLanguageCountryAndCeidFromTemplate() {
        AtomicReference<URI> requestedUri = new AtomicReference<>();
        NewsTool tool = new NewsTool(
                "https://example.com/rss?hl={language}&gl={country}&ceid={ceid}",
                "https://example.com/rss/search?q={query}&hl={hl}&gl={gl}&ceid={ceid}",
                "fr-CA",
                "CA",
                "",
                uri -> {
                    requestedUri.set(uri);
                    return new NewsTool.FeedResponse(200, emptyRss());
                });

        tool.execute("show me headlines near Montreal now");

        assertThat(requestedUri.get().toString())
                .contains("q=Montreal+news")
                .contains("hl=fr-CA")
                .contains("gl=CA")
                .contains("ceid=CA:fr");
    }

    @Test
    void newsToolUsesLlmProvidedArgumentsWhenBuildingSearchFeed() {
        AtomicReference<URI> requestedUri = new AtomicReference<>();
        NewsTool tool = new NewsTool(
                "https://example.com/rss?hl={language}&gl={country}&ceid={ceid}",
                "https://example.com/rss/search?q={query}&hl={language}&gl={country}&ceid={ceid}",
                "en-US",
                "US",
                "",
                uri -> {
                    requestedUri.set(uri);
                    return new NewsTool.FeedResponse(200, emptyRss());
                });

        tool.execute("latest Hindi news about AI policy in India", Map.of(
                "query", "AI policy India",
                "language", "hi-IN",
                "country", "IN"));

        assertThat(requestedUri.get().toString())
                .contains("q=AI+policy+India")
                .contains("hl=hi-IN")
                .contains("gl=IN")
                .contains("ceid=IN:hi");
    }

    @Test
    void newsToolUsesDefaultFeedWhenPromptDoesNotNameAPlace() {
        URI feedUri = URI.create("https://example.com/rss");
        AtomicReference<URI> requestedUri = new AtomicReference<>();
        NewsTool tool = new NewsTool(feedUri, LOCATION_FEED_TEMPLATE, uri -> {
            requestedUri.set(uri);
            return new NewsTool.FeedResponse(200, emptyRss());
        });

        tool.execute("get news from the web");

        assertThat(requestedUri.get()).isEqualTo(feedUri);
    }

    private String emptyRss() {
        return """
                <?xml version="1.0" encoding="UTF-8" ?>
                <rss version="2.0">
                  <channel>
                    <title>Test News</title>
                  </channel>
                </rss>
                """;
    }
}
