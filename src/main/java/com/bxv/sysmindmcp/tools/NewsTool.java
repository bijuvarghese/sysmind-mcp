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
import java.util.Map;
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

    private final String feedUrlTemplate;
    private final String locationFeedUrlTemplate;
    private final String language;
    private final String country;
    private final String ceid;
    private final FeedClient feedClient;

    @Autowired
    public NewsTool(
            @Value("${news.feed-url}") String feedUrl,
            @Value("${news.location-feed-url-template}") String locationFeedUrlTemplate,
            @Value("${news.language:en-US}") String language,
            @Value("${news.country:US}") String country,
            @Value("${news.ceid:}") String ceid) {
        this(feedUrl, locationFeedUrlTemplate, language, country, ceid, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build());
    }

    NewsTool(URI feedUri, String locationFeedUrlTemplate, HttpClient httpClient) {
        this(feedUri.toString(), locationFeedUrlTemplate, "en-US", "US", "", httpClient);
    }

    NewsTool(URI feedUri, String locationFeedUrlTemplate, FeedClient feedClient) {
        this(feedUri.toString(), locationFeedUrlTemplate, "en-US", "US", "", feedClient);
    }

    NewsTool(
            String feedUrlTemplate,
            String locationFeedUrlTemplate,
            String language,
            String country,
            String ceid,
            HttpClient httpClient) {
        this(feedUrlTemplate, locationFeedUrlTemplate, language, country, ceid, uri -> {
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
        return execute(prompt, Map.of());
    }

    @Override
    public Object execute(String prompt, Map<String, String> arguments) {
        Instant fetchedAt = Instant.now();
        URI requestUri = feedUriFor(prompt, arguments);

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

    private URI feedUriFor(String prompt, Map<String, String> arguments) {
        String location = extractLocation(prompt);
        String query = firstText(arguments, "query", "search", "topic");
        String resolvedLanguage = firstText(arguments, "language", "hl");
        String resolvedCountry = firstText(arguments, "country", "gl");
        String resolvedCeid = firstText(arguments, "ceid");

        if (resolvedLanguage.isBlank()) {
            resolvedLanguage = configuredLanguage();
        }

        if (resolvedCountry.isBlank()) {
            resolvedCountry = configuredCountry();
        }

        if (resolvedCeid.isBlank()) {
            resolvedCeid = configuredCeid(resolvedLanguage, resolvedCountry);
        }

        if (query.isBlank() && !location.isBlank()) {
            query = location + " news";
        }

        if (query.isBlank()) {
            return resolveFeedUri(feedUrlTemplate, "", resolvedLanguage, resolvedCountry, resolvedCeid);
        }

        return resolveFeedUri(locationFeedUrlTemplate, query, resolvedLanguage, resolvedCountry, resolvedCeid);
    }

    private URI resolveFeedUri(String template, String query, String language, String country, String ceid) {
        return URI.create(template
                .replace("{query}", encode(query))
                .replace("{language}", language)
                .replace("{hl}", language)
                .replace("{country}", country)
                .replace("{gl}", country)
                .replace("{languageCode}", languageCode(language))
                .replace("{ceid}", ceid));
    }

    private String firstText(Map<String, String> arguments, String... keys) {
        if (arguments == null || arguments.isEmpty()) {
            return "";
        }

        for (String key : keys) {
            String value = arguments.get(key);

            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }

        return "";
    }

    private String languageCode(String language) {
        return language.split("[-_]", 2)[0];
    }

    private String configuredLanguage() {
        return language == null || language.isBlank() ? "en-US" : language;
    }

    private String configuredCountry() {
        return country == null || country.isBlank() ? "US" : country;
    }

    private String configuredCeid(String language, String country) {
        if (ceid != null && !ceid.isBlank()) {
            return ceid;
        }

        return country + ":" + languageCode(language);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
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
