package com.aiagent.tools;

import org.springframework.ai.tool.annotation.Tool;

/**
 * 终止工具（作用是让自主规划智能体能够合理地中断）
 */
public class TerminateTool {

    @Tool(description = """
            Terminate the Agent session — call this when the task is complete.

            WHEN TO CALL (必须调用的时机):
            1. 所有用户要求的任务已完成
            2. 最终回答已给出
            3. 无法继续执行，需要结束会话

            DO NOT call this tool if:
            - 还有待完成的任务或子步骤
            - 需要先回复用户的追问

            After calling doTerminate, the agent loop ends immediately. This should be
            the LAST tool you call in any session.
            完成后必须调用此工具终止任务。
            """)
    public String doTerminate() {
        return "任务结束";
    }
}
