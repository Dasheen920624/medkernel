package com.medkernel.engine.org;

import java.security.SecureRandom;
import java.time.Instant;

import org.springframework.data.relational.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

/**
 * 组织节点保存前补齐字符串主键。
 */
@Component
public class OrgUnitIdGenerator implements BeforeConvertCallback<OrgUnit> {

    private static final char[] CROCKFORD_BASE32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 在组织单元写入数据库前补齐空缺主键。
     *
     * @param unit 待保存的组织单元
     * @return 已带主键的组织单元
     */
    @Override
    public OrgUnit onBeforeConvert(OrgUnit unit) {
        if (unit == null || hasText(unit.id())) {
            return unit;
        }
        return new OrgUnit(
            nextOrgUnitId(),
            unit.parentId(),
            unit.tenantId(),
            unit.orgPath(),
            unit.level(),
            unit.code(),
            unit.name(),
            unit.namePinyin(),
            unit.specialtyId(),
            unit.status(),
            unit.createdAt(),
            unit.createdBy(),
            unit.updatedAt(),
            unit.updatedBy()
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String nextOrgUnitId() {
        char[] out = new char[26];
        long timestamp = Instant.now().toEpochMilli();
        for (int i = 9; i >= 0; i--) {
            out[i] = CROCKFORD_BASE32[(int) (timestamp & 31)];
            timestamp >>>= 5;
        }

        byte[] random = new byte[10];
        RANDOM.nextBytes(random);
        int buffer = 0;
        int bits = 0;
        int index = 10;
        for (byte b : random) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out[index++] = CROCKFORD_BASE32[(buffer >>> (bits - 5)) & 31];
                bits -= 5;
            }
        }
        return new String(out);
    }
}
