package com.aiagent.app;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

@SpringBootTest
class KnowledgeBaseServiceTest {

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    @Test
    void testChat() {
        String chatId = UUID.randomUUID().toString();
        // 第一轮
        String message = "你好，我正在学习 AI Agent 工程化落地";
        String answer = knowledgeBaseService.doChat(message, chatId);
        // 第二轮
        message = "我想设计一个支持工具调用和知识库问答的智能体项目";
        answer = knowledgeBaseService.doChat(message, chatId);
        Assertions.assertNotNull(answer);
        // 第三轮
        message = "刚才我想做的项目方向是什么？帮我回忆一下";
        answer = knowledgeBaseService.doChat(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithReport() {
        String chatId = UUID.randomUUID().toString();
        String message = "你好，我想做一个可展示在简历上的 AI Agent 项目，请帮我拆解需求和实现路径";
        KnowledgeBaseService.ResearchSummary loveReport = knowledgeBaseService.doChatWithReport(message, chatId);
        Assertions.assertNotNull(loveReport);
    }

    @Test
    void doChatWithRag() {
        String chatId = UUID.randomUUID().toString();
        String message = "我已经结婚了，但是婚后关系不太亲密，怎么办？";
        String answer = knowledgeBaseService.doChatWithRag(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithTools() {
        // 测试联网搜索问题的答案
        testMessage("帮我搜索近期 AI Agent 工程化落地的常见技术方案，并给出简要对比。");

        // 测试网页抓取：技术文章分析
        testMessage("抓取 Spring AI 官方文档首页，并总结它适合构建 Agent 的能力。");

        // 测试资源下载：图片下载
        testMessage("直接下载一张适合做手机壁纸的星空情侣图片为文件");

        // 测试终端操作：执行代码
        testMessage("执行 Python3 脚本来生成数据分析报告");

        // 测试文件操作：保存用户档案
        testMessage("保存我的恋爱档案为文件");

        // 测试 PDF 生成
        testMessage("生成一份‘七夕约会计划’PDF，包含餐厅预订、活动流程和礼物清单");
    }

    private void testMessage(String message) {
        String chatId = UUID.randomUUID().toString();
        String answer = knowledgeBaseService.doChatWithTools(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithMcp() {
        String chatId = UUID.randomUUID().toString();
        // 测试地图 MCP
//              String message = "我的另一半居住在上海静安区静安寺地铁站，请帮我找到 5 公里内合适的约会地点并给我返回相应的链接";
//              String answer =  knowledgeBaseService.doChatWithMcp(message, chatId);
//              Assertions.assertNotNull(answer);
        // 测试图片搜索 MCP
              String message = "帮我在网上搜索一些哄对象开心的照片";
              String answer =  knowledgeBaseService.doChatWithMcp(message, chatId);
              Assertions.assertNotNull(answer);
    }
}
