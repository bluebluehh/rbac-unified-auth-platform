package com.rbac.exception;

import com.rbac.common.BusinessException;
import com.rbac.common.ErrorResponseFactory;
import com.rbac.common.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * MVC 全局异常处理器：统一输出 {code, message, fieldErrors, requestId} 错误结构。
 * 业务异常按异常携带的 HTTP 状态输出；框架/系统异常收敛为 400/404/409/500 语义码。
 * 500 只记录服务端日志，不向客户端泄露堆栈。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：状态与错误码由异常自身携带；仅 400 携带字段错误明细。 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Object> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(ex.getHttpStatus())
                .body(ErrorResponseFactory.buildBody(ex.getCode(), ex.getMessage(),
                        ex.getHttpStatus() == 400 ? ex.getFieldErrors() : null));
    }

    /** 请求正文缺失或 JSON 无法解析。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> handleNotReadable(HttpMessageNotReadableException ex) {
        return badRequest(ResultCode.INVALID_ARGUMENT, "请求正文无法解析");
    }

    /** @Valid 校验失败。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidation(MethodArgumentNotValidException ex) {
        return badRequest(ResultCode.INVALID_ARGUMENT, "请求参数不合法");
    }

    /** 路径/查询参数类型不匹配。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Object> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return badRequest(ResultCode.INVALID_ARGUMENT, "请求参数类型不合法");
    }

    /** 静态资源/路径不存在。 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Object> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(404)
                .body(ErrorResponseFactory.buildBody(ResultCode.NOT_FOUND, "目标资源不存在", null));
    }

    /** HTTP 方法不支持。 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Object> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return badRequest(ResultCode.INVALID_ARGUMENT, "请求方法不支持");
    }

    /** 数据库死锁/锁等待超时：可安全重试的并发冲突。 */
    @ExceptionHandler({DeadlockLoserDataAccessException.class, CannotAcquireLockException.class})
    public ResponseEntity<Object> handleLockConflict(Exception ex) {
        return ResponseEntity.status(409)
                .body(ErrorResponseFactory.buildBody(ResultCode.TRANSACTION_RETRY, "并发冲突，请查询后重试", null));
    }

    /** 兜底异常：仅服务端记录完整堆栈，客户端只收到 INTERNAL_ERROR。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex) {
        log.error("系统内部错误", ex);
        return ResponseEntity.status(500)
                .body(ErrorResponseFactory.buildBody(ResultCode.INTERNAL_ERROR, "系统内部错误", null));
    }

    /** 400 统一构造。 */
    private ResponseEntity<Object> badRequest(String code, String message) {
        return ResponseEntity.badRequest().body(ErrorResponseFactory.buildBody(code, message, null));
    }
}
