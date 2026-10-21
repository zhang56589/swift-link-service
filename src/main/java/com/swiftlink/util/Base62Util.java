package com.swiftlink.util;

import java.util.HashMap;
import java.util.Map;

/**
 * 62 进制编解码：雪花 ID -> 短码，缩短长度并隐藏自增规律。
 */
public final class Base62Util {

    private static final char[] CHARS =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

    private static final int RADIX = CHARS.length;

    private static final Map<Character, Integer> INDEX = new HashMap<>(RADIX);

    static {
        for (int i = 0; i < CHARS.length; i++) {
            INDEX.put(CHARS[i], i);
        }
    }

    private Base62Util() {
    }

    public static String encode(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Base62 不支持负数");
        }
        if (value == 0) {
            return String.valueOf(CHARS[0]);
        }
        StringBuilder sb = new StringBuilder(11);
        long current = value;
        while (current > 0) {
            sb.append(CHARS[(int) (current % RADIX)]);
            current /= RADIX;
        }
        return sb.reverse().toString();
    }

    public static long decode(String code) {
        if (code == null || code.isEmpty()) {
            throw new IllegalArgumentException("短码不能为空");
        }
        long result = 0;
        for (char c : code.toCharArray()) {
            Integer index = INDEX.get(c);
            if (index == null) {
                throw new IllegalArgumentException("非法 Base62 字符: " + c);
            }
            result = Math.addExact(Math.multiplyExact(result, RADIX), index);
        }
        return result;
    }
}
