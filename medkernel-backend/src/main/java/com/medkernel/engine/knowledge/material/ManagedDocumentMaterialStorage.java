package com.medkernel.engine.knowledge.material;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.config.SystemConfigService;
import com.medkernel.shared.hash.Sha256ContentHash;

/**
 * 受管资料库存储实现。当前支持现场显式配置的 {@code file://} 本地资料库根；
 * 其他对象或网关协议必须配置对应适配器，未配置时诚实阻断，不伪造落库。
 */
@Service
public class ManagedDocumentMaterialStorage implements DocumentMaterialStoragePort {

    private static final String LOCAL_FILE_BACKEND = "LOCAL_FILE";

    private final SystemConfigService configService;
    private final KnowledgeMaterialObjectRepository repository;

    public ManagedDocumentMaterialStorage(SystemConfigService configService,
                                          KnowledgeMaterialObjectRepository repository) {
        this.configService = configService;
        this.repository = repository;
    }

    @Override
    @Transactional
    public StoredDocumentMaterial store(DocumentMaterialStoreRequest request) {
        validateRequest(request);
        String normalizedHash = Sha256ContentHash.normalizeExternalSha256(request.sha256());
        return repository.findByTenantIdAndScopeKeyAndSha256(
                request.tenantId(), request.scopeKey(), normalizedHash)
            .map(KnowledgeMaterialObject::toStored)
            .orElseGet(() -> storeNew(request, normalizedHash));
    }

    @Override
    public byte[] fetch(String tenantId, String fileUri) {
        KnowledgeMaterialObject object = repository.findByTenantIdAndFileUri(tenantId, fileUri)
            .orElseThrow(() -> ApiException.notFound("文档原件"));
        if (!LOCAL_FILE_BACKEND.equals(object.storageBackend())) {
            throw new ApiException(ErrorCode.DOWNSTREAM_UNAVAILABLE, "资料库读取适配器未配置：" + object.storageBackend());
        }
        try {
            byte[] bytes = Files.readAllBytes(Path.of(URI.create(object.fileUri())));
            String hash = Sha256ContentHash.sha256Bytes(bytes, "文档原件不能为空");
            if (!hash.equals(object.sha256())) {
                throw new ApiException(ErrorCode.ENG_EVID_002, "文档原件指纹与账本不一致，禁止取回");
            }
            return bytes;
        } catch (IOException exception) {
            throw new ApiException(ErrorCode.DOWNSTREAM_UNAVAILABLE, "文档原件资料库不可读取", exception);
        }
    }

    @Override
    public boolean exists(String tenantId, String fileUri) {
        return repository.findByTenantIdAndFileUri(tenantId, fileUri)
            .filter(object -> LOCAL_FILE_BACKEND.equals(object.storageBackend()))
            .map(object -> Files.exists(Path.of(URI.create(object.fileUri()))))
            .orElse(false);
    }

    @Override
    @Transactional
    public void delete(String tenantId, String fileUri) {
        KnowledgeMaterialObject object = repository.findByTenantIdAndFileUri(tenantId, fileUri)
            .orElseThrow(() -> ApiException.notFound("文档原件"));
        if (!LOCAL_FILE_BACKEND.equals(object.storageBackend())) {
            throw new ApiException(ErrorCode.DOWNSTREAM_UNAVAILABLE, "资料库删除适配器未配置：" + object.storageBackend());
        }
        try {
            Files.deleteIfExists(Path.of(URI.create(object.fileUri())));
            repository.delete(object);
        } catch (IOException exception) {
            throw new ApiException(ErrorCode.DOWNSTREAM_UNAVAILABLE, "文档原件资料库不可删除", exception);
        }
    }

    private StoredDocumentMaterial storeNew(DocumentMaterialStoreRequest request, String normalizedHash) {
        URI root = materialRootUri();
        if (!"file".equalsIgnoreCase(root.getScheme())) {
            throw new ApiException(ErrorCode.DOWNSTREAM_UNAVAILABLE, "资料库写入适配器未配置：" + root.getScheme());
        }
        Path rootPath = Path.of(root).normalize();
        String safeScope = safeSegment(request.scopeKey(), "资料库作用域不能为空");
        String safeName = safeFileName(request.fileName());
        Path target = rootPath
            .resolve(safeScope)
            .resolve(normalizedHash.substring(0, 2))
            .resolve(normalizedHash)
            .resolve(safeName)
            .normalize();
        if (!target.startsWith(rootPath)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "资料库对象路径越界");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, request.bytes());
        } catch (IOException exception) {
            throw new ApiException(ErrorCode.DOWNSTREAM_UNAVAILABLE, "文档原件资料库不可写入", exception);
        }
        KnowledgeMaterialObject saved = repository.save(new KnowledgeMaterialObject(
            null,
            request.tenantId(),
            request.scopeKey(),
            target.toUri().toString(),
            normalizedHash,
            request.contentType(),
            (long) request.bytes().length,
            LOCAL_FILE_BACKEND,
            request.sourceChannel(),
            Instant.now(),
            request.actor()));
        return saved.toStored();
    }

    private URI materialRootUri() {
        String root = configService.runtimeKnowledgeLiteratureMaterialRootUri();
        if (root == null || root.isBlank()) {
            throw new ApiException(ErrorCode.CONFLICT, "平台知识文献资料库根地址未配置，禁止存储文档原件");
        }
        return URI.create(root.trim());
    }

    private static void validateRequest(DocumentMaterialStoreRequest request) {
        if (request == null || request.tenantId() == null || request.tenantId().isBlank()) {
            throw ApiException.tenantMissing();
        }
        if (request.bytes() == null || request.bytes().length == 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "文档原件不能为空");
        }
        String computed = Sha256ContentHash.sha256Bytes(request.bytes(), "文档原件不能为空");
        String normalizedHash = Sha256ContentHash.normalizeExternalSha256(request.sha256());
        if (!computed.equals(normalizedHash)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "文档原件 SHA-256 与请求不一致");
        }
    }

    private static String safeSegment(String value, String blankMessage) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, blankMessage);
        }
        return value.trim().replaceAll("[^A-Za-z0-9._-]", "-");
    }

    private static String safeFileName(String value) {
        String fileName = value == null || value.isBlank() ? "material.bin" : value.trim();
        int slash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        if (slash >= 0) {
            fileName = fileName.substring(slash + 1);
        }
        fileName = fileName.replaceAll("[^A-Za-z0-9._-]", "-");
        if (fileName.isBlank() || ".".equals(fileName) || "..".equals(fileName)) {
            return "material.bin";
        }
        return fileName.toLowerCase(Locale.ROOT);
    }
}
