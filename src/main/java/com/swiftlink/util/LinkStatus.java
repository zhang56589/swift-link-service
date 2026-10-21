package com.swiftlink.util;

/**
 * 短链接状态常量，业务判断禁止直接写魔法值。
 */
public final class LinkStatus {

    public static final int DISABLED = 0;

    public static final int ACTIVE = 1;

    private LinkStatus() {
    }

    public static boolean isValid(Integer status) {
        return status != null && (status == DISABLED || status == ACTIVE);
    }

    public static String desc(Integer status) {
        if (status == null) {
            return "未知";
        }
        return status == ACTIVE ? "启用" : "停用";
    }
}
