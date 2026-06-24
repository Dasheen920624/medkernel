package com.medkernel.engine.terminology;

import java.util.List;

import org.springframework.stereotype.Component;

import com.medkernel.engine.integration.inbound.InboundTerminologyMapping;
import com.medkernel.engine.integration.inbound.InboundTerminologyMappingPort;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 从指定机构生效版本锁定的术语资产版本中解析第三方入站编码。
 */
@Component
public class InboundTerminologyMappingAdapter implements InboundTerminologyMappingPort {

    private final EffectiveTermMappingResolver mappings;

    public InboundTerminologyMappingAdapter(EffectiveTermMappingResolver mappings) {
        this.mappings = mappings;
    }

    @Override
    public InboundTerminologyMapping resolve(
            String tenantId,
            String runtimeReleaseId,
            String sourceSystem,
            String localCode,
            String targetDictionaryKey,
            String category) {
        List<EffectiveTermMapping> resolved = mappings.resolve(
            tenantId,
            runtimeReleaseId,
            sourceSystem,
            localCode,
            targetDictionaryKey,
            category
        );
        if (resolved.isEmpty()) {
            throw new ApiException(
                ErrorCode.ENG_INTEG_001,
                "当前机构生效版本没有可用术语映射: " + sourceSystem + "/" + localCode
            );
        }
        if (resolved.size() > 1) {
            throw new ApiException(
                ErrorCode.ENG_INTEG_001,
                "当前机构生效版本存在歧义术语映射: " + sourceSystem + "/" + localCode
            );
        }
        EffectiveTermMapping mapping = resolved.get(0);
        return new InboundTerminologyMapping(
            mapping.mappingId(),
            mapping.standardTermId(),
            mapping.standardCode(),
            mapping.versionNo()
        );
    }
}
