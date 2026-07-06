package com.aiagent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 终端操作工具 — 执行系统命令并返回输出。
 *
 * <p>自动检测操作系统选择对应 Shell（Windows: cmd.exe, Linux/Mac: sh）。
 * 进程超时 30 秒，同时捕获 stdout 和 stderr，输出截断至 4000 字符。
 *
 * <h3>安全设计</h3>
 * 在命令执行前做两层校验：
 * <ol>
 *   <li><b>危险模式拦截</b>：拒绝包含已知危险操作（递归删除、格式化、fork 炸弹等）的命令</li>
 *   <li><b>安全命令白名单</b>：只允许执行白名单内的命令，其他一律拒绝</li>
 * </ol>
 * 校验发生在 {@link #executeTerminalCommand(String)} 的第一行，
 * 在启动任何系统进程之前，确保零副作用。
 */
public class TerminalOperationTool {

    private static final int TIMEOUT_SECONDS = 30;
    private static final int MAX_OUTPUT_LENGTH = 4_000;

    // ─────────── 安全校验 ───────────

    /**
     * 危险命令模式（拒绝）。这些是明确有害的操作。
     */
    private static final List<Pattern> DANGEROUS_PATTERNS = List.of(
            // 递归强制删除
            Pattern.compile("\\brm\\s+.*-.*rf?\\b", Pattern.CASE_INSENSITIVE),
            // Windows 删除
            Pattern.compile("\\bdel\\s+/[fsq]\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\brmdir\\s+/[sq]\\b", Pattern.CASE_INSENSITIVE),
            // 磁盘格式化
            Pattern.compile("\\bformat\\s", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bmkfs\\.", Pattern.CASE_INSENSITIVE),
            // Fork 炸弹
            Pattern.compile(":\\(\\)\\s*\\{"),
            Pattern.compile("%0\\|%0"),
            // 覆盖关键系统文件
            Pattern.compile(">\\s*/dev/sda"),
            Pattern.compile(">\\s*/dev/null\\s*/dev/"),
            // 关机/重启
            Pattern.compile("\\b(shutdown|reboot|halt|poweroff|init\\s+[06])\\b", Pattern.CASE_INSENSITIVE),
            // 修改系统关键权限
            Pattern.compile("\\bchmod\\s+[-+]?[rwx]*7", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bchown\\s+root", Pattern.CASE_INSENSITIVE),
            // 破坏性磁盘操作
            Pattern.compile("\\bdd\\s+if=", Pattern.CASE_INSENSITIVE),
            // 下载并执行远程脚本（常见攻击手法）
            Pattern.compile("\\b(curl|wget)\\s+.*\\|\\s*(sh|bash|cmd)", Pattern.CASE_INSENSITIVE)
    );

    /**
     * 允许执行的安全命令白名单。
     * 不在白名单中的命令一律拒绝，保证安全性。
     */
    private static final Set<String> ALLOWED_COMMANDS = Set.of(
            // 文件浏览
            "ls", "dir", "tree", "pwd", "cd",
            // 文件查看
            "cat", "head", "tail", "more", "less", "type",
            // 文件信息
            "wc", "du", "df", "stat", "file",
            // 文本搜索
            "grep", "find", "findstr", "where",
            // 网络诊断（只读）
            "ping", "tracert", "traceroute", "nslookup",
            // 进程信息（只读）
            "ps", "tasklist",
            // 开发工具
            "java", "javac", "python", "python3", "node", "npm", "npx",
            "mvn", "mvnw", "gradle", "git",
            // 系统信息（只读）
            "uname", "hostname", "whoami", "date", "time", "echo",
            "set", "env", "printenv", "export",
            // 文件操作（不含删除和权限修改）
            "mkdir", "touch", "cp", "copy", "mv", "move", "rename",
            // 压缩解压
            "tar", "zip", "unzip", "gzip", "gunzip"
    );

    /**
     * 校验命令是否安全。不安全时返回错误信息，安全时返回 null。
     */
    private String validateCommand(String command) {
        if (command == null || command.isBlank()) {
            return "拒绝执行空命令";
        }

        // 第一层：危险模式检查
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(command).find()) {
                return "安全拦截：命令包含危险操作，已被拒绝执行。匹配模式: " + pattern.pattern();
            }
        }

        // 第二层：提取命令名，检查是否在白名单中
        String cmdName = extractCommandName(command);
        if (cmdName == null || !ALLOWED_COMMANDS.contains(cmdName)) {
            return String.format(
                    "安全拦截：命令 '%s' 不在允许执行的白名单中。"
                            + "允许的命令: %s",
                    cmdName != null ? cmdName : command,
                    String.join(", ", ALLOWED_COMMANDS));
        }

        return null; // 安全
    }

    /**
     * 从命令字符串中提取命令名（第一个非管道/重定向的单词）。
     */
    private String extractCommandName(String command) {
        // 去掉前导空格和路径前缀
        String trimmed = command.trim();
        // 处理路径形式：/usr/bin/ls → ls
        if (trimmed.contains("/")) {
            int lastSlash = trimmed.lastIndexOf('/');
            if (lastSlash < trimmed.length() - 1) {
                trimmed = trimmed.substring(lastSlash + 1);
            }
        }
        // 处理 cmd /c 或 sh -c 包装形式
        trimmed = trimmed.replaceAll("^(cmd\\.exe|cmd|sh|bash|powershell\\.exe|pwsh)\\s+[/-]c\\s+", "");

        // 取第一个空白字符之前的部分作为命令名
        String[] parts = trimmed.split("\\s+", 2);
        if (parts.length == 0) return null;

        // 去掉可能的 .exe 后缀（Windows）
        String name = parts[0].replaceAll("\\.(exe|bat|cmd|ps1)$", "");
        return name.toLowerCase();
    }

    // ─────────── 命令执行 ───────────

    @Tool(description = """
            Execute a shell command in the terminal and return the output.

            WHEN TO USE: For system automation tasks, file system operations (mkdir, ls,
            etc.), or running scripts. Use ONLY when the task cannot be accomplished with
            other tools.

            WHEN NOT TO USE: For tasks that can be done with file_write, web_search, or
            other dedicated tools. Avoid using terminal as a workaround.

            SECURITY: Only safe, whitelisted commands are allowed. Dangerous operations
            (rm -rf, format, fork bombs, etc.) are rejected automatically.

            OUTPUT: Captures stdout and stderr. Output is truncated to 4000 characters.
            Process has a 30-second timeout.

            TIP: Prefer dedicated tools (file_write, web_search, web_scrape) over raw
            terminal commands when a suitable tool exists.
            """)
    public String executeTerminalCommand(
            @ToolParam(description = "Command to execute in the terminal") String command) {

        // ═══════ 安全校验入口 ═══════
        String validationError = validateCommand(command);
        if (validationError != null) {
            return validationError;
        }

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
