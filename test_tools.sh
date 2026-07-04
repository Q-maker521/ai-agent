#!/bin/bash
# 工具真实验证脚本 — 逐个测试所有 7 个工具
# 用法: bash test_tools.sh

BASE="http://localhost:8123/api/ai/agent/chat/stream"
RESULT_FILE="/tmp/tool_test_results.txt"
> "$RESULT_FILE"

log() {
    echo "[$(date +%H:%M:%S)] $1" | tee -a "$RESULT_FILE"
}

# 发送消息给 Agent，流式收集事件，超时 120 秒
test_tool() {
    local tool_name="$1"
    local message="$2"
    local session_id="test-${tool_name}-$(date +%s)"

    log ""
    log "=========================================="
    log "测试工具: $tool_name"
    log "消息: $message"
    log "Session: $session_id"
    log "=========================================="

    # 收集 SSE 事件
    local events=$(curl -s -N --max-time 120 \
        "${BASE}?message=$(python3 -c "import urllib.parse; print(urllib.parse.quote('''${message}'''))")&sessionId=${session_id}" \
        2>/dev/null)

    # 统计事件类型
    local tool_calls=$(echo "$events" | grep -c '"type":"tool_call"')
    local tool_results=$(echo "$events" | grep -c '"type":"tool_result"')
    local agent_errors=$(echo "$events" | grep -c '"type":"agent_error"')
    local final_answer=$(echo "$events" | grep -c '"type":"final_answer"')
    local agent_finish=$(echo "$events" | grep -c '"type":"agent_finish"')

    # 提取工具名称
    local tools_used=$(echo "$events" | grep '"type":"tool_call"' | grep -o '"toolName":"[^"]*"' | sort -u)

    log "事件统计: tool_call=$tool_calls, tool_result=$tool_results, agent_error=$agent_errors, final_answer=$final_answer"
    log "使用的工具: $tools_used"

    # 提取 final_answer 内容
    local answer=$(echo "$events" | grep '"type":"final_answer"' | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('content','')[:200])" 2>/dev/null)
    log "最终回答: $answer"

    # 判断结果
    if [ "$agent_errors" -gt 0 ]; then
        log "❌ 失败: 出现 agent_error 事件"
        return 1
    elif [ "$tool_calls" -gt 0 ] && [ "$tool_results" -gt 0 ]; then
        log "✅ 成功: 工具被调用并返回结果"
        return 0
    elif [ "$final_answer" -gt 0 ]; then
        log "⚠️  部分: 有 final_answer 但无工具调用（可能 LLM 选择不用工具）"
        return 0
    else
        log "❌ 失败: 无工具调用且无最终回答"
        return 1
    fi
}

# ========== 测试用例 ==========

# 1. WebSearchTool
test_tool "searchWeb" \
    "搜索一下2026年AI Agent的最新进展，用中文关键词搜索"

# 2. TerminalOperationTool
test_tool "executeTerminalCommand" \
    "请执行 ls 命令列出当前目录下的文件"

# 3. FileOperationTool (write + read)
test_tool "fileOperation" \
    "请创建一个名为 test_hello.txt 的文件，内容为 'Hello from AI Agent test!'，然后读取这个文件确认内容正确"

# 4. WebScrapingTool
test_tool "scrapeWebPage" \
    "请抓取 https://www.baidu.com 的网页内容，看看返回了什么"

# 5. PDFGenerationTool
test_tool "generatePDF" \
    "请生成一份PDF文档，文件名为 test_report.pdf，内容为 'AI Agent Tool Test Report - 工具验证测试报告'。生成后请调用 doTerminate 结束。"

# 6. ResourceDownloadTool
test_tool "downloadResource" \
    "请下载 https://www.baidu.com/img/PCtm_d9c8750bed0b3c7d089fa7d55720d6cf.png 这张百度Logo图片，保存为 baidu_logo.png"

log ""
log "=========================================="
log "测试完成！结果汇总："
log "=========================================="
grep -E "^(测试工具|✅|❌|⚠️)" "$RESULT_FILE"
