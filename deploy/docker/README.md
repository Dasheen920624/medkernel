# Docker Development Platform

本目录提供可保留、可迁移的 MedKernel 开发部署环境。仓库维护应用、PostgreSQL、Neo4j
与监控的编排；Dify 使用固定版本的官方 Compose 项目，并通过仓库内的摘要覆盖文件
锁定默认完整模式镜像。所有数据、密钥、备份和 Dify 运行副本均存放在仓库之外。

## 运行边界

| 内容 | 位置 | 说明 |
|---|---|---|
| 编排与脚本 | `deploy/docker/` | 可提交并随服务器部署迁移 |
| 开发运行根目录 | `/Users/zhikunzheng/work/medkernel/runtime/` | 不进入 Git |
| MedKernel PostgreSQL | `runtime/data/postgres/` | 业务数据权威来源 |
| Neo4j | `runtime/data/neo4j/` | 可重建的图谱投影 |
| Dify 官方栈 | `runtime/dify/v1.14.0/` | 独立可选依赖 |
| 备份 | `runtime/backups/` | PostgreSQL 自包含备份 |

## 服务端口

| 服务 | 地址 |
|---|---|
| 前端入口 | `http://localhost:8088/` |
| 后端健康检查 | `http://localhost:18080/medkernel/actuator/health` |
| PostgreSQL | `localhost:15432` |
| Neo4j Browser / Bolt | `http://localhost:7474/` / `bolt://localhost:7687` |
| Prometheus / Grafana | `http://localhost:9090/` / `http://localhost:3000/` |
| Dify（完整模式） | `http://localhost:8090/` |

## 首次启动

需要 Docker Desktop 与 Java 21。首次准备运行目录：

```bash
./deploy/docker/scripts/bootstrap-runtime.sh --skip-dify
./deploy/docker/scripts/up.sh core
./deploy/docker/scripts/healthcheck.sh core
```

`core` 启动 PostgreSQL、Neo4j、后端和前端，适合日常业务开发。需要监控与 Dify 时：

```bash
./deploy/docker/scripts/up.sh full
./deploy/docker/scripts/healthcheck.sh full
```

首次 `full` 会将官方 Dify `v1.14.0` 检出到运行目录；耗时及下载体积会明显高于
`core`。`deploy/docker/dify/compose.lock.yml` 会覆盖默认启动组件的镜像摘要，避免
上游 `latest` 辅助标签在不同机器或不同时间解析为不同内容。

## 密钥与配置

`bootstrap-runtime.sh` 首次运行时从 `.env.example` 生成：

```text
/Users/zhikunzheng/work/medkernel/runtime/env/medkernel.env
```

其中 PostgreSQL、Neo4j 与 Grafana 密码会自动随机生成，权限设置为 `600`。首次完整
模式初始化还会在 Dify 官方环境文件生成数据库、缓存、沙箱、会话与插件随机密钥，
避免沿用上游示例凭据。迁移到其他服务器时，应设置目标机的 `MEDKERNEL_RUNTIME_ROOT`，
重新生成本地密钥或安全传递运行环境文件，不要提交该文件。

## 停止、降级与备份

```bash
./deploy/docker/scripts/down.sh core
./deploy/docker/scripts/down.sh full
./deploy/docker/scripts/down.sh optional
./deploy/docker/scripts/backup.sh
./deploy/docker/scripts/restore.sh /path/to/medkernel-backup.dump
./deploy/docker/scripts/backup-restore-drill.sh
```

`optional` 仅停止 Neo4j 与 Dify，用于验证 PostgreSQL 驱动的基础能力在可选服务不可用
时仍然可运行。`backup-restore-drill.sh` 会把当前 PostgreSQL 备份恢复到隔离演练库，
校验 `flyway_schema_history` 后删除演练库并生成恢复证据，不会覆盖主库。停止操作不会
删除持久化目录。

国产化数据库烟测必须在具备达梦或人大金仓真实实例与闭源 JDBC 驱动的内网环境执行：

```bash
MEDKERNEL_GOV_DATABASE_DIALECT=dm \
MEDKERNEL_GOV_DB_URL=jdbc:dm://... \
MEDKERNEL_GOV_DB_DRIVER=dm.jdbc.driver.DmDriver \
MEDKERNEL_GOV_DB_USERNAME=... \
MEDKERNEL_GOV_DB_PASSWORD=... \
MEDKERNEL_GOV_JDBC_JAR=/path/to/DmJdbcDriver.jar \
./deploy/docker/scripts/govcloud-smoke.sh
```

脚本缺少任一连接条件会直接失败，不把“未连接”伪装成通过。

## 迁移到服务器

1. 将仓库检出到目标服务器，并安装 Docker Compose v2。
2. 设置 `MEDKERNEL_RUNTIME_ROOT` 为服务器的数据目录。
3. 执行 `bootstrap-runtime.sh` 生成目标机密钥与目录。
4. 使用 `up.sh core` 或 `up.sh full` 启动并执行 `healthcheck.sh`。
5. 用 `backup.sh` 建立迁移/升级前备份，备份文件存入受控存储。

升级 Dify 版本或变更默认向量存储时，应作为独立评审变更同步刷新并验证
`deploy/docker/dify/compose.lock.yml`。
