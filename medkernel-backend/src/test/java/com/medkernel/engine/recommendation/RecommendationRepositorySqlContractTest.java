package com.medkernel.engine.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * 推荐运行库查询 SQL 契约：当前运行范围保障 PostgreSQL + Oracle。
 */
class RecommendationRepositorySqlContractTest {

    @Test
    void paginationQueriesUsePostgresAndOracleCompatibleOffsetFetch() throws IOException {
        assertPaginationSql("RecommendationCardRepository.java");
        assertPaginationSql("RecommendationFatigueSignalRepository.java");
    }

    private static void assertPaginationSql(String fileName) throws IOException {
        String source = Files.readString(Path.of(
            "src/main/java/com/medkernel/engine/recommendation", fileName));

        assertThat(source)
            .doesNotContain("LIMIT :limit OFFSET :offset")
            .contains("OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY");
    }
}
