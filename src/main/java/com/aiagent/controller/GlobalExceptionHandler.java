package com.aiagent.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 全局异常处理器 — 统一 API 错误响应格式。
 *
 * <h3>设计要点</h3>
 * <ul>
 *   <li><b>Spring MVC 框架异常</b>（请求解析阶段抛出）→ 4xx（客户端错误）</li>
 *   <li><b>业务异常</b>（Controller/Service 主动抛出）→ 4xx 或 409</li>
 *   <li><b>未知异常</b>（真正的服务端 bug）→ 500，但不暴露内部细节</li>
 * </ul>
 *
 * <p>关键教训：如果没有中间的 Spring MVC 异常处理器，框架异常会直接落入
 * {@code Exception} 泛化处理器，导致"缺参数"这类明显的客户端错误也被返回 500。
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ========== Spring MVC 框架异常（请求解析阶段）==========

    /**
     * 缺少必填的 @RequestParam 参数。
     * 例如：{@code POST /ai/agent/cancel} 不带 {@code sessionId}。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("Missing required parameter: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "error", "缺少必填参数: " + e.getParameterName(),
                        "status", 400));
    }

    /**
     * 缺少 @RequestBody 或请求体格式错误（JSON 解析失败）。
     * 例如：{@code PATCH} 不带 body。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleMissingBody(HttpMessageNotReadableException e) {
        log.warn("Missing or malformed request body: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "error", "请求体缺失或格式错误",
                        "status", 400));
    }

    /**
     * 参数类型不匹配。
     * 例如：{@code ?count=abc} 但参数声明为 {@code int count}。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("Type mismatch for parameter '{}': {}", e.getName(), e.getMessage());
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "error", String.format("参数 '%s' 类型错误: 期望 %s",
                                e.getName(),
                                e.getRequiredType() != null
                                        ? e.getRequiredType().getSimpleName() : "未知"),
                        "status", 400));
    }

    /**
     * HTTP 方法不支持。
     * 例如：对 GET-only 的端点使用 POST。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException e) {
        String supported = e.getSupportedMethods() != null
                ? String.join(", ", e.getSupportedMethods())
                : "未知";
        log.warn("Method not supported: {} (supported: {})", e.getMethod(), supported);
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "error", "不支持的 HTTP 方法: " + e.getMethod()
                                + "，请使用: " + supported,
                        "status", 405));
    }

    // ========== 业务异常 ==========

    /**
     * 业务参数校验失败。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
        log.warn("Bad request: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("error", e.getMessage(), "status", 400));
    }

    /**
     * 业务状态冲突。
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(IllegalStateException e) {
        log.warn("Conflict: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("error", e.getMessage(), "status", 409));
    }

    // ========== 文件上传异常 ==========

    /**
     * 上传文件超过大小限制（默认 50MB）。
     */
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(
            org.springframework.web.multipart.MaxUploadSizeExceededException e) {
        log.warn("Upload size exceeded: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "error", "文件大小超过限制 (最大 50MB)",
                        "status", 413));
    }

    /**
     * 通用文件上传失败（格式错误、请求不完整等）。
     */
    @ExceptionHandler(org.springframework.web.multipart.MultipartException.class)
    public ResponseEntity<Map<String, Object>> handleMultipart(
            org.springframework.web.multipart.MultipartException e) {
        log.warn("Multipart upload failed: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "error", "文件上传失败: " + e.getMessage(),
                        "status", 400));
    }

    // ========== AI Provider 调用异常 ==========

    /**
     * LLM API 调用失败（非瞬时性）：API Key 无效、模型不存在、参数错误等。
     * <p>
     * 返回 502 Bad Gateway + 原始错误信息，让用户知道具体原因（如模型名拼错、
     * API Key 过期、额度不足等），而不是返回模糊的 "Internal server error"。
     * 适用于所有 Provider：DashScope / OpenAI / Anthropic / OpenAI 兼容接口。
     */
    @ExceptionHandler(org.springframework.ai.retry.NonTransientAiException.class)
    public ResponseEntity<Map<String, Object>> handleNonTransientAi(
            org.springframework.ai.retry.NonTransientAiException e) {
        // 提取最内层 root cause 的消息（通常是 Provider 返回的原始 JSON 错误）
        String detail = extractRootMessage(e);
        log.error("AI provider non-transient error: {}", detail);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "AI 模型调用失败: " + detail);
        body.put("status", 502);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    /**
     * LLM API 调用失败（瞬时性）：网络超时、服务暂时不可用、限流等。
     * <p>
     * 返回 503 Service Unavailable，提示用户稍后重试。
     */
    @ExceptionHandler(org.springframework.ai.retry.TransientAiException.class)
    public ResponseEntity<Map<String, Object>> handleTransientAi(
            org.springframework.ai.retry.TransientAiException e) {
        String detail = extractRootMessage(e);
        log.error("AI provider transient error: {}", detail);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "AI 服务暂时不可用: " + detail);
        body.put("status", 503);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    /**
     * 从异常链中提取根因消息。
     * <p>
     * 遍历 cause chain 找到最底层异常（通常是 Provider 返回的原始错误），
     * 取其 message。如果异常链为空，回退到当前异常的 message。
     */
    private String extractRootMessage(Exception e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String msg = root.getMessage();
        return msg != null && !msg.isBlank() ? msg : e.getMessage();
    }

    // ========== 兜底 ==========

    /**
     * 真正的未知异常（服务端 bug）。
     * <p>
     * 不返回 {@code e.getMessage()} 给客户端——防止泄露堆栈信息等内部细节。
     * 完整堆栈通过日志输出。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception e) {
        log.error("Unhandled exception", e);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Internal server error");
        body.put("status", 500);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
