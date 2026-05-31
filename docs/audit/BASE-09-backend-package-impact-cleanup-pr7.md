# BASE-09 后端包影响分析真实性净化 PR7 记录

## 范围

- `PackageEngineService.calculateDiff` 不再把受影响科室伪造为 `dept-default`。
- 规则资产的影响科室改为读取 `RuleDefinition.applicableOrgUnitId`，评估指标继续读取 `EvaluationIndicator.responsibleDepartmentId`，并统一过滤空白值。
- 路径模板当前没有责任科室 / 组织归属字段，本批只做诚实降级：查询失败直接暴露错误；查询成功但无真实字段时不返回伪造科室。
- 未建模资产类型（知识、术语、随访等）不再返回默认科室。
- 真实性门禁新增 `backend.fake-impact-department`，阻断后端生产代码再次出现 `dept-default` 或“模拟受影响的责任科室”假逻辑。

## 红绿验证

- 红灯 1：新增 `PackageEngineServiceTest.calculateDiffUsesOnlyRealAssetDepartments` 后，旧代码返回 `["dept-default", "dept-eval"]`，缺失真实 `dept-rule`。
- 红灯 2：新增 `PackageEngineServiceTest.calculateDiffDoesNotForgeDepartmentWhenAssetLookupFails` 后，旧代码吞掉规则查询异常并伪造 `dept-default`，没有抛出原始错误。
- 红灯 3：新增真实性门禁测试后，`dept-default` 未被拦截，测试失败。
- 绿灯：改为真实字段读取、移除 catch 伪降级并补门禁规则后，上述测试通过。

## 已执行验证

- `mvn -B -q -Dtest=PackageEngineServiceTest test`
- `node --test scripts/authenticity-guard.test.mjs`
- `node scripts/authenticity-guard.mjs --mode=inventory`
- `node --test scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/authenticity-guard.test.mjs`
- `mvn -B -q test`（含 Docker Testcontainers 下 PostgreSQL / Oracle 迁移烟测）
- `git diff --check`
- `rg -n "dept-default|模拟受影响的责任科室" medkernel-backend/src/main/java frontend/src` 无生产路径命中。

## 剩余边界

- 本批只清理包影响分析中的假科室和吞错成功，不宣称 PKG-01 / GA-ENG-API-10 全部完成。
- 路径模板影响科室需要在后续业务卡中显式建模责任科室 / 组织归属字段后再展示；在字段不存在前，禁止用默认科室补空白。
- 包发布仍有回滚二次确认、回滚反向投影、影响患者 / 规则 / 路径级联分析和影响范围导出等残留，应继续按一逻辑单元一 PR 收口。
