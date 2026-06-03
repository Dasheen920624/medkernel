# deploy/ 部署入口

本目录有两套部署入口，**正式 / 长期生产以 Docker 标准构建为准**：

- **`docker/`（标准）**：PostgreSQL / Neo4j / Dify / 监控一体化容器化平台，正式环境与后续交付的标准构建。
- **`onprem/`（⚠️ 临时）**：现场单机 + Oracle 的过渡生产方案（当前 `192.168.8.191`），为尽快上线临时搭建，**不作为标准交付**；正式环境请改用 `docker/`，待标准容器化环境就绪后本方案应退役。

> 完整版本约定见 [VERSIONING.md](../VERSIONING.md)。更早的离线发布脚本已移除，需要追溯用 Git 历史。

## 当前目录结构

```text
deploy/
├── README.md
├── docker/                     # 【标准】PostgreSQL / Neo4j / Dify / 监控一体化 Docker 平台
├── onprem/                     # 【临时】现场单机 + Oracle 部署/发布脚本与运维手册（过渡，非标准交付）
└── monitoring/                 # Docker 监控栈复用的 Grafana 面板与 Prometheus 告警规则
```

## 启动与健康检查

运行数据、密钥、Dify 官方副本和备份都保存在仓库外：

```text
/Users/zhikunzheng/work/medkernel/runtime/
```

启动核心模式：

```bash
./deploy/docker/scripts/up.sh core
./deploy/docker/scripts/healthcheck.sh core
```

启动完整模式（附加 Prometheus、Grafana 和官方 Dify）：

```bash
./deploy/docker/scripts/up.sh full
./deploy/docker/scripts/healthcheck.sh full
```

更多端口、备份、恢复、Dify 镜像摘要锁定和服务器迁移说明见
[docker/README.md](docker/README.md)。
