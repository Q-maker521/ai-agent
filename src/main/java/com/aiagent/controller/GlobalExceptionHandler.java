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
