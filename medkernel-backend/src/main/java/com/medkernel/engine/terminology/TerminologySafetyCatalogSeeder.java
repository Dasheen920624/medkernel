package com.medkernel.engine.terminology;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 从版本化医学资源初始化术语安全规则，避免在代码和迁移中散落医学常量。 */
@Component
@Order(120)
public class TerminologySafetyCatalogSeeder implements ApplicationRunner {

    private static final String RESOURCE = "catalog/terminology-safety-rules.json";
    private static final String ACTOR = "catalog-seeder";

    private final HighRiskRuleRepository rules;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TerminologySafetyCatalogSeeder(HighRiskRuleRepository rules) {
        this.rules = rules;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed();
    }

    /** 只补充缺失规则，保留上线后对既有规则的启停和调整。 */
    @Transactional
    public void seed() {
        Instant now = Instant.now();
        for (Definition item : readDefinitions()) {
            if (rules.findByTenantIdAndRuleCode(item.tenantId(), item.ruleCode()).isEmpty()) {
                rules.save(new HighRiskRule(
                    null, item.tenantId(), item.ruleCode(), item.ruleType(), item.category(),
                    item.leftTerms(), item.rightTerms(), item.unitTerms(), item.scaleRatio(),
                    item.evidenceText(), HighRiskRuleStatus.ACTIVE, now, ACTOR, now, ACTOR));
            }
        }
    }

    private List<Definition> readDefinitions() {
        try (var input = new ClassPathResource(RESOURCE).getInputStream()) {
            return objectMapper.readValue(input, new TypeReference<List<Definition>>() {});
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取术语安全规则资源: " + RESOURCE, exception);
        }
    }

    private record Definition(String tenantId, String ruleCode, HighRiskRuleType ruleType,
                              TermCategory category, String leftTerms, String rightTerms,
                              String unitTerms, Double scaleRatio, String evidenceText) {}
}
