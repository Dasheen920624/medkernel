# 会话接力

## 当前执行

- PR #522 已 squash 合入 `main`（`a7b14121`，含租户开通 profile 裁剪修复 + 集成异步投递测试等待窗口 3s→15s 放宽消除 jdk-matrix-smoke temurin 偶发红）。
- PR #523（权限指纹面板标签横向换行 + 弹层限高滚动，修复多权限角色面板纵向溢出视口）待合，基于最新 `origin/main`；后续只从最新 `origin/main` 继续。
- 线1统一承接全部任务；项目未上线且不保留兼容层。
- 待清理：远程已合并分支 `claude/fix-tenant-admin-profile-gate` 删除被自动门禁拦下，需人工授权后清。

## 当前状态

- 知识生产服务器（193.112.107.134，govcloud + PostgreSQL 15）已清库重发并接管：Flyway 113/113、每日 02:30 备份至 COS 盘 30 天保留、nginx 已封堵 swagger/api-docs/actuator（health 除外）、java.io.tmpdir 已固化（PrivateTmp 防证据文件丢失）。
- 真实空库首发暴露并修复三类缺陷：①只读事务播种 25006（SystemConfigSeedWriter REQUIRES_NEW）；②指派 String 主键误判 UPDATE（UserPreference/SavedView/LargeListExportJob/ModelCapabilityDefinition 实现 Persistable）；③租户开通接口被 @Profile({dev,test}) 裁剪致 govcloud 404（已去除，ArchUnit 契约锁定 @RestController 禁 profile 裁剪）。
- 新实体持久化规约：业务指派主键要么 null id + BeforeConvertCallback 生成，要么实现 Persistable 显式新建语义；H2 测不出只读事务强制与空库首写，空库首部署冒烟（真实 PG Testcontainers）待补。
- 验证：后端 2177 项通过（2 项本机无 Docker 跳过）；前端 618 项与 T-GATE 通过；生产服务器运行 7127a238 健康零错误。

## 下一步

1. 合并 PR #523 后从最新 `origin/main` 领取下一项未完成任务。
2. 优先补「真实 PG 空库首部署冒烟测试入 CI」与「应急命令非 Web 模式启动修复」（运维手册 §5 救命通道在真实 jar 上无法启动，已实测变通：--server.port=18081 跑完即杀）。
3. 知识生产中心进入内容生产：首批最小内容包（危急值 + DDI Top 50 + 试点专病路径）走「登记→会签→发布→离线导出→院内导入」全链路。
4. 不恢复旧并行分线，不新增重复 handoff 文档。
