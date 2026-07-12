package com.medkernel.engine.knowledge.delivery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.knowledge.authority.MedicalPackageType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.crypto.SmCryptoService;
import org.springframework.stereotype.Service;

/** 确定性 FULL 医疗资源包 manifest 的规范编码、解码和摘要入口。 */
@Service
public class FullPackageManifestCodec {

    private static final String SCHEMA_VERSION = "1.0";
    private static final Pattern STABLE_ID =
        Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");
    private static final Pattern SM3 = Pattern.compile("sm3:[0-9a-f]{64}");
    private static final Pattern VERSION =
        Pattern.compile("[0-9]+(?:\\.(?:[0-9]+|x)){1,2}(?:-[A-Za-z0-9.-]+)?");
    private static final Pattern DATABASE_SCHEMA = Pattern.compile("V[1-9][0-9]*");
    private static final Pattern PATH_SEGMENT =
        Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,255}");

    private final CanonicalJson canonicalJson;
    private final SmCryptoService crypto;

    /** 使用平台统一 JSON 和国密实现建立规范 manifest 编解码器。 */
    public FullPackageManifestCodec(ObjectMapper json, SmCryptoService crypto) {
        this.canonicalJson = new CanonicalJson(json);
        this.crypto = crypto;
    }

    /** 将逻辑 manifest 规范化并编码为跨宿主一致的 UTF-8 字节。 */
    public byte[] encode(FullPackageManifest manifest) {
        return canonicalJson.encode(normalize(manifest));
    }

    /** 读取并校验规范 manifest；未知字段或非规范字节一律拒绝。 */
    public FullPackageManifest decode(byte[] bytes) {
        FullPackageManifest normalized = normalize(
            canonicalJson.decodeCanonical(bytes, FullPackageManifest.class));
        if (!Arrays.equals(bytes, canonicalJson.encode(normalized))) {
            throw invalid("manifest 文件条目未按规范相对路径排序");
        }
        return normalized;
    }

    /** 按真实规范 manifest 字节计算带算法前缀的 SM3 摘要。 */
    public String sm3Digest(byte[] manifestBytes) {
        if (manifestBytes == null || manifestBytes.length == 0) {
            throw invalid("manifest 字节不能为空");
        }
        return "sm3:" + HexFormat.of().formatHex(crypto.sm3(manifestBytes));
    }

    private FullPackageManifest normalize(FullPackageManifest manifest) {
        if (manifest == null) {
            throw invalid("manifest 不能为空");
        }
        if (!SCHEMA_VERSION.equals(manifest.schemaVersion())) {
            throw invalid("manifest schemaVersion 仅支持 " + SCHEMA_VERSION);
        }
        if (manifest.packageType() != MedicalPackageType.FULL
                || manifest.parentManifestDigest() != null) {
            throw invalid("首发医疗资源包只允许无父链的 FULL manifest");
        }
        requireStableId(manifest.deliveryId(), "deliveryId");
        requireStableId(manifest.authorityId(), "authorityId");
        requireStableId(manifest.issuerInstanceId(), "issuerInstanceId");
        requireStableId(manifest.keyId(), "keyId");
        requireStableId(manifest.platformReleaseIdentity(), "platformReleaseIdentity");
        if (manifest.releaseSequence() <= 0) {
            throw invalid("releaseSequence 必须大于零");
        }
        FullPackageManifest.Compatibility compatibility = normalize(manifest.compatibility());
        if (manifest.files() == null || manifest.files().isEmpty()) {
            throw invalid("FULL manifest 至少必须绑定一个内容文件");
        }
        Set<String> paths = new HashSet<>();
        List<FullPackageManifest.FileEntry> files = new ArrayList<>();
        for (FullPackageManifest.FileEntry file : manifest.files()) {
            FullPackageManifest.FileEntry normalized = normalize(file);
            if (!paths.add(normalized.path())) {
                throw invalid("manifest 文件路径重复: " + normalized.path());
            }
            files.add(normalized);
        }
        files.sort(java.util.Comparator.comparing(FullPackageManifest.FileEntry::path));
        return new FullPackageManifest(
            SCHEMA_VERSION,
            MedicalPackageType.FULL,
            manifest.deliveryId(),
            manifest.authorityId(),
            manifest.issuerInstanceId(),
            manifest.keyId(),
            manifest.releaseSequence(),
            manifest.platformReleaseIdentity(),
            null,
            compatibility,
            List.copyOf(files));
    }

    private FullPackageManifest.Compatibility normalize(
            FullPackageManifest.Compatibility compatibility) {
        if (compatibility == null) {
            throw invalid("manifest compatibility 不能为空");
        }
        return new FullPackageManifest.Compatibility(
            requiredVersion(compatibility.packageFormatVersion(), "packageFormatVersion"),
            requiredVersion(compatibility.minimumEngineVersion(), "minimumEngineVersion"),
            requiredVersion(compatibility.maximumEngineVersion(), "maximumEngineVersion"),
            requiredDatabaseSchema(
                compatibility.minimumDatabaseSchemaVersion(), "minimumDatabaseSchemaVersion"),
            requiredDatabaseSchema(
                compatibility.maximumDatabaseSchemaVersion(), "maximumDatabaseSchemaVersion"));
    }

    private FullPackageManifest.FileEntry normalize(FullPackageManifest.FileEntry file) {
        if (file == null) {
            throw invalid("manifest 文件条目不能为空");
        }
        String path = required(file.path(), "文件路径");
        requireCanonicalRelativePath(path);
        if (file.size() <= 0) {
            throw invalid("manifest 文件大小必须大于零: " + path);
        }
        if (file.sm3Digest() == null || !SM3.matcher(file.sm3Digest()).matches()) {
            throw invalid("manifest 文件缺少规范 SM3 摘要: " + path);
        }
        return new FullPackageManifest.FileEntry(path, file.size(), file.sm3Digest());
    }

    private static void requireCanonicalRelativePath(String path) {
        if (path.startsWith("/") || path.contains("\\") || path.contains("//")) {
            throw invalid("manifest 文件路径必须是规范相对路径: " + path);
        }
        for (String segment : path.split("/", -1)) {
            if (!PATH_SEGMENT.matcher(segment).matches()) {
                throw invalid("manifest 文件路径包含越界或非规范片段: " + path);
            }
        }
    }

    private static void requireStableId(String value, String label) {
        if (value == null || !value.equals(value.trim()) || !STABLE_ID.matcher(value).matches()) {
            throw invalid(label + " 必须是与宿主无关的稳定标识");
        }
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw invalid(label + " 不能为空且不能包含首尾空白");
        }
        return value;
    }

    private static String requiredVersion(String value, String label) {
        String normalized = required(value, label);
        if (!VERSION.matcher(normalized).matches()) {
            throw invalid(label + " 必须是确定性版本或版本范围");
        }
        return normalized;
    }

    private static String requiredDatabaseSchema(String value, String label) {
        String normalized = required(value, label);
        if (!DATABASE_SCHEMA.matcher(normalized).matches()) {
            throw invalid(label + " 必须是受支持的 Vn 数据库模式版本");
        }
        return normalized;
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message);
    }
}
