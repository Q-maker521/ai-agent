#!/bin/bash
# 10 条真实用户对话测试 — 观察思考链与返回结果

BASE="http://localhost:8123/api/ai/agent/chat/stream"

run_test() {
    local num="$1"
    local title="$2"
    local message="$3"
    local session="test10-${num}-$(date +%s)"
    local encoded=$(python3 -c "import urllib.parse; print(urllib.parse.quote('''${message}'''))")

    echo ""
    echo "══════════════════════════════════════════════"
    echo "📝 测试 ${num}/10: ${title}"
    echo "📨 用户: ${message}"
    echo "══════════════════════════════════════════════"

    local step=0 tool_count=0 final="" error="" steps_detail=""
    local start_time=$(date +%s)

    while IFS= read -r line; do
        [[ "$line" != data:* ]] && continue
        json="${line#data:}"
        type=$(echo "$json" | python3 -c "import sys,json; print(json.load(sys.stdin).get('type',''))" 2>/dev/null)

        case "$type" in
            step_start)
                step=$((step + 1))
                ;;
            thinking)
                content=$(echo "$json" | python3 -c "import sys,json; c=json.load(sys.stdin).get('content','')[:100]; print(c.replace(chr(10),' '))" 2>/dev/null)
                [[ "$content" != "正在调用 LLM 分析当前状态..." ]] && echo "  💭 Step${step}: ${content}"
                ;;
            tool_call)
                tool_count=$((tool_count + 1))
                tn=$(echo "$json" | python3 -c "import sys,json; d=json.load(sys.stdin); print(f\"{d.get('toolName','')}({d.get('toolInput','')[:80]})\")" 2>/dev/null)
                echo "  🔧 Step${step}.${tool_count}: ${tn}"
                ;;
            tool_result)
                tn=$(echo "$json" | python3 -c "import sys,json; d=json.load(sys.stdin); o=d.get('content','')[:150]; print(f\"{d.get('toolName','')} => [{len(d.get('content',''))}chars] {o}\")" 2>/dev/null)
                echo "  📋 ${tn}"
                ;;
            final_answer)
                final=$(echo "$json" | python3 -c "import sys,json; print(json.load(sys.stdin).get('content','')[:200])" 2>/dev/null)
                ;;
            agent_error)
                error=$(echo "$json" | python3 -c "import sys,json; print(json.load(sys.stdin).get('content','')[:200])" 2>/dev/null)
                echo "  ❌ ERROR: ${error}"
                ;;
            agent_finish)
                local elapsed=$(($(date +%s) - start_time))
                echo "  ────────────────────────────────────"
                echo "  📊 步数: ${step} | 工具调用: ${tool_count} | 耗时: ${elapsed}s"
                if [[ -n "$error" ]]; then
                    echo "  ❌ 失败: ${error}"
                elif [[ -n "$final" ]]; then
                    echo "  ✅ 最终回答: ${final}"
                else
                    echo "  ⚠️  无最终回答"
                fi
                ;;
        esac
    done < <(curl -s -N --max-time 180 \
        "${BASE}?message=${encoded}&sessionId=${session}" 2>/dev/null)
}

# ══════════════════════════════════════════════
# 10 条真实用户场景测试用例
# ══════════════════════════════════════════════

run_test 01 "简单问候" \
    "你好，请介绍一下你自己，你能做什么？"

run_test 02 "知识问答" \
    "什么是ReAct模式？它和普通的Function Calling有什么区别？"

run_test 03 "网页搜索" \
    "帮我搜索一下2026年最新的AI新闻"

run_test 04 "文件操作" \
    "创建一个文件 my_notes.txt，内容包含今天的日期2026-07-04和一句名言'Stay hungry, stay foolish'，然后读取这个文件确认内容正确"

run_test 05 "代码生成" \
    "用Java语言写一个线程安全的单例模式实现，要包含双重检查锁"

run_test 06 "多步骤任务" \
    "搜索一下Spring Boot最新稳定版本号，然后把版本信息保存到文件 spring_boot_version.txt 中"

run_test 07 "翻译任务" \
    "把'人工智能正在改变世界'这句话翻译成英文、日文、法文三种语言"

run_test 08 "文本摘要" \
    "请用一段话总结AI Agent的核心能力和工作原理，200字以内"

run_test 09 "创意写作" \
    "写一首关于程序员和AI协作的五言绝句（四句，每句五个字）"

run_test 10 "终端操作" \
    "请用终端命令查看当前系统时间，然后查看Java版本"

echo ""
echo "══════════════════════════════════════════════"
echo "🏁 全部 10 条测试完成"
echo "══════════════════════════════════════════════"