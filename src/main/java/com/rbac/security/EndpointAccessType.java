package com.rbac.security;

/**
 * 端点访问分类枚举（设计文档 §10.1 访问分类列）。
 */
public enum EndpointAccessType {
    /** 公开端点：登录、退出。 */
    PUBLIC,
    /** 有效普通会话即可（A40 字典）。 */
    AUTHENTICATED,
    /** 本人端点：身份取自 CurrentUserContext，不信任客户端传入 userId（A03-A06）。 */
    SELF,
    /** 需要固定权限 key（PermissionAspect 校验）。 */
    PERMISSION
}
