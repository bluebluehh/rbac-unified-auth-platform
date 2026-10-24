package com.rbac.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rbac.common.ErrorResponseFactory;
import com.rbac.common.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

import org.springframework.stereotype.Component;

/**
 * 过滤链错误输出器：AuthenticationFilter 认证失败时向客户端写统一错误 JSON，
 * 与 GlobalExceptionHandler（MVC 路径）共享 ErrorResponseFactory 的错误结构，不重复写响应。
 */
@Component
public class ErrorWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 以统一错误结构写出 JSON 响应；响应已提交时静默跳过。 */
    public void write(HttpServletRequest request, HttpServletResponse response,
                      int status, ErrorResponseFactory.ErrorBody body) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        String requestId = request.getAttribute(RequestIds.ATTRIBUTE) instanceof String s ? s : null;
        if (requestId != null && body.requestId() == null) {
            response.setHeader(RequestIds.HEADER, requestId);
        }
        MAPPER.writeValue(response.getWriter(), body);
    }
}
