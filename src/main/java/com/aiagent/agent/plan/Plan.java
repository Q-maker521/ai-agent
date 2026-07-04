package com.aiagent.agent.plan;

import java.util.List;

/**
 * 任务执行计划。
 *
 * @param summary 计划一句话概括
 * @param steps   子步骤列表
 */
public record Plan(
        String summary,
        List<PlanStep> steps
) {
    public record PlanStep(
            int number,
            String goal,
            String toolHint,
            String successCriteria
    ) {}
}
