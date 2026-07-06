package com.aiagent.agent.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 简单任务规划器。
 *
 * <p>在 Agent 开始执行前，用一次轻量级 LLM 调用分析用户任务，
 * 拆解为 3-7 个子步骤并注入到 system prompt 中。
 * 规划失败时静默降级，Agent 照常执行。
 */
@Component
@Slf4j
public class SimplePlanner {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 为给定任务生成执行计划。
     *
     * @param userTask  用户任务描述
     * @param chatClient 用于规划调用的 ChatClient
     * @return Plan 对象，规划失败时返回 null
     */
    public Plan plan(String userTask, ChatClient chatClient) {
        if (userTask == null || userTask.isBlank()) {
            return null;
        }
        // 简单任务（<20 字）不需要规划
        if (userTask.trim().length() < 20) {
            return null;
        }

        String planningPrompt = """
                你是一个任务规划器。请分析以下用户任务，将其拆解为 3-7 个独立子步骤。
                每个步骤应该明确、可独立执行。

                用户任务：%s

                请只用 JSON 格式输出计划（不要输出任何其他文本）：
                {
                  "summary": "一句话概括整体计划",
                  "steps": [
                    {"number": 1, "goal": "该步要达成的目标", "toolHint": "可能用到的工具（web_search/web_scrape/file_write等），不需要则填none。generate_pdf/download_resource 仅在用户明确要求时才建议", "successCriteria": "该步完成的判断标准"}
                  ]
                }
                """.formatted(userTask);

        try {
            String response = chatClient.prompt()
                    .user(planningPrompt)
                    .call()
                    .content();
            if (response == null || response.isBlank()) {
                return null;
            }
            return parsePlan(response);
        } catch (Exception e) {
            log.warn("Failed to generate plan: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 将计划格式化为可注入 system prompt 的文本。
     */
    public String formatPlanForPrompt(Plan plan) {
        if (plan == null || plan.steps() == null || plan.steps().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n== 执行计划 ==\n");
        sb.append("目标：").append(plan.summary()).append("\n\n");
        for (Plan.PlanStep step : plan.steps()) {
            sb.append(String.format("%d. %s", step.number(), step.goal()));
            if (step.toolHint() != null && !step.toolHint().equals("none")) {
                sb.append(String.format(" [建议工具: %s]", step.toolHint()));
            }
            sb.append(String.format("\n   完成标准: %s\n", step.successCriteria()));
        }
        sb.append("\n请按计划逐步执行，每完成一步检查是否达到完成标准。如果某步提前完成或不再需要，可以跳过。\n");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private Plan parsePlan(String jsonResponse) {
        // 清理可能的 markdown 代码块包装
        String json = jsonResponse.trim();
        if (json.startsWith("```")) {
            json = json.substring(json.indexOf('\n') + 1);
            if (json.endsWith("```")) {
                json = json.substring(0, json.lastIndexOf("```")).trim();
            }
        }
        try {
            Map<String, Object> root = objectMapper.readValue(json, Map.class);
            String summary = (String) root.getOrDefault("summary", "");
            List<Map<String, Object>> stepList =
                    (List<Map<String, Object>>) root.get("steps");
            List<Plan.PlanStep> steps = new ArrayList<>();
            if (stepList != null) {
                for (Map<String, Object> s : stepList) {
                    steps.add(new Plan.PlanStep(
                            toInt(s.get("number")),
                            (String) s.getOrDefault("goal", ""),
                            (String) s.getOrDefault("toolHint", "none"),
                            (String) s.getOrDefault("successCriteria", "")
                    ));
                }
            }
            return new Plan(summary, steps);
        } catch (Exception e) {
            log.warn("Failed to parse plan JSON: {}", e.getMessage());
            return null;
        }
    }

    private int toInt(Object obj) {
        if (obj instanceof Number n) return n.intValue();
        if (obj instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }
}
