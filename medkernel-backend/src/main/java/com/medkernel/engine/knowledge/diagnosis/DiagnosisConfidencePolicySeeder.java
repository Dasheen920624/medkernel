package com.medkernel.engine.knowledge.diagnosis;

import java.io.IOException;
import java.time.Instant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 初始化可配置诊断置信策略，保证空库具备诚实可运行的默认策略。 */
@Component
@Order(110)
public class DiagnosisConfidencePolicySeeder implements ApplicationRunner {

    private static final String RESOURCE = "catalog/diagnosis-confidence-policy.json";
    private static final String ACTOR = "catalog-seeder";

    private final DiagnosisConfidencePolicyRepository policies;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DiagnosisConfidencePolicySeeder(DiagnosisConfidencePolicyRepository policies) {
        this.policies = policies;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed();
    }

    /** 仅在默认策略不存在时创建，不覆盖上线后的配置调整。 */
    @Transactional
    public void seed() {
        Definition definition = readDefinition();
        if (policies.findByTenantIdAndScopeKey(definition.tenantId(), definition.scopeKey()).isPresent()) {
            return;
        }
        Instant now = Instant.now();
        policies.save(new DiagnosisConfidencePolicy(
            null, definition.tenantId(), definition.scopeKey(), definition.strongMinMajor(),
            definition.requireAllRequired(), definition.moderateMinHits(),
            now, ACTOR, now, ACTOR, "catalog-seed"));
    }

    private Definition readDefinition() {
        try (var input = new ClassPathResource(RESOURCE).getInputStream()) {
            return objectMapper.readValue(input, Definition.class);
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取诊断置信策略资源: " + RESOURCE, exception);
        }
    }

    private record Definition(String tenantId, String scopeKey, int strongMinMajor,
                              boolean requireAllRequired, int moderateMinHits) {}
}
