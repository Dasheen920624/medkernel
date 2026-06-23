package com.medkernel.engine.terminology;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;
import org.springframework.context.annotation.Import;

@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:high-risk-rule-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
@Import(TerminologySafetyCatalogSeeder.class)
class HighRiskRuleRepositoryTest {

    @Autowired
    HighRiskRuleRepository repository;

    @Autowired
    TerminologySafetyCatalogSeeder seeder;

    @BeforeEach
    void seedCatalog() {
        seeder.seed();
    }

    @Test
    void systemPotassiumSodiumRuleAppliesToLabTerminology() {
        List<HighRiskRule> rules = repository.findActiveByTenantIdAndCategory("tenant-A", TermCategory.LAB);

        assertThat(rules)
            .filteredOn(rule -> "MED-C1-K-NA".equals(rule.ruleCode()))
            .singleElement()
            .satisfies(rule -> {
                assertThat(rule.category()).isNull();
                assertThat(rule.evidenceText()).contains("钾/钠");
            });
    }

    @Test
    void repeatedSeedKeepsExactlyOneRulePerCode() {
        seeder.seed();

        assertThat(repository.findAll())
            .extracting(HighRiskRule::ruleCode)
            .doesNotHaveDuplicates()
            .hasSize(5);
    }
}
