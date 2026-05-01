package com.bxv.sysmindmcp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "LLM_URL=http://localhost:1234",
        "LLM_TIMEOUT=3m",
        "NEWS_FEED_URL=https://news.google.com/rss?hl=en-US&gl=US&ceid=US:en"
})
class SysMindMcpApplicationTests {

    @Test
    void contextLoads() {
    }

}
