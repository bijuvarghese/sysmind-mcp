package com.bxv.sysmindmcp.tools;

import com.bxv.sysmindmcp.model.NewsArticle;
import com.bxv.sysmindmcp.model.NewsResult;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class NewsTool implements SystemTool {
    private static final int MAX_ARTICLES = 10;
    private static final Pattern LOCATION_PATTERN = Pattern.compile(
            "\\b(?:in|from|for|near|around)\\s+([\\p{L}][\\p{L}\\p{M} .,'-]{1,80}?)(?=\\s+(?:news|headlines|updates|stories|today|latest|now|right now)\\b|[?.!,]|$)",
            Pattern.CASE_INSENSITIVE);
    private static final Set<String> NON_PLACE_TERMS = Set.of(
            "web", "the web", "internet", "the internet", "online");

    private final URI feedUri;
    private final String locationFeedUrlTemplate;
    private final FeedClient feedClient;

    @Autowired
    public NewsTool(
            @Value("${news.feed-url}") String feedUrl,
            @Value("${news.location-feed-url-template}") String locationFeedUrlTemplate) {
        this(URI.create(feedUrl), locationFeedUrlTemplate, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build());
    }

    NewsTool(URI feedUri, String locationFeedUrlTemplate, HttpClient httpClient) {
        this(feedUri, locationFeedUrlTemplate, uri -> {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "SysMindMCP/1.0")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            return new FeedResponse(response.statusCode(), response.body());
        });
    }

    @Override
    public String name() {
        return "latest_news";
    }

    @Override
    public String version() {
        return "v1";
    }

    @Override
    public String description() {
        return "Fetch the latest web news headlines from an RSS feed";
    }

    @Override
    public Object execute() {
        return execute(null);
    }

    @Override
    public Object execute(String prompt) {
        Instant fetchedAt = Instant.now();
        URI requestUri = feedUriFor(prompt);

        try {
            FeedResponse response = feedClient.fetch(requestUri);

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new NewsResult(fetchedAt, requestUri.toString(), List.of(),
                        "News feed returned HTTP status " + response.statusCode());
            }

            return new NewsResult(fetchedAt, requestUri.toString(), parseArticles(response.body(), requestUri), null);
        } catch (Exception e) {
            return new NewsResult(fetchedAt, requestUri.toString(), List.of(), e.getMessage());
        }
    }

    private URI feedUriFor(String prompt) {
        String location = extractLocation(prompt);

        if (location.isBlank()) {
            return feedUri;
        }

        String query = URLEncoder.encode(location + " news", StandardCharsets.UTF_8);
        return URI.create(locationFeedUrlTemplate.replace("{query}", query));
    }

    private String extractLocation(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "";
        }

        Matcher matcher = LOCATION_PATTERN.matcher(prompt);

        if (!matcher.find()) {
            return "";
        }

        String location = normalizeLocation(matcher.group(1));

        if (NON_PLACE_TERMS.contains(location.toLowerCase(Locale.ROOT))) {
            return "";
        }

        return location;
    }

    private String normalizeLocation(String location) {
        return location
                .replaceAll("(?i)^the\\s+", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private List<NewsArticle> parseArticles(String xml, URI sourceUri) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setExpandEntityReferences(false);

        Document document = factory.newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml)));
        NodeList items = document.getElementsByTagName("item");
        List<NewsArticle> articles = new ArrayList<>();

        for (int i = 0; i < items.getLength() && articles.size() < MAX_ARTICLES; i++) {
            Node item = items.item(i);
            if (item.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element itemElement = (Element) item;
            String title = childText(itemElement, "title");

            if (title.isBlank()) {
                continue;
            }

            articles.add(new NewsArticle(
                    title,
                    source(itemElement, sourceUri),
                    childText(itemElement, "link"),
                    childText(itemElement, "pubDate")));
        }

        return articles;
    }

    private String source(Element itemElement, URI sourceUri) {
        String source = childText(itemElement, "source");

        if (!source.isBlank()) {
            return source;
        }

        return sourceUri.getHost();
    }

    private String childText(Element element, String tagName) {
        NodeList nodes = element.getElementsByTagName(tagName);

        if (nodes.getLength() == 0) {
            return "";
        }

        return nodes.item(0).getTextContent().trim();
    }

    @FunctionalInterface
    interface FeedClient {
        FeedResponse fetch(URI uri) throws Exception;
    }

    record FeedResponse(int statusCode, String body) {
    }
}
