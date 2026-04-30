package com.bxv.sysmindmcp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "LLM_URL=http://localhost:1234",
        "LLM_TIMEOUT=3m"
})
class SysMindMcpApplicationTests {

    @Test
    void contextLoads() {
    }

}
