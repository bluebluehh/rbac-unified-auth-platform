package com.rbac.security;

import com.rbac.common.RequestIds;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 请求追踪 ID 过滤器：优先采用客户端 X-Request-Id（合法时），否则服务端生成；
 * 写入请求属性并在响应头回传，供统一响应体 requestId 字段使用。
 */
public class RequestIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String incoming = request.getHeader(RequestIds.HEADER);
        String requestId = RequestIds.isValid(incoming) ? incoming : RequestIds.generate();
        request.setAttribute(RequestIds.ATTRIBUTE, requestId);
        response.setHeader(RequestIds.HEADER, requestId);
        chain.doFilter(request, response);
    }
}
