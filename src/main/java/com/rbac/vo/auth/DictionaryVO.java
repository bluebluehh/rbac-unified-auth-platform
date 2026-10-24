package com.rbac.vo.auth;

import java.util.List;

/**
 * A40 集中字典（§10.3、§10.5）。
 * 字典是版本化只读配置，不增加数据库表，也不提供写接口。
 * 字典列出的动作不代表当前用户已被授权。
 */
public record DictionaryVO(
        String dictionaryVersion,
        List<Option> userStatuses,
        List<Option> binaryStatuses,
        List<Option> nodeTypes,
        List<Option> auditResults,
        List<Option> eventTypes,
        List<ActionOption> actions,
        Limits limits,
        List<String> reservedRoleKeys,
        List<String> reservedAppKeys) {

    public record Option(String value, String label) {
    }

    public record ActionOption(String code, String label) {
    }

    public record Limits(
            int pageSizeMax,
            int roleSelectionMax,
            int permissionSelectionMax,
            int passwordMin,
            int passwordMax,
            int logRangeDaysMax) {
    }
}
