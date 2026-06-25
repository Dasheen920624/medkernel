# 备份、恢复与清库发布 Runbook

> 适用于 Docker Compose 与 `deploy/onprem` 单机部署。恢复会覆盖目标数据库和运行文件，必须先确认备份、摘要和目标环境。不得在未授权主机上执行清库或恢复。

## 备份

```bash
./deploy/docker/scripts/healthcheck.sh full
./deploy/docker/scripts/backup.sh
```

备份脚本生成 PostgreSQL 自定义格式备份及同名 `.sha256` 摘要。应将二者复制到受控存储，记录：

- 生成时间；
- Git commit；
- 数据库地址和环境；
- 备份文件名；
- SHA-256；
- 操作者和用途。

仓库不保存数据库备份、患者数据、密钥或历史演练截图。

## 恢复前检查

1. 确认目标是隔离验证环境或已经批准覆盖的运行环境；
2. 停止对目标数据库的写入；
3. 再执行一次当前状态备份；
4. 确认备份文件与 `.sha256` 位于同一目录；
5. 记录当前容器、Git commit 和数据库状态。

## 恢复

```bash
./deploy/docker/scripts/restore.sh /path/to/medkernel-YYYYMMDD-HHMMSS.dump
./deploy/docker/scripts/healthcheck.sh full
```

`restore.sh` 必须在恢复前自动校验摘要。缺少摘要或摘要不一致时立即停止，不得绕过。

## 恢复后验证

至少验证：

1. Flyway 状态与目标版本一致；
2. 登录、权限和组织隔离正常；
3. 平台标准版本、机构扩展、当前机构生效版本及其精确知识、规则和路径版本可读；
4. 临床推荐、反馈和审计链可追踪；
5. 外部依赖断开时诚实降级；
6. 服务重启后数据与当前版本保持一致。

演练证据写入目标机受控运行目录，不提交仓库。仓库只保留不含敏感数据的摘要、测试结果和问题结论。

## 清库重部署

项目未上线时允许在完成备份后清理目标库并从统一 V1 重建。步骤见
[部署与演练](../../DEPLOYMENT_AND_REHEARSAL.md)。

单机清库入口 `deploy/onprem/medkernel-fresh-deploy.sh` 必须满足以下顺序：

1. 严格 TLS 预检：验证可信证书链、SAN 主机/IP 匹配、证书有效期和外部 readiness；不得使用 `curl -k` 或 `--insecure`；
2. 同时快照数据库、`conf/`、systemd 单元、Nginx 配置、后端 JAR、前端 `dist`、manifest、运行目录与发布脚本；
3. 备份完成后立即生成 `SHA256SUMS` 并执行 `sha256sum -c`，摘要失败时不得停服或清库；
4. 隔离恢复数据库备份通过后，才允许修改 systemd、执行 `dropdb` 或切换交付文件；
5. 从开始修改 systemd 与 Nginx 候选配置起注册强制恢复事务。`ERR`、`INT`、`TERM`，以及清库后、发布中、候选 readiness 任一点失败，都必须恢复发布前数据库、配置、Nginx、systemd 和前后端交付文件；
6. 恢复完成的判定不是“文件已复制”，而是旧 JAR 摘要一致，内部 readiness 与严格 TLS 外部 readiness 均返回 200；
7. 只有候选版本全部验证通过后才能解除恢复事务。备份与恢复证据保留在本次 `fresh-preclear-*` 目录。

`medkernel-deploy.sh` 的交付文件回滚不可关闭。发布前必须同时备份数据库与运行层，并立即生成、校验摘要。候选 JAR 启动时可能已经执行 Flyway，因此交付文件切换后的错误或信号必须先恢复发布前数据库，再恢复配置、Nginx、systemd、后端 JAR、前端 `dist` 和 manifest，并验证旧版本 readiness。

如自动恢复失败，禁止继续发布或清理备份。应保持流量隔离，使用日志中给出的本次备份目录人工恢复，并在恢复后重新执行摘要校验和旧版本 readiness 验证。
