package com.medkernel.engine.llm.eval;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动期把 OPT-04 已审红线库投影为医学回归基线；无 ACTIVE 红线时保持空集并由 readiness 如实阻断。
 */
@Component
public class RegressionBaselineSeeder implements ApplicationRunner {

    private final RegressionBaselineProjectionService projectionService;

    public RegressionBaselineSeeder(RegressionBaselineProjectionService projectionService) {
        this.projectionService = projectionService;
    }

    @Override
    public void run(ApplicationArguments args) {
        projectionService.projectAllActiveTenants();
    }
}
