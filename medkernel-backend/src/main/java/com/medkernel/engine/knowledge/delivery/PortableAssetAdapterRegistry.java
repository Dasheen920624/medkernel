package com.medkernel.engine.knowledge.delivery;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.crypto.SmCryptoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 13 类版本化资产的完整 `.mkp` 适配器注册表。
 *
 * <p>注册表在构造期拒绝缺类型和重复类型，使新增枚举值无法静默绕过导出、校验或物化合同。
 */
@Service
public class PortableAssetAdapterRegistry {

    private final Map<VersionedAssetType, PortableAssetAdapter> byType;
    private final List<PortableAssetAdapter> adapters;

    /** 使用统一规范编码与 SM3 实现登记全部枚举类型的默认适配器。 */
    @Autowired
    public PortableAssetAdapterRegistry(ObjectMapper json, SmCryptoService crypto) {
        this(defaultAdapters(json, crypto));
    }

    /**
     * 用显式适配器集合建立严格注册表；供组合根和独立实现复用同一完整性校验。
     */
    public PortableAssetAdapterRegistry(Collection<PortableAssetAdapter> adapters) {
        if (adapters == null) {
            throw new IllegalStateException("医疗资源包适配器集合不能为空");
        }
        EnumMap<VersionedAssetType, PortableAssetAdapter> indexed =
            new EnumMap<>(VersionedAssetType.class);
        for (PortableAssetAdapter adapter : adapters) {
            if (adapter == null || adapter.assetType() == null) {
                throw new IllegalStateException("医疗资源包适配器及其资产类型不能为空");
            }
            if (indexed.putIfAbsent(adapter.assetType(), adapter) != null) {
                throw new IllegalStateException("医疗资源包适配器类型重复: " + adapter.assetType());
            }
        }
        Set<VersionedAssetType> missing = EnumSet.allOf(VersionedAssetType.class);
        missing.removeAll(indexed.keySet());
        if (!missing.isEmpty()) {
            throw new IllegalStateException("医疗资源包适配器缺少资产类型: " + missing);
        }
        this.byType = Map.copyOf(indexed);
        this.adapters = List.of(VersionedAssetType.values()).stream()
            .map(indexed::get)
            .toList();
    }

    /** 按枚举定义顺序返回恰好 13 类适配器。 */
    public List<PortableAssetAdapter> adapters() {
        return adapters;
    }

    /** 返回指定资产类型唯一适配器。 */
    public PortableAssetAdapter require(VersionedAssetType assetType) {
        PortableAssetAdapter adapter = assetType == null ? null : byType.get(assetType);
        if (adapter == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "医疗资源包资产类型未登记: " + assetType);
        }
        return adapter;
    }

    private static Collection<PortableAssetAdapter> defaultAdapters(
            ObjectMapper json,
            SmCryptoService crypto) {
        CanonicalJson canonicalJson = new CanonicalJson(json);
        List<PortableAssetAdapter> result = new ArrayList<>();
        for (VersionedAssetType type : VersionedAssetType.values()) {
            result.add(new CanonicalPortableAssetAdapter(type, canonicalJson, crypto));
        }
        return result;
    }
}
