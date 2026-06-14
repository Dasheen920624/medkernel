ALTER TABLE embed_launch_token ADD parent_origin VARCHAR2(512) NULL;

COMMENT ON COLUMN embed_launch_token.parent_origin IS '签发时绑定并通过白名单校验的父系统Origin';
