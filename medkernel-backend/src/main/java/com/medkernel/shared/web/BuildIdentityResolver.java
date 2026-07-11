package com.medkernel.shared.web;

import java.io.InputStream;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 从后端 JAR 根目录的不可变构建元数据解析候选提交，避免把外置配置冒充制品身份。
 */
@Component
public final class BuildIdentityResolver {

    private static final String METADATA_PATH = "META-INF/medkernel-build.json";
    private static final Pattern FULL_COMMIT = Pattern.compile("^[a-f0-9]{40}$");

    private final ObjectMapper objectMapper;
    private final Supplier<InputStream> metadataSource;

    @Autowired
    public BuildIdentityResolver(ObjectMapper objectMapper) {
        this(
            objectMapper,
            () -> BuildIdentityResolver.class.getClassLoader().getResourceAsStream(METADATA_PATH)
        );
    }

    BuildIdentityResolver(ObjectMapper objectMapper, Supplier<InputStream> metadataSource) {
        this.objectMapper = objectMapper;
        this.metadataSource = metadataSource;
    }

    public BuildIdentity resolve() {
        try (InputStream input = metadataSource.get()) {
            if (input == null) {
                return BuildIdentity.unbound("METADATA_MISSING");
            }
            JsonNode metadata = objectMapper.readTree(input);
            String candidateCommit = metadata.path("candidateCommit").asText();
            if (
                !"1.0.0".equals(metadata.path("schemaVersion").asText())
                    || !"MEDKERNEL_BUILD_METADATA".equals(metadata.path("kind").asText())
                    || !"BACKEND_JAR".equals(metadata.path("artifactId").asText())
                    || !FULL_COMMIT.matcher(candidateCommit).matches()
            ) {
                return BuildIdentity.unbound("METADATA_INVALID");
            }
            return BuildIdentity.bound(candidateCommit);
        } catch (Exception ignored) {
            return BuildIdentity.unbound("METADATA_INVALID");
        }
    }
}
