package com.rbac.controller;

import com.rbac.annotation.EndpointAccess;
import com.rbac.common.Result;
import com.rbac.security.EndpointAccessType;
import com.rbac.service.MetaService;
import com.rbac.vo.auth.DictionaryVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 集中字典与枚举接口（A40）。有效普通会话可读，受限会话 403；只读版本化配置，不提供写字典接口。
 */
@RestController
@RequestMapping("/api/v1/meta")
public class MetaController {

    private final MetaService metaService;

    public MetaController(MetaService metaService) {
        this.metaService = metaService;
    }

    /** A40 字典：userStatuses/binaryStatuses/nodeTypes/auditResults/eventTypes/actions/limits/保留键。 */
    @GetMapping("/dictionaries")
    @EndpointAccess(EndpointAccessType.AUTHENTICATED)
    public Result<DictionaryVO> dictionaries() {
        return Result.ok(metaService.getDictionaries());
    }
}
