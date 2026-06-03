package com.medkernel.engine.integration.domain;

import org.springframework.data.relational.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

import com.medkernel.shared.ids.Ulid;

/**
 * 保存数据质量报告前补齐 ULID 形态业务主键。
 */
@Component
class DataQualityReportIdCallback implements BeforeConvertCallback<DataQualityReport> {

    @Override
    public DataQualityReport onBeforeConvert(DataQualityReport aggregate) {
        if (aggregate.reportId() != null && !aggregate.reportId().isBlank()) {
            return aggregate;
        }
        return new DataQualityReport(
            "dqr-" + Ulid.newUlid(),
            aggregate.tenantId(),
            aggregate.generatedAt(),
            aggregate.requiredFieldTotal(),
            aggregate.requiredFieldPresent(),
            aggregate.requiredFieldRate(),
            aggregate.adapterTotal(),
            aggregate.mappedAdapterCount(),
            aggregate.mappingRate(),
            aggregate.timelyAdapterCount(),
            aggregate.timelinessRate(),
            aggregate.notConnectedCount(),
            aggregate.misconfiguredCount(),
            aggregate.gapSummary(),
            aggregate.createdAt(),
            aggregate.createdBy(),
            aggregate.traceId()
        );
    }
}
