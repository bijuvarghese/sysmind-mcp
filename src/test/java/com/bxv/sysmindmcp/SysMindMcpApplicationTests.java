package com.bxv.sysmindmcp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "LLM_URL=http://localhost:1234",
        "LLM_TIMEOUT=3m",
        "NEWS_FEED_URL=https://example.com/rss",
        "NEWS_LOCATION_FEED_URL_TEMPLATE=https://example.com/rss/search?q={query}"
})
class SysMindMcpApplicationTests {

    @Test
    void contextLoads() {
    }

}
