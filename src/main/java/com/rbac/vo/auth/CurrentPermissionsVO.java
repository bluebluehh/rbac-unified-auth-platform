package com.rbac.vo.auth;

import java.util.List;

/**
 * A06 当前用户权限响应（§10.3）。
 * roleIds 为当前启用的直接角色；policyVersion 取自 sys_security_state。
 */
public record CurrentPermissionsVO(List<String> permissionKeys, List<String> roleIds, String policyVersion) {
}
