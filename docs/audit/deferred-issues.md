# 当前待处理问题

这里只记录无法由当前仓库代码直接关闭的外部环境事项。已解决事项不留历史账本。

| ID | 事项 | 当前状态 | 关闭条件 |
|---|---|---|---|
| DEFER-001 | 人大金仓与达梦真实运行环境尚未接入当前工作机 | 不阻断 PostgreSQL/H2/Oracle 上线验证 | 在目标数据库执行 V1、启动应用并提交脱敏 smoke 结果 |
| DEFER-002 | 134 公网证书缺少有效 SAN | 2026-06-24 严格 TLS 探测显示当前证书为自签 `CN=193.112.107.134` 且无 SAN；清库部署和公网浏览器验收不得放行 | 安装含正确域名或 IP SAN 的可信证书并完成严格 TLS 与浏览器验证 |
| DEFER-003 | 134 生产环境缺少首次接管令牌配置 | 2026-06-24 已安装当前清库部署脚本后执行只读环境预检，失败于 `MEDKERNEL_BOOTSTRAP_INIT_TOKEN` 未配置；远端也不存在 `/zoesoft/medkernel/conf/bootstrap-init-token.txt`，未读取或输出密钥值 | 在 `/zoesoft/medkernel/conf/medkernel.env` 配置唯一真实接管令牌并通过环境预检 |
