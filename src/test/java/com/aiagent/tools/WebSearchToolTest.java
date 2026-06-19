package com.aiagent.tools;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class WebSearchToolTest {

    @Test
    void searchWeb() {
        WebSearchTool webSearchTool = new WebSearchTool();
        String query = "AI technology news 2026";
        String result = webSearchTool.searchWeb(query);
        Assertions.assertNotNull(result);
        // 搜索至少应该返回非空字符串（即使网络不可达也应有错误信息）
        assertFalse(result.isEmpty());
    }
}
