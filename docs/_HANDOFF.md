# 会话接力

## 当前执行

- PR #524 已合入 `main`（`b2e7329e`，squash）：①真实 PG 空库首部署冒烟入 CI；②应急命令非 Web 模式启动修复；③接力中由 CI 真跑 PG 冒烟暴露并修复 `mk_experience_user_pref.pref_value` 列过窄缺陷（见「当前状态」）。CI 8/8 全绿。
- 134 已据 `b2e7329e` 清库重发：Flyway 113/113 全成功、`pref_value` 已 `text`、健康 UP、manifest source=b2e7329e；清库前 `pg_dump` 落 `backups/pre-v35-reclean-20260610-205652.dump`（1.18MB）留底。
- 线1统一承接全部任务；项目全新、不保留兼容层（迁移就地改，134 清库重迁，不追加 ALTER 补丁迁移）。
- 待清理：远程已合并分支 `claude/fix-tenant-admin-profile-gate`、`claude/fix-first-deploy-emergency-net` 删除受自动门禁拦，需人工授权后清。

## 当前状态

- 知识生产服务器（193.112.107.134，govcloud + PostgreSQL 15）已据 `b2e7329e` 清库重发并接管：**Flyway 113/113 真应用**（此前实际停在 V99，本次清库重迁补齐）、`pref_value=text`、每日 02:30 备份至 COS 盘 30 天保留、nginx 已封堵 swagger/api-docs/actuator（health 除外）、java.io.tmpdir 已固化（PrivateTmp 防证据文件丢失）。
- 真实空库首发暴露并修复三类缺陷：①只读事务播种 25006（SystemConfigSeedWriter REQUIRES_NEW）；②指派 String 主键误判 UPDATE（UserPreference/SavedView/LargeListExportJob/ModelCapabilityDefinition 实现 Persistable）；③租户开通接口被 @Profile({dev,test}) 裁剪致 govcloud 404（已去除，ArchUnit 契约锁定 @RestController 禁 profile 裁剪）。
- 新实体持久化规约：业务指派主键要么 null id + BeforeConvertCallback 生成，要么实现 Persistable 显式新建语义。
- 第四类缺陷（本接力 PG 冒烟在 CI 真跑暴露并修复）：通知设置以序列化 JSON 存入 `mk_experience_user_pref.pref_value`，而该列原为 `VARCHAR(256)` 过窄（JSON 约 330 字符 → PSQLException value too long）；H2 不强制 varchar 长度故测不出，正是冒烟卡设计要逮的同族。修复＝V35 五方言 `pref_value` 加宽为大文本（postgres/kingbase=TEXT，h2/oracle/dm=CLOB，复用配置中心 config_value 等 String↔CLOB 既有先例，无需新增转换器），就地改 V35；同族扫描确认其余 `*_id/*_digest/*_hash/*_uri` 均为定长标识，无第二处 JSON-塞-小VARCHAR 风险。
- **本分支补两网（防同族缺陷再潜伏）**：
  - 真实 PG 空库首部署冒烟入 CI（`FirstDeployEmptyPostgresSmokeTest`，`@Tag("docker")`+`disabledWithoutDocker`）：Testcontainers PG15 空库 Flyway 建库 → 启全量上下文（连带 seeder 空库首动）→ 实跑只读事务播种（25006 网，H2 结构测不出）+ 指派主键空表首写（INSERT 误判 UPDATE 网）。CI 有 Docker 必跑，本机无 Docker 跳过。
  - 应急命令非 Web 模式启动修复：根因＝`SecurityConfig.filterChain(HttpSecurity)` 等 servlet Web 专属 Bean 未设条件，`web-application-type=none` 缺 `HttpSecurity` 致上下文起不来、救命通道 ApplicationRunner 永不执行（曾被迫占空业务端口 18081 变通）。修复＝`filterChain`/`csrfDoubleSubmitFilter` 加 `@ConditionalOnWebApplication(SERVLET)`；入口检测 `--bootstrap-emergency` 强制 `WebApplicationType.NONE` 兜底；命令提交后 `ApplicationReadyEvent` 阶段一发干净退出（解决「跑完即杀」），`operations.md §5` 已注记。
- 验证：后端 **2188 项 0 失败 0 错误**（5 项本机无 Docker 跳过：2 既有方言 smoke + 3 新空库 smoke，CI 真跑）；T-GATE（真实性 / 配置边界 / 迁移规约 / 中文注释）changed 全绿。Task A（非 Web 启动，H2）本地完整验证；Task B（PG 空库 smoke）本机无 Docker，红/绿以 CI 为准。

## 下一步

1. 知识生产中心进入内容生产：首批最小内容包（危急值 + DDI Top 50 + 试点专病路径）走「登记→会签→发布→离线导出→院内导入」全链路。
2. 人工授权清理两条已合并远程分支（见上「待清理」）。
3. 不恢复旧并行分线，不新增重复 handoff 文档。
