package com.aiagent.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PDFGenerationToolTest {

    @Test
    void generatePDF() {
        PDFGenerationTool tool = new PDFGenerationTool();
        String fileName = "ai-agent-report.pdf";
        String content = "AI Agent 项目报告：ReAct、工具调用、RAG、记忆管理与部署流程。";
        String result = tool.generatePDF(fileName, content);
        assertNotNull(result);
    }
}
