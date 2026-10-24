package com.rbac.controller;

import com.rbac.annotation.EndpointAccess;
import com.rbac.annotation.OperationLog;
import com.rbac.annotation.RequiresPermission;
import com.rbac.common.Result;
import com.rbac.common.constants.PermissionKeyConstants;
import com.rbac.common.enums.EventType;
import com.rbac.dto.common.BinaryStatusDTO;
import com.rbac.dto.dept.DeptCreateDTO;
import com.rbac.dto.dept.DeptPatchDTO;
import com.rbac.security.EndpointAccessType;
import com.rbac.service.DeptService;
import com.rbac.util.VersionPreconditionParser;
import com.rbac.vo.dept.DeptStatusVO;
import com.rbac.vo.dept.DeptTreeVO;
import com.rbac.vo.dept.DeptVO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 部门组织接口（A15-A19）。树形组装由 Service 完成，变更接口要求 If-Match，响应附带新版本 ETag。
 */
@RestController
@RequestMapping("/api/v1/depts")
public class DeptController {

    private final DeptService deptService;

    public DeptController(DeptService deptService) {
        this.deptService = deptService;
    }

    /** A15 组织树：rootId 可选默认 ROOT；includeDisabled 默认 false。 */
    @GetMapping("/tree")
    @EndpointAccess(EndpointAccessType.PERMISSION)
    @RequiresPermission(PermissionKeyConstants.DEPT_VIEW)
    public Result<List<DeptTreeVO>> tree(
            @RequestParam(value = "rootId", required = false) String rootId,
            @RequestParam(value = "includeDisabled", required = false, defaultValue = "false") boolean includeDisabled) {
        return Result.ok(deptService.tree(rootId, includeDisabled));
    }

    /** A16 部门详情，附带版本 ETag。 */
    @GetMapping("/{id}")
    @EndpointAccess(EndpointAccessType.PERMISSION)
    @RequiresPermission(PermissionKeyConstants.DEPT_VIEW)
    public ResponseEntity<Result<DeptVO>> get(@PathVariable String id) {
        DeptVO vo = deptService.get(id);
        return ResponseEntity.ok().eTag(etagOf(vo.getVersion())).body(Result.ok(vo));
    }

    /** A17 创建节点：父节点需启用、节点键唯一、不能创建 ROOT。 */
    @PostMapping
    @EndpointAccess(EndpointAccessType.PERMISSION)
    @RequiresPermission(PermissionKeyConstants.DEPT_CREATE)
    @OperationLog(type = EventType.DEPT_CREATE, target = "DEPT")
    public ResponseEntity<Result<DeptVO>> create(@RequestBody DeptCreateDTO dto) {
        DeptVO vo = deptService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).eTag(etagOf(vo.getVersion())).body(Result.ok(vo));
    }

    /** A18 部分更新：不改变父节点/nodeKey/nodeType。 */
    @PatchMapping("/{id}")
    @EndpointAccess(EndpointAccessType.PERMISSION)
    @RequiresPermission(PermissionKeyConstants.DEPT_EDIT)
    @OperationLog(type = EventType.DEPT_UPDATE, target = "DEPT")
    public ResponseEntity<Result<DeptVO>> update(@PathVariable String id,
                                                 @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                                 @RequestBody DeptPatchDTO dto) {
        DeptVO vo = deptService.update(id, VersionPreconditionParser.parseRequired(ifMatch), dto);
        return ResponseEntity.ok().eTag(etagOf(vo.getVersion())).body(Result.ok(vo));
    }

    /** A19 启停：返回受影响子树不同用户数与策略版本；停用撤销子树账号会话并检查管理员（无 ETag）。 */
    @PatchMapping("/{id}/status")
    @EndpointAccess(EndpointAccessType.PERMISSION)
    @RequiresPermission(PermissionKeyConstants.DEPT_STATUS)
    @OperationLog(type = EventType.DEPT_STATUS, target = "DEPT")
    public Result<DeptStatusVO> changeStatus(@PathVariable String id,
                                             @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                             @RequestBody BinaryStatusDTO dto) {
        return Result.ok(deptService.changeStatus(id, VersionPreconditionParser.parseRequired(ifMatch), dto));
    }

    /** 版本字符串 → 带引号 ETag；解析失败回退 "0"（防御，正常由 Service 保证）。 */
    private static String etagOf(String version) {
        try {
            return VersionPreconditionParser.toETag(Long.parseLong(version));
        } catch (NumberFormatException e) {
            return "\"0\"";
        }
    }
}
