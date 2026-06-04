package com.medkernel.engine.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 字段目录合并逻辑单测（P2/P5）：系统字段优先，租户字段去重/过滤后补充。
 */
class ContextFieldCatalogServiceMergeTest {

    private final List<ContextFieldDescriptor> systemFields =
        new ContextFieldCatalog().query(null, null);

    private ContextFieldCatalogEntry tenant(String fieldPath, String resourceType, String category) {
        Instant now = Instant.now();
        return new ContextFieldCatalogEntry(
            1L, "f1", "t", category, "自定义分组", resourceType, fieldPath, "院内自定义", "string",
            null, null, "扩展", "ACTIVE", now, "u", now, "u", "trace");
    }

    @Test
    void tenantCustomFieldAppended() {
        var merged = ContextFieldCatalogService.merge(
            systemFields, List.of(tenant("medications[].customFlag", "Medication", "医嘱信息")), null, null);
        assertThat(merged).hasSize(systemFields.size() + 1);
        assertThat(merged).anyMatch(f -> f.fieldPath().equals("medications[].customFlag"));
    }

    @Test
    void tenantFieldWithSamePathAsSystemIsNotDuplicated() {
        // conditions[].code 已是系统字段
        var merged = ContextFieldCatalogService.merge(
            systemFields, List.of(tenant("conditions[].code", "Condition", "诊断信息")), null, null);
        assertThat(merged).hasSize(systemFields.size());
    }

    @Test
    void buildEntryValidatesAndMaps() {
        var req = new ContextFieldCatalogUpsertRequest(
            "医嘱信息", "用药医嘱", "Medication", " medications[].customFlag ", "院内自定义",
            "string", "", "", "扩展说明");
        var entry = ContextFieldCatalogService.buildEntry(req, "t-1", "u-1", "trace-1");
        assertThat(entry.tenantId()).isEqualTo("t-1");
        assertThat(entry.fieldPath()).isEqualTo("medications[].customFlag"); // 去空格
        assertThat(entry.category()).isEqualTo("医嘱信息");
        assertThat(entry.groupName()).isEqualTo("用药医嘱");
        assertThat(entry.status()).isEqualTo("ACTIVE");
        assertThat(entry.unit()).isNull(); // 空白归一为 null
    }

    @Test
    void buildEntryRejectsInvalidDataType() {
        var req = new ContextFieldCatalogUpsertRequest(
            "医嘱信息", "用药医嘱", "Medication", "x.y", "名", "weird", null, null, null);
        org.assertj.core.api.Assertions
            .assertThatThrownBy(() -> ContextFieldCatalogService.buildEntry(req, "t", "u", "tr"))
            .hasMessageContaining("数据类型非法");
    }

    @Test
    void tenantFieldFilteredByResourceTypeAndKeyword() {
        var entries = List.of(tenant("medications[].customFlag", "Medication", "医嘱信息"));
        assertThat(ContextFieldCatalogService.merge(systemFields, entries, "Observation", null))
            .hasSize(systemFields.size()); // 资源类型不匹配，不补充
        assertThat(ContextFieldCatalogService.merge(systemFields, entries, null, "自定义"))
            .anyMatch(f -> f.fieldPath().equals("medications[].customFlag")); // 关键词命中分组
    }
}
