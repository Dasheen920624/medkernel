ALTER TABLE source_fragment ADD CONSTRAINT uk_source_fragment_version_hash UNIQUE (source_version_id, content_hash);

COMMENT ON COLUMN source_fragment.content_hash IS '来源片段正文的 SHA-256 内容指纹，用于同版本内去重和真实性核验';
