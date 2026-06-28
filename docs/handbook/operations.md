# 运维手册

## 日常入口

- 健康：`/medkernel/actuator/health`
- 就绪：`/medkernel/actuator/health/readiness`
- 指标：`/medkernel/actuator/prometheus`
- API：`/medkernel/api/v1`
- OpenAPI：`/medkernel/swagger-ui.html`

## 运行原则

- 只展示真实健康、迁移、同步、模型和审计状态。
- 外部系统或模型断连时保持 B0 运行并明确降级。
- 发布前核查来源、内容摘要、依赖闭包、影响、安全校验和回滚。
- 临床运行只读取当前机构生效版本；发布资产版本不会原地改变既有临床事件。
- 任何凭据、JWT、恢复码、患者明文和模型密钥不得进入日志或证据。
- MFA 由全局配置控制，默认关闭；开启后按真实登录会话验证。

## 发布与回滚

```bash
sudo medkernel-deploy --status
sudo medkernel-deploy
sudo medkernel-deploy --rollback
```

普通发布会备份交付文件和配置。数据库全新部署使用
`deploy/onprem/medkernel-fresh-deploy.sh`，必须先完成数据库备份和隔离恢复验证。

## 备份恢复

容器环境使用：

```bash
deploy/docker/scripts/backup.sh
deploy/docker/scripts/backup-restore-drill.sh
```

单机环境由全新部署脚本创建数据库 dump、SHA-256 和隔离恢复证据。详细说明见
[备份恢复](runbooks/backup-restore.md)。

## 故障处理

1. 停止新的发布和高风险配置变更。
2. 查看 readiness、应用日志、数据库连接池和 Flyway 版本。
3. 确认是应用、数据库、模型、图投影还是外部系统故障。
4. 优先恢复确定性 B0 主链；不得把降级写成成功。
5. 必要时回滚交付文件；数据库恢复必须按已验证备份执行。
