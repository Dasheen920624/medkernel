package com.medkernel.engine.versioning;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.ids.Ulid;

/**
 * SYS-04 历史版本重放服务。
 */
@Service
public class VersionReplayService implements ReplayPort {

    private static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");

    private final AssetVersionRepository assetVersions;
    private final VersionReplayBindingRepository bindings;
    private final Clock clock;

    @Autowired
    public VersionReplayService(
            AssetVersionRepository assetVersions,
            VersionReplayBindingRepository bindings) {
        this(assetVersions, bindings, Clock.systemUTC());
    }

    VersionReplayService(
            AssetVersionRepository assetVersions,
            VersionReplayBindingRepository bindings,
            Clock clock) {
        this.assetVersions = assetVersions;
        this.bindings = bindings;
        this.clock = clock;
    }

    @Override
    @Transactional
    public VersionReplayBinding bindRuntimeResult(VersionReplayBindingCommand command) {
        AssetVersion version = requireVersion(command);
        Instant now = clock.instant();
        String actor = required(command.actor(), "操作人");
        return bindings.save(new VersionReplayBinding(
            null,
            "vrb-" + Ulid.newUlid(),
            command.tenantId(),
            command.assetType(),
            command.assetIdentity(),
            version.versionId(),
            required(command.patientSnapshotId(), "患者快照 ID"),
            required(command.runtimeEventId(), "运行事件 ID"),
            requireSha256(command.resultHash()),
            now,
            actor,
            now,
            actor,
            command.traceId()
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public VersionReplayResult replay(VersionReplayQuery query) {
        String tenantId = required(query.tenantId(), "租户");
        String bindingId = required(query.bindingId(), "重放绑定 ID");
        VersionReplayBinding binding = bindings.findByTenantIdAndBindingId(tenantId, bindingId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "历史重放绑定不存在: " + bindingId));
        AssetVersion version = assetVersions.findByVersionIdAndTenantId(binding.versionId(), tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "历史重放版本不存在: " + binding.versionId()));
        String summary = "历史重放：按患者快照 " + binding.patientSnapshotId()
            + " 与版本 " + version.versionNo()
            + " 复现既往结果；该历史版本不参与新事件解析";
        return new VersionReplayResult(binding, version, summary);
    }

    private AssetVersion requireVersion(VersionReplayBindingCommand command) {
        required(command.tenantId(), "租户");
        required(command.assetIdentity(), "资产身份");
        required(command.versionId(), "版本 ID");
        if (command.assetType() == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "资产类型不能为空");
        }
        AssetVersion version = assetVersions.findByVersionIdAndTenantId(command.versionId(), command.tenantId())
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "资产版本不存在: " + command.versionId()));
        if (version.assetType() != command.assetType()
                || !Objects.equals(version.assetIdentity(), command.assetIdentity())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "版本与重放绑定的资产域不一致");
        }
        if (version.status() == AssetVersionStatus.DRAFT || version.status() == AssetVersionStatus.PENDING_REVIEW) {
            throw new ApiException(ErrorCode.CONFLICT, "未审核版本不得绑定历史重放");
        }
        return version;
    }

    private String requireSha256(String value) {
        String normalized = required(value, "结果摘要");
        if (!SHA256.matcher(normalized).matches()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "结果摘要必须是 SHA-256 十六进制");
        }
        return normalized;
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, label + "不能为空");
        }
        return value.trim();
    }
}
