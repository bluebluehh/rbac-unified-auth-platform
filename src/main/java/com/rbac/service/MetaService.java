package com.rbac.service;

import com.rbac.vo.auth.DictionaryVO;

/**
 * 集中字典与枚举读取契约（A40）。只读版本化配置，不提供浏览器写字典接口。
 */
public interface MetaService {

    DictionaryVO getDictionaries();
}
