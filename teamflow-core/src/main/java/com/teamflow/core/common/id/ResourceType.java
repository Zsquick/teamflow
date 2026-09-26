package com.teamflow.core.common.id;

import java.util.Locale;

/**
 * 支持生成可读编号的资源类型。
 */
public enum ResourceType {
    USER("USER", "u"),
    TEAM("TEAM", "tm"),
    TEAM_MEMBER("TEAM_MEMBER", "mb"),
    TEAM_INVITATION("TEAM_INVITATION", "ti"),
    PROJECT("PROJECT", "p"),
    TASK("TASK", "t"),
    TASK_COMMENT("TASK_COMMENT", "c"),
    ATTACHMENT("ATTACHMENT", "a"),
    IMPORT_JOB("IMPORT_JOB", "ij"),
    NOTIFICATION("NOTIFICATION", "n"),
    OPERATION_LOG("OPERATION_LOG", "op"),
    OUTBOX_EVENT("OUTBOX_EVENT", "oe");

    private static final int MINIMUM_DIGITS = 3;

    private final String sequenceName;
    private final String prefix;

    ResourceType(String sequenceName, String prefix) {
        this.sequenceName = sequenceName;
        this.prefix = prefix;
    }

    /**
     * 获取数据库序列表使用的稳定名称。
     *
     * @return 序列名称
     */
    public String sequenceName() {
        return sequenceName;
    }

    /**
     * 获取展示在资源编号开头的短前缀。
     *
     * @return 小写字母前缀
     */
    public String prefix() {
        return prefix;
    }

    /**
     * 将正整数序号格式化成至少三位的可读资源编号。
     *
     * @param sequenceValue 正整数序号
     * @return 例如 {@code u001}、{@code t012} 或 {@code p1234}
     */
    public String format(int sequenceValue) {
        if (sequenceValue < 1) {
            throw new IllegalArgumentException("资源序号必须是正整数");
        }

        return prefix + String.format(
                Locale.ROOT,
                "%0" + MINIMUM_DIGITS + "d",
                sequenceValue
        );
    }
}
