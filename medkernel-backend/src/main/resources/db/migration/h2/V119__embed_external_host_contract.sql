ALTER TABLE embed_launch_token
    ADD COLUMN IF NOT EXISTS parent_origin VARCHAR(512) NULL;

COMMENT ON COLUMN embed_launch_token.parent_origin IS '签发时绑定并通过白名单校验的父系统Origin';
