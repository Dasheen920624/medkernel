package com.medkernel.shared.ids;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * 通用 ULID 生成器。
 *
 * <p>生成 26 位 Crockford Base32 编码，前 10 位为毫秒时间部分，后 16 位为
 * 安全随机部分；用于需要可排序业务 ID 的配置资产、临床对象和投影对象。
 */
public final class Ulid {

    private static final char[] CROCKFORD =
        "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private Ulid() {
    }

    public static String newUlid() {
        long time = System.currentTimeMillis();
        byte[] random = new byte[10];
        RANDOM.nextBytes(random);
        char[] out = new char[26];

        out[0] = CROCKFORD[(int) ((time >>> 45) & 31)];
        out[1] = CROCKFORD[(int) ((time >>> 40) & 31)];
        out[2] = CROCKFORD[(int) ((time >>> 35) & 31)];
        out[3] = CROCKFORD[(int) ((time >>> 30) & 31)];
        out[4] = CROCKFORD[(int) ((time >>> 25) & 31)];
        out[5] = CROCKFORD[(int) ((time >>> 20) & 31)];
        out[6] = CROCKFORD[(int) ((time >>> 15) & 31)];
        out[7] = CROCKFORD[(int) ((time >>> 10) & 31)];
        out[8] = CROCKFORD[(int) ((time >>> 5) & 31)];
        out[9] = CROCKFORD[(int) (time & 31)];

        int buffer = 0;
        int bits = 0;
        int index = 10;
        for (byte value : random) {
            buffer = (buffer << 8) | (value & 0xff);
            bits += 8;
            while (bits >= 5) {
                out[index++] = CROCKFORD[(buffer >>> (bits - 5)) & 31];
                bits -= 5;
                buffer &= bits == 0 ? 0 : (1 << bits) - 1;
            }
        }
        return new String(out).toUpperCase(Locale.ROOT);
    }
}
