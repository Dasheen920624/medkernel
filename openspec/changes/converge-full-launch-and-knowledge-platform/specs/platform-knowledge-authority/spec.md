## ADDED Requirements

### Requirement: 稳定的平台知识权威身份

系统 SHALL 为平台知识权威持久化全局稳定且不可变的 authorityId；authorityId MUST 与 IP、主机名、部署目录、数据库主键和当前宿主解耦，并始终归属唯一平台主租户 t-1。所有平台包、发布登记、信任记录和迁移交接 MUST 引用该 authorityId，宿主迁移或备份恢复不得生成第二个平台权威身份。

#### Scenario: 134 宿主地址变化

- **WHEN** 134 更换 IP、主机名或从备份恢复到等价宿主
- **THEN** 系统返回与迁移前相同的 authorityId，医院既有信任关系无需重新登记

#### Scenario: 试图重建第二个平台权威

- **WHEN** 操作者在 t-1 已有平台知识权威的情况下请求创建另一个 authorityId
- **THEN** 系统 MUST 拒绝请求并记录包含操作者、现有 authorityId 和 traceId 的失败审计

### Requirement: 发布实例身份与唯一活动发布者

每个实际发布实例 SHALL 拥有唯一且不可变的 issuerInstanceId，同一 authorityId 在任一时刻 MUST 只有一个被登记为 ACTIVE 的发布者。只有该 issuerInstanceId 及其有效外置密钥才能签发和登记新平台包；待命、冻结、已交接或已吊销实例 MUST 被禁止签发。首次上线 MUST 固定 134 为活动发布者，不要求在运行期提供自动接管接口。

#### Scenario: 134 作为首发活动发布者

- **WHEN** 134 以稳定 issuerInstanceId 登记为某 authorityId 的活动发布者并绑定有效外置密钥
- **THEN** 系统只允许该实例签发新包，并把 issuerInstanceId 与 keyId 写入签名信封和包注册表

#### Scenario: 非活动实例请求签发

- **WHEN** 任一未登记为 ACTIVE、已冻结或已吊销的 issuerInstanceId 请求签发
- **THEN** 系统 MUST 拒绝请求、不得产生包，并记录权威与实例身份的失败审计

#### Scenario: 非活动实例签发的包进入医院

- **WHEN** 医院导入的包声明某 authorityId 但 issuerInstanceId 在对应交接序号下不是活动发布者
- **THEN** 系统 MUST 拒绝该包且不得物化或激活其中任何资产

### Requirement: 固定信任根与国密签发

医院 SHALL 在受控初始化时固定绑定 authorityId 与平台信任根指纹，并 MUST 仅信任锚定到该根或其已授权过渡链的发布密钥。平台包、发布登记和迁移交接 MUST 使用 SM2 签名并携带 keyId、证书链、有效期和签发实例身份；主机地址或包内任意自带公钥 MUST NOT 成为信任依据。

#### Scenario: 可信活动实例签发

- **WHEN** 包的 SM2 签名可沿证书链验证到医院固定的信任根，且 keyId、issuerInstanceId 和 authorityId 均处于有效授权范围
- **THEN** 权威校验 SHALL 返回可信签发者及完整信任链摘要

#### Scenario: 包携带临时自签公钥

- **WHEN** 包内签名可由其自带公钥验真但无法锚定到医院固定信任根
- **THEN** 系统 MUST 判定发布者不可信并拒绝导入

#### Scenario: 仅宿主地址匹配

- **WHEN** 包来自已知的 134 IP 或主机名但签名链不可信
- **THEN** 系统 MUST 拒绝该包，且不得以网络来源覆盖信任校验结果

### Requirement: 密钥轮换与吊销连续性

权威服务 SHALL 维护不可改写的密钥生命周期和单调追加的吊销记录。发布密钥轮换 MUST 由当前可信根授权；信任根过渡 MUST 由旧可信根签署并显式绑定 authorityId、新根指纹、生效边界和交接序号。医院 MUST 拒绝未知、过期、超出授权序号或已吊销密钥签发的新导入包，且普通平台包不得静默替换固定信任根。

#### Scenario: 离线介质携带合法轮换链

- **WHEN** 医院收到的包同时携带由当前可信根签署的密钥轮换记录和新 keyId 的有效 SM2 签名
- **THEN** 系统 SHALL 在验证完整过渡链后更新本地信任检查点并接受该签名

#### Scenario: 已吊销密钥再次签发

- **WHEN** 新导入包由医院本地已知的吊销 keyId 签发
- **THEN** 系统 MUST 拒绝该包、保留当前生效版本并记录高风险验签失败审计

#### Scenario: 未授权根替换

- **WHEN** 包声明新的根证书但没有旧可信根签署的过渡记录
- **THEN** 系统 MUST 拒绝根替换和该包，不得修改本地固定信任关系

### Requirement: 不可变包注册表与权威审计

