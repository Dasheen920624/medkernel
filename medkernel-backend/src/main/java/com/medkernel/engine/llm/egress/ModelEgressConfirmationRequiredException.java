package com.medkernel.engine.llm.egress;

import java.util.List;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 高敏模型外调缺少责任确认时抛出的可操作阻断异常。
 */
public class ModelEgressConfirmationRequiredException extends ApiException {

    private final String capabilityCode;
    private final String payloadHash;
    private final List<String> egressFields;
    private final String providerCode;

    public ModelEgressConfirmationRequiredException(
            String capabilityCode,
            String payloadHash,
            List<String> egressFields,
            String providerCode,
            String message) {
        super(ErrorCode.ENG_LLM_007, message);
        this.capabilityCode = capabilityCode;
        this.payloadHash = payloadHash;
        this.egressFields = egressFields == null ? List.of() : List.copyOf(egressFields);
        this.providerCode = providerCode;
    }

    public String capabilityCode() {
        return capabilityCode;
    }

    public String payloadHash() {
        return payloadHash;
    }

    public List<String> egressFields() {
        return egressFields;
    }

    public String providerCode() {
        return providerCode;
    }
}
