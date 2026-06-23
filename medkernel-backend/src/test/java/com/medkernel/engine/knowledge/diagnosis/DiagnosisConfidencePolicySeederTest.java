package com.medkernel.engine.knowledge.diagnosis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:diagnosis-policy-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
@Import(DiagnosisConfidencePolicySeeder.class)
class DiagnosisConfidencePolicySeederTest {

    @Autowired
    DiagnosisConfidencePolicyRepository repository;

    @Autowired
    DiagnosisConfidencePolicySeeder seeder;

    @BeforeEach
    void seedCatalog() {
        seeder.seed();
    }

    @Test
    void emptyDatabaseReceivesDefaultPolicy() {
        assertThat(repository.findByTenantIdAndScopeKey("t-1", "DEFAULT"))
            .get()
            .satisfies(policy -> {
                assertThat(policy.strongMinMajor()).isEqualTo(2);
                assertThat(policy.requireAllRequired()).isTrue();
                assertThat(policy.moderateMinHits()).isEqualTo(1);
            });
    }

    @Test
    void repeatedSeedIsIdempotent() {
        seeder.seed();

        assertThat(repository.findAll()).hasSize(1);
    }
}
