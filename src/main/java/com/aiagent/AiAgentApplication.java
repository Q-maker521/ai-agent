package com.aiagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {
        org.springframework.ai.mcp.client.autoconfigure.McpClientAutoConfiguration.class,
        org.springframework.ai.mcp.client.autoconfigure.McpToolCallbackAutoConfiguration.class,
        DataSourceAutoConfiguration.class
})
public class AiAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiAgentApplication.class, args);
    }

}
