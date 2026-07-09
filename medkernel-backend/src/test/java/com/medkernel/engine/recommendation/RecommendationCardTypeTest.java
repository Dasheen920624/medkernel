package com.medkernel.engine.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/** 推荐卡类型枚举：诊断辅助卡（运行时鉴别诊断 Plan B）类型存在。 */
class RecommendationCardTypeTest {

    @Test
    void diagnosisTypeExists() {
        assertThat(RecommendationCardType.valueOf("DIAGNOSIS")).isNotNull();
    }

    @Test
    void databaseCheckConstraintAllowsEveryRecommendationCardType() throws Exception {
        Set<String> enumValues = Arrays.stream(RecommendationCardType.values())
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());
        for (String dialect : new String[] {"h2", "postgres", "oracle", "kingbase", "dm"}) {
            String sql = Files.readString(Path.of(
                "src/main/resources/db/migration",
                dialect,
                "V1__baseline.sql"));
            String constraint = sql.lines()
                .filter(line -> line.contains("ck_rec_card_type"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(dialect + " 缺少推荐卡类型约束"));
            assertThat(constraint)
                .as(dialect + " 推荐卡类型约束必须覆盖 Java 枚举")
                .contains(enumValues.toArray(String[]::new));
        }
    }
}
