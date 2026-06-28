package com.medkernel.engine.llm;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 从版本化资源初始化模型能力和模型赋能覆盖目录，不覆盖运行期人工调整。 */
@Component
@Order(100)
public class ModelCatalogSeeder implements ApplicationRunner {

    private static final String RESOURCE = "catalog/model-catalog.json";
    private static final String ACTOR = "catalog-seeder";

    private final ModelCapabilityDefinitionRepository capabilities;
    private final ModelEnhancementMatrixRepository matrix;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ModelCatalogSeeder(ModelCapabilityDefinitionRepository capabilities,
                              ModelEnhancementMatrixRepository matrix) {
        this.capabilities = capabilities;
        this.matrix = matrix;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed();
    }

    /** 对空目录补齐固定项；已有项视为运行期权威值。 */
    @Transactional
    public void seed() {
        Catalog catalog = readCatalog();
        Instant now = Instant.now();
        for (Capability item : catalog.capabilities()) {
            if (!capabilities.existsById(item.capabilityCode())) {
                capabilities.save(new ModelCapabilityDefinition(
                    item.capabilityCode(), item.displayName(), item.description(), item.category(),
                    "Y", item.sortOrder(), now, ACTOR, now, ACTOR, true));
            }
        }
        for (Enhancement item : catalog.enhancementMatrix()) {
            if (matrix.findByBusinessPoint(item.businessPoint()).isEmpty()) {
                matrix.save(new ModelEnhancementMatrix(
                    null, item.businessPoint(), item.businessName(), item.capabilityCode(), item.b0Path(),
                    item.accessStatus(), "Y", item.sortOrder(), now, ACTOR, now, ACTOR));
            }
        }
    }

    private Catalog readCatalog() {
        try (var input = new ClassPathResource(RESOURCE).getInputStream()) {
            return objectMapper.readValue(input, Catalog.class);
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取模型能力目录资源: " + RESOURCE, exception);
        }
    }

    private record Catalog(List<Capability> capabilities, List<Enhancement> enhancementMatrix) {}

    private record Capability(String capabilityCode, String displayName, String description,
                              String category, int sortOrder) {}

    private record Enhancement(String businessPoint, String businessName, String capabilityCode,
                               String b0Path, String accessStatus, int sortOrder) {}
}
