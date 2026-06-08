# Copy-on-write 覆盖与传播（copy-on-write-inheritance）

## ADDED Requirements

### Requirement: 仅按需覆盖
租户/机构 SHALL 仅对需要改动的资产创建覆盖记录（`InheritanceOverride`），覆盖 SHALL 只存储相对平台/上级的差异；未覆盖资产 SHALL 恒指向平台权威，不得整包复制。

#### Scenario: 定制单条规则不影响其余
- **WHEN** FACILITY 仅对身份 A 创建 REPLACE 覆盖
- **THEN** 该机构对身份 B、C… 的解析仍返回平台版本，仅 A 返回机构版本

### Requirement: 三种覆盖模式
系统 SHALL 支持覆盖模式：REPLACE（以自有版本替换平台同身份资产）、DISABLE（在作用域停用平台资产）、ADD（新增平台不存在的独有资产，分配新 `asset_identity` 并归属该 org_path）。

#### Scenario: 停用不适用的平台资产
- **WHEN** 基层 FACILITY 对身份 A 创建 DISABLE 覆盖
- **THEN** 该机构解析有效集时 A 被剔除（标记 DISABLED）

#### Scenario: 新增院内独有资产
- **WHEN** FACILITY ADD 一个本机构独有路径（新身份 X）
- **THEN** X 在该机构作用域可解析，平台与其他租户不可见

### Requirement: 传播语义（复用 vs 独有）
每个覆盖 SHALL 声明 `propagation`：INHERITABLE（对本节点及所有下级生效，直到下级进一步覆盖）或 EXCLUSIVE（仅本节点生效，下级回退上一层适用版本）。默认 SHALL 为 INHERITABLE。

#### Scenario: 区域覆盖被机构复用
- **WHEN** REGION 对身份 A 创建 REPLACE 覆盖且 propagation=INHERITABLE，机构 B 未进一步覆盖
- **THEN** `resolve(A, B)` 返回区域版本

#### Scenario: 机构独有覆盖不下沉科室
- **WHEN** FACILITY 对身份 A 创建覆盖且 propagation=EXCLUSIVE
- **THEN** 其下科室 `resolve(A, 科室)` 返回平台/上级版本，而非机构版本

#### Scenario: 下级进一步覆盖优先
- **WHEN** REGION INHERITABLE 覆盖 A 为 v-region，科室对 A 再 REPLACE 为 v-dept
- **THEN** `resolve(A, 科室)` 返回 v-dept（最具体优先）
