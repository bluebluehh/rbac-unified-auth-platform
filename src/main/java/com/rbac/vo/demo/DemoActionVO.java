package com.rbac.vo.demo;

/** A39 合成提交结果（§10.3），不修改真实业务数据。 */
public record DemoActionVO(boolean accepted, String operation) {
}
