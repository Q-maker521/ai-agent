package com.aiagent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * 终端操作工具 — 执行系统命令并返回输出。
 *
 * <p>自动检测操作系统选择对应 Shell（Windows: cmd.exe, Linux/Mac: sh）。
 * 进程超时 30 秒，同时捕获 stdout 和 stderr，输出截断至 4000 字符。
 */
public class TerminalOperationTool {

    private static final int TIMEOUT_SECONDS = 30;
    private static final int MAX_OUTPUT_LENGTH = 4_000;

    @Tool(description = "Execute a command in the terminal and return the output")
    public String executeTerminalCommand(
            @ToolParam(description = "Command to execute in the terminal") String command) {
        StringBuilder output = new StringBuilder();
        try {
            ProcessBuilder builder = buildProcess(command);
            builder.redirectErrorStream(false);  // keep stderr separate
            Process process = builder.start();

            // 读取 stdout
            StringBuilder stdout = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stdout.append(line).append("\n");
                }
            }

            // 读取 stderr
            StringBuilder stderr = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stderr.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "Command timed out after " + TIMEOUT_SECONDS + " seconds and was terminated: " + command;
            }

            int exitCode = process.exitValue();

            if (!stdout.isEmpty()) {
                output.append(stdout);
            }
            if (!stderr.isEmpty()) {
                output.append("[STDERR]\n").append(stderr);
            }
            if (exitCode != 0) {
                output.append("[EXIT CODE: ").append(exitCode).append("]");
            }
            if (output.isEmpty()) {
                output.append("Command completed with no output.");
            }

            String result = output.toString();
            if (result.length() > MAX_OUTPUT_LENGTH) {
                result = result.substring(0, MAX_OUTPUT_LENGTH)
                        + "\n\n[... output truncated, total length: "
                        + result.length() + " characters]";
            }
            return result;

        } catch (Exception e) {
            return "Error executing command: " + e.getMessage();
        }
    }

    /**
     * 根据操作系统选择对应的 Shell。
     */
    private ProcessBuilder buildProcess(String command) {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return new ProcessBuilder("cmd.exe", "/c", command);
        } else {
            return new ProcessBuilder("sh", "-c", command);
        }
    }
}
