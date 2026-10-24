package com.rbac.security;

import com.rbac.common.ErrorResponseFactory;
import com.rbac.common.ResultCode;
import com.rbac.common.RequestIds;
import com.rbac.entity.SysDept;
import com.rbac.entity.SysSession;
import com.rbac.entity.SysUser;
import com.rbac.mapper.DeptMapper;
import com.rbac.mapper.SessionMapper;
import com.rbac.mapper.UserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 认证过滤器（挂在 Spring Security 过滤链，由 SecurityConfig 调用，不重复注册）：
 * PUBLIC 白名单（登录/退出/OPTIONS）直接放行；其余端点校验 Bearer 会话
 * （未撤销、未绝对过期、未空闲过期、账号状态组合、组织链启用）后建立 CurrentUserContext，
 * finally 清理上下文。过滤链失败用 ErrorWriter 输出统一错误结构。
 * 骨架阶段未引入只读事务协调器（§6.3），认证读取为各自独立查询，升级时集中改造。
 */
@Component
public class AuthenticationFilter extends OncePerRequestFilter {

    /** 免认证白名单：登录、退出（可携带任意状态 Token）、预检。 */
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/v1/auth/login", "/api/v1/auth/logout");

    /** 免认证前缀：OpenAPI 契约与 swagger-ui 静态资源（联调/演示用，生产可经配置关闭）。 */
    private static final List<String> PUBLIC_PREFIXES = List.of("/v3/api-docs", "/swagger-ui");

    private final TokenService tokenService;
    private final SessionMapper sessionMapper;
    private final UserMapper userMapper;
    private final DeptMapper deptMapper;
    private final ErrorWriter errorWriter;

    /** nodeKey → 子节点快照缓存代理：骨架直接查全表（111 节点），由 DeptMapper 提供。 */
    public AuthenticationFilter(TokenService tokenService, SessionMapper sessionMapper,
                                UserMapper userMapper, DeptMapper deptMapper, ErrorWriter errorWriter) {
        this.tokenService = tokenService;
        this.sessionMapper = sessionMapper;
        this.userMapper = userMapper;
        this.deptMapper = deptMapper;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || isPublic(path)) {
            chain.doFilter(request, response);
            return;
        }

        try {
            CurrentUser user = authenticate(request);
            if (user == null) {
                writeError(request, response, 401, ResultCode.SESSION_INVALID, "会话无效或已过期");
                return;
            }
            CurrentUserContext.set(user);
            chain.doFilter(request, response);
        } catch (Exception e) {
            // 认证存储故障默认拒绝，不降级放行（§8.4）
            writeError(request, response, 503, ResultCode.AUTH_UNAVAILABLE, "鉴权服务暂时不可用，默认拒绝");
        } finally {
            CurrentUserContext.clear();
        }
    }

    /** 白名单裁决：精确路径 + 文档前缀。 */
    private static boolean isPublic(String path) {
        if (PUBLIC_PATHS.contains(path)) {
            return true;
        }
        for (String prefix : PUBLIC_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** Bearer 会话校验；失败返回 null。成功后独立触碰 last_seen_at（条件更新，不复活撤销/过期会话）。 */
    private CurrentUser authenticate(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String token = authorization.substring(7).trim();
        if (token.isEmpty()) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        SysSession session = sessionMapper.selectByTokenHash(tokenService.digest(token));
        if (session == null || session.getRevokedAt() != null
                || !session.getExpiresAt().isAfter(now)
                || !session.getLastSeenAt().isAfter(now.minusMinutes(idleMinutes()))) {
            return null;
        }
        SysUser user = userMapper.selectById(session.getUserId());
        if (user == null || user.getPasswordHash() == null) {
            return null;
        }
        boolean restricted = session.getRestricted() != null && session.getRestricted() == 1;
        if (restricted) {
            // 受限会话：必须 PENDING_ACTIVATION 且未完成首次改密（§5.2）
            if (!"PENDING_ACTIVATION".equals(user.getStatus())
                    || user.getMustChangePassword() == null || user.getMustChangePassword() != 1) {
                return null;
            }
        } else {
            // 普通会话：必须 ENABLED 且已完成首次改密（§5.2）
            if (!"ENABLED".equals(user.getStatus())
                    || user.getMustChangePassword() == null || user.getMustChangePassword() != 0) {
                return null;
            }
        }
        if (!orgChainEnabled(user)) {
            return null;
        }
        // 会话续活：仅未撤销、未过期、未空闲过期时触碰（§6.5）
        sessionMapper.touch(session.getId(), now, now.minusMinutes(idleMinutes()));
        return new CurrentUser(user.getId(), user.getUsername(), session.getId(), restricted);
    }

    /** 组织链启用检查：companyId 与 deptId 节点及其全部祖先 status=1（§3.2）。 */
    private boolean orgChainEnabled(SysUser user) {
        List<SysDept> all = deptMapper.selectAll();
        Map<Long, SysDept> byId = new HashMap<>();
        for (SysDept d : all) {
            byId.put(d.getId(), d);
        }
        Set<Long> visited = new HashSet<>();
        return chainEnabled(user.getCompanyId(), byId, visited) && chainEnabled(user.getDeptId(), byId, visited);
    }

    private boolean chainEnabled(Long nodeId, Map<Long, SysDept> byId, Set<Long> visited) {
        Long current = nodeId;
        while (current != null && visited.add(current)) {
            SysDept dept = byId.get(current);
            if (dept == null || dept.getStatus() == null || dept.getStatus() != 1) {
                return false;
            }
            current = dept.getParentId();
        }
        return true;
    }

    private long idleMinutes() {
        return 30; // 骨架固定；后续接 RbacSecurityProperties
    }

    private void writeError(HttpServletRequest request, HttpServletResponse response,
                            int status, String code, String message) throws IOException {
        errorWriter.write(request, response, status,
                ErrorResponseFactory.buildBody(code, message, null));
    }
}
