# 医学评测制品指纹与签署状态设计

## 1. 背景与根因

2026-06-19 22:34，`quality-governor` 在 134 对运行 1、2 完成独立专家签署，数据库和审计均已写入 `PASSED`、审核人和签署时间。随后暴露三个问题：

1. 前端把已通过运行的 `reviewable=false` 解释成红色“当前运行不可签字”，没有区分“已签署”与“被阻断”；
2. 评价指标页面无条件读取评测包列表，`quality-governor` 缺少 `package.read` 时产生无业务价值的 403 与失败审计；
3. 运行 1、2 创建于旧部署制品，但评测运行没有冻结运行制品指纹，签署和 Provider 放行只校验 provider、模型版本、能力码、基准与逐例证据，无法在代码层阻止旧制品评测跨部署复用。

## 2. 目标

- 每次医学评测冻结当前进程的不可变 release fingerprint；
- 专家签署、Provider 上线门禁和 readiness 只认可与当前进程指纹一致的评测；
- 历史签署保留审计，不删除、不改写，但明确标记为“历史制品证据，不可用于当前放行”；
- 已签署运行在页面显示审核人、时间和意见，不再显示为错误；
- 页面只在具备 `package.read` 时读取评测包引用，消除无权限预取和失败审计噪声。

## 3. 设计

### 3.1 运行制品指纹

在 `medkernel.runtime.release-fingerprint` 配置中注入当前运行制品指纹。正式生产中心必须配置非占位值；本地与测试环境可使用 `development`。134 使用精确部署提交 SHA。

`mk_llm_eval_run` 新增可空字段 `release_fingerprint VARCHAR(128)`：

- 新运行必须写入当前指纹；
- 历史运行字段为空，自动视为非当前制品；
- 不回填、不猜测历史值，避免把旧证据伪装成当前证据。

### 3.2 服务门禁

`ModelEvalService` 统一计算 `releaseCurrent`：

- `signOff`：指纹不一致时返回 `ENG_LLM_008`；
- `isClearedForGoLive`：指纹不一致时返回 `false`；
- `getRunDetail`：返回 `releaseCurrent` 和明确阻断原因；
- `runEvaluation`、`runQualityEvaluation`：持久化当前指纹。

生产中心若未配置有效指纹，评测生成失败关闭，不允许生成可被误认的正式证据。

### 3.3 前端状态

医学回归复核抽屉按状态展示：

- `PASSED && releaseCurrent`：绿色“已由独立专家签署放行”；
- `PASSED && !releaseCurrent`：警告“历史制品签署仅保留审计，不可用于当前放行”；
- `PENDING_REVIEW && reviewable`：绿色“证据完整且基准未变化”；
- 其他状态：红色阻断原因。

已签署态展示审核人、签署时间和复核意见；不再出现“当前运行不可签字”的误导。

### 3.4 权限查询

`usePackages` 增加可选 `enabled` 参数。评价指标页读取安全画像，仅在具备 `package.read` 时启用评测包查询；无权限时包版本保持可选文本输入/空选项，不发起 403 请求。

## 4. 数据与迁移

- 新增 V156，五方言一致；
- 字段可空用于保留历史记录；
- 增加中文 `COMMENT ON`；
- 不修改现有 `PASSED`、reviewer、signed_at 或审计链；
- 部署后旧运行 1、2仍可查看，但不再满足当前制品放行门禁，必须重新运行并重新由真人专家签署。

## 5. 测试

- 后端服务测试：旧/异指纹不可签署、不可放行，当前指纹可签署；
- 后端详情测试：返回 `releaseCurrent` 与精确阻断原因；
- 迁移测试：V156 五方言、字段和注释契约；
- 前端测试：已签署态正确展示；历史签署态警告；无 `package.read` 不请求包列表；
- 定向测试后执行后端全量、前端 verify、迁移规约、B0、产品目录与 `git diff --check`。

## 6. 现场修复顺序

1. 部署包含 V156 的新制品，并将 134 的 `MEDKERNEL_RUNTIME_RELEASE_FINGERPRINT` 设置为该部署提交 SHA；
2. 核验旧运行 1、2显示为历史制品证据且 Provider 门禁不认可；
3. 在新制品上重新生成医学评测；
4. 由真实独立专家重新逐例核验并签署；
5. 改由具备 `llm.provider.manage` 的集成运维员精确启用一个 Provider。