平台 SHALL 按 authorityId 维护不可变医疗资源交付注册表，至少记录 deliveryId、发布序号、manifest 摘要、issuerInstanceId、keyId、父交付或基线摘要、包类型、签发状态和时间。deliveryId 只标识可移植交付制品，不得作为临床运行选择器；相同 deliveryId 与摘要的重复登记 MUST 幂等；相同 authorityId 和发布序号出现不同摘要 MUST 视为冲突。权威创建、实例冻结、接管、签发、轮换、吊销和拒绝事件 MUST 全部进入可追溯审计链。

#### Scenario: 重复登记同一已签包

- **WHEN** 活动发布实例以相同 deliveryId、发布序号和 manifest 摘要再次登记
- **THEN** 系统 SHALL 返回既有登记且不得创建第二条发布事实

#### Scenario: 发布序号摘要冲突

- **WHEN** 某 authorityId 的既有发布序号被提交为不同 manifest 摘要
- **THEN** 系统 MUST 拒绝登记并产生防重放冲突审计

#### Scenario: 查询权威当前状态

- **WHEN** 具备只读权限的操作者查询平台权威
- **THEN** 系统 SHALL 返回 authorityId、当前活动 issuerInstanceId、固定信任根指纹、有效 keyId、最新交接序号和最近包登记，且不得返回私钥材料

### Requirement: 保留从 134 到未来服务器的受控迁移边界

首发数据和包合同 SHALL 保留未来受控迁移所需的 authorityId、issuerInstanceId、keyId、发布序号、父摘要、密钥状态和吊销事实，但首次上线 MUST NOT 依赖完整自动化跨宿主交接状态机。未来迁移 MUST 采用冷迁移边界：停止新签发，备份并核对数据库、原件、审计、包注册表和信任材料，轮换或吊销旧发布密钥，再把新宿主登记为唯一 ACTIVE 发布实例；迁移过程中不得同时存在两个可被医院接受的新签发者。

#### Scenario: 未来执行受控冷迁移

- **WHEN** 目标实例以不同 issuerInstanceId 完成全量摘要核对，且旧发布密钥已轮换或吊销
- **THEN** 系统 SHALL 保持原 authorityId、信任根、发布序号和父摘要连续，并只登记目标实例为 ACTIVE

#### Scenario: 目标核对失败

- **WHEN** 数据库、原件、审计、包注册表或信任材料任一摘要与迁移清单不一致
- **THEN** 运维流程 MUST 终止切换并禁止目标实例签发；在旧密钥尚未吊销时可恢复 134 的原发布状态

#### Scenario: 交接后旧实例继续签发

- **WHEN** 已完成迁移的 134 尝试用已吊销 keyId 或非活动 issuerInstanceId 登记更高序号包
- **THEN** 系统 MUST 拒绝签发和登记，并保留已激活的未来服务器为唯一活动发布者

### Requirement: 医院只读消费且不得反写平台主源

医院 SHALL 只读消费由 authorityId 签发的平台标准资产。医院本地修改 MUST 在客户租户内形成机构覆盖或机构新增资产，并保持与 t-1 平台主源、权威包注册表和签发密钥物理及逻辑隔离；任何医院接口、同步任务或离线回传 MUST NOT 把本地覆盖、患者运行事实或审核结果写回平台主源或伪装成平台签发包。

#### Scenario: 医院创建本地覆盖

- **WHEN** 医院基于某个平台稳定资产身份创建本地修改
- **THEN** 系统 SHALL 将修改保存为该医院租户的覆盖版本，平台 t-1 原版本、authorityId 和包登记保持不变

#### Scenario: 医院尝试反写平台

- **WHEN** 医院实例尝试以平台 authorityId 签发包或向 t-1 写入本地覆盖
- **THEN** 系统 MUST 拒绝操作并记录租户越界审计

### Requirement: 权威断连时的诚实本地运行

医院运行 SHALL 不依赖 134 或未来权威宿主实时在线。权威断连时，医院 MUST 继续使用本地最后一个已验证并激活的机构生效版本完成无模型 B0 主链，使用本地最后接受的信任与吊销检查点校验离线包，并把平台同步状态诚实标记为 NOT_CONNECTED 或 NOT_SYNCED；系统 MUST NOT 伪造最新状态、自动信任未知轮换或设置未经产品定义的联网许可闸门。

#### Scenario: 医院与 134 长时间断网

- **WHEN** 医院无法连接当前活动发布实例
- **THEN** 临床服务 SHALL 继续由服务端解析本地当前机构生效版本，同时平台同步状态返回 NOT_CONNECTED 或 NOT_SYNCED

#### Scenario: 断网导入可验证完整包

- **WHEN** 医院断网但离线介质中的包可从本地固定信任检查点验证出完整授权链
- **THEN** 系统 SHALL 允许其进入统一离线预检和导入流程，不要求回连平台

#### Scenario: 断网时无法证明新信任链

- **WHEN** 离线包要求一个本地未知且无法由既有信任检查点验证的根或发布密钥
- **THEN** 系统 MUST 保留当前生效版本并返回 NOT_SYNCED，不得声称该包可信或平台状态最新
