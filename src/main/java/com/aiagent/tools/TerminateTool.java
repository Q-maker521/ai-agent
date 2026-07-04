package com.aiagent.tools;

import org.springframework.ai.tool.annotation.Tool;

/**
 * 终止工具（作用是让自主规划智能体能够合理地中断）
 */
public class TerminateTool {

    @Tool(description = """
            Terminate the Agent session — call this when the task is complete.

            WHEN TO CALL (必须调用的时机):
            1. All user-requested tasks have been completed successfully
            2. The final answer has been given, PDF/file has been saved
            3. You cannot proceed further and need to end the session

            DO NOT call this tool if:
            - There are still pending tasks or sub-steps to complete
            - The user asked a clarifying question that needs a response first

            After calling doTerminate, the agent loop ends immediately. This should be
            the LAST tool you call in any session.
            完成后必须调用此工具终止任务。
            """)
    public String doTerminate() {
        return "任务结束";
    }
}
