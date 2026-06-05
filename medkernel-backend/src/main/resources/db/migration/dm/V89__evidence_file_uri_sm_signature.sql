-- MedKernel v1.0 GA · EVID-01 证据真实文件与国密签名（达梦）
-- 为 evidence_snapshot 补真实文件 URI、文件 SM3 摘要与 SM3_WITH_SM2 签名材料，兼容既有快照行并由服务层保证新写入完整。
-- ROLLBACK：确认没有依赖文件下载、SM3 文件摘要和 SM2 验签后，删除本迁移新增 5 个字段并恢复 payload_hash 注释。

ALTER TABLE evidence_snapshot ADD (
    file_uri VARCHAR2(512),
    file_digest VARCHAR2(128),
    signature_algorithm VARCHAR2(32),
    signature_value VARCHAR2(2048),
    signer_public_key VARCHAR2(2048)
);

COMMENT ON COLUMN evidence_snapshot.payload_hash IS '证据规范串 SM3 摘要，格式 sm3:<hex>';
COMMENT ON COLUMN evidence_snapshot.file_uri IS '真实证据文件下载 URI；由证据服务写出文件后生成，不得伪造';
COMMENT ON COLUMN evidence_snapshot.file_digest IS '真实证据文件内容 SM3 摘要，格式 sm3:<hex>';
COMMENT ON COLUMN evidence_snapshot.signature_algorithm IS '证据链签名算法，固定为 SM3_WITH_SM2';
COMMENT ON COLUMN evidence_snapshot.signature_value IS '基于证据规范串的 SM2 签名值（Base64）';
COMMENT ON COLUMN evidence_snapshot.signer_public_key IS '验签用 SM2 公钥（X.509 Base64）';
