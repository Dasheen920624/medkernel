import { useState } from "react";
import { PageShell } from "@/shared/ui/PageShell";
import {
  useSecurityProfile,
  useUserRoleAssignments,
  useCreateUserRoleAssignment,
  useDeleteUserRoleAssignment,
  usePlatformCredentials,
  useCreateMember,
  useResetMemberPassword,
  useSetCredentialStatus,
  useTenants,
  useProvisionTenant,
} from "@/shared/api/hooks";
import { ROLE_OPTIONS, SCOPE_LEVEL_OPTIONS } from "@/shared/config/roleCatalog";
import { getApiErrorMessage } from "@/shared/api/errors";
import styles from "./Compliance.module.css";

export default function AdminUsers() {
  const { data: securityProfile } = useSecurityProfile();
  const { data: assignments, isLoading, refetch } = useUserRoleAssignments();
  const createMutation = useCreateUserRoleAssignment();
  const deleteMutation = useDeleteUserRoleAssignment();

  // 成员账号（凭证）管理
  const { data: credentials, refetch: refetchCredentials } = usePlatformCredentials();
  const createMemberMutation = useCreateMember();
  const resetPwdMutation = useResetMemberPassword();
  const setStatusMutation = useSetCredentialStatus();
  const [showMemberForm, setShowMemberForm] = useState(false);
  const [memberUsername, setMemberUsername] = useState("");
  const [memberRole, setMemberRole] = useState("doctor");
  const [memberInitPwd, setMemberInitPwd] = useState("");

  // 平台租户开通（平台管理员）
  const { data: tenants, refetch: refetchTenants } = useTenants();
  const provisionTenantMutation = useProvisionTenant();
  const [showTenantForm, setShowTenantForm] = useState(false);
  const [newTenantId, setNewTenantId] = useState("");
  const [newTenantName, setNewTenantName] = useState("");
  const [newTenantAdmin, setNewTenantAdmin] = useState("");

  // 表单状态
  const [userId, setUserId] = useState("");
  const [roleCode, setRoleCode] = useState("doctor");
  const [scopeLevel, setScopeLevel] = useState("TENANT");
  const [scopeCode, setScopeCode] = useState("");
  const [showAddForm, setShowAddForm] = useState(false);
  const [message, setMessage] = useState<{ text: string; type: "success" | "error" } | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!userId.trim()) {
      setMessage({ text: "用户ID不能为空", type: "error" });
      return;
    }
    const normalizedScopeCode = scopeCode.trim() || securityProfile?.dataScope?.tenantId;
    if (!normalizedScopeCode) {
      setMessage({ text: "作用域编码不能为空，请输入当前租户或组织编码", type: "error" });
      return;
    }
    try {
      await createMutation.mutateAsync({
        userId: userId.trim(),
        roleCode,
        scopeLevel,
        scopeCode: normalizedScopeCode,
      });
      setMessage({ text: "角色范围分配成功！", type: "success" });
      setUserId("");
      setScopeCode("");
      refetch();
      setTimeout(() => setMessage(null), 3000);
    } catch (err: unknown) {
      const errMsg = getApiErrorMessage(err, "操作失败");
      setMessage({ text: `绑定失败: ${errMsg}`, type: "error" });
    }
  };

  const handleDelete = async (id: number) => {
    // eslint-disable-next-line no-alert
    if (!window.confirm("确定要移去/解除该用户的角色绑定关系吗？")) {
      return;
    }
    try {
      await deleteMutation.mutateAsync(id);
      setMessage({ text: "角色解绑成功！", type: "success" });
      refetch();
      setTimeout(() => setMessage(null), 3000);
    } catch (err: unknown) {
      const errMsg = getApiErrorMessage(err, "解绑失败");
      setMessage({ text: `操作失败: ${errMsg}`, type: "error" });
    }
  };

  const handleCreateMember = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!memberUsername.trim()) {
      setMessage({ text: "登录名不能为空", type: "error" });
      return;
    }
    try {
      const res = await createMemberMutation.mutateAsync({
        username: memberUsername.trim(),
        roleCode: memberRole,
        initialPassword: memberInitPwd.trim() || undefined,
      });
      setMessage({
        text: res.tempPassword
          ? `成员 ${res.username} 已开通，临时密码：${res.tempPassword}（请转交本人并提示首登改密）`
          : `成员 ${res.username} 已开通（使用所设初始密码，须首登改密）`,
        type: "success",
      });
      setMemberUsername("");
      setMemberInitPwd("");
      refetchCredentials();
      refetch();
    } catch (err: unknown) {
      const errMsg = getApiErrorMessage(err, "开通失败");
      setMessage({ text: `开通成员失败: ${errMsg}`, type: "error" });
    }
  };

  const handleResetPassword = async (uid: string) => {
    try {
      const res = await resetPwdMutation.mutateAsync(uid);
      setMessage({
        text: `已重置 ${uid} 的密码，临时密码：${res.tempPassword}（请转交本人并提示首登改密）`,
        type: "success",
      });
      refetchCredentials();
    } catch (err: unknown) {
      const errMsg = getApiErrorMessage(err, "重置失败");
      setMessage({ text: `重置密码失败: ${errMsg}`, type: "error" });
    }
  };

  const handleToggleStatus = async (uid: string, currentStatus: string) => {
    const next = currentStatus === "ACTIVE" ? "DISABLED" : "ACTIVE";
    try {
      await setStatusMutation.mutateAsync({ userId: uid, status: next });
      setMessage({ text: `账号 ${uid} 已${next === "ACTIVE" ? "启用" : "停用"}`, type: "success" });
      refetchCredentials();
    } catch (err: unknown) {
      const errMsg = getApiErrorMessage(err, "操作失败");
      setMessage({ text: `状态更新失败: ${errMsg}`, type: "error" });
    }
  };

  const handleProvisionTenant = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newTenantId.trim() || !newTenantName.trim() || !newTenantAdmin.trim()) {
      setMessage({ text: "租户标识、租户名称、管理员登录名均不能为空", type: "error" });
      return;
    }
    try {
      const res = await provisionTenantMutation.mutateAsync({
        tenantId: newTenantId.trim(),
        tenantName: newTenantName.trim(),
        adminUsername: newTenantAdmin.trim(),
      });
      setMessage({
        text: `租户 ${res.tenantId} 已开通，管理员 ${res.adminUsername} 临时密码：${res.tempPassword}（登录时租户填 ${res.tenantId}，须首登改密）`,
        type: "success",
      });
      setNewTenantId("");
      setNewTenantName("");
      setNewTenantAdmin("");
      refetchTenants();
    } catch (err: unknown) {
      const errMsg = getApiErrorMessage(err, "开通失败");
      setMessage({ text: `开通租户失败: ${errMsg}`, type: "error" });
    }
  };

  const credStatusLabel = (statusCode: string) => {
    if (statusCode === "ACTIVE") return "运行中";
    if (statusCode === "DISABLED") return "已停用";
    return statusCode;
  };

  const getRoleName = (code: string) => {
    const found = ROLE_OPTIONS.find((r) => r.code === code);
    return found ? found.name : code;
  };

  const getStats = () => {
    const list = assignments || [];
    const adminCount = list.filter((a) => a.roleCode.includes("admin")).length;
    const deptCount = list.filter((a) => a.scopeLevel === "DEPARTMENT").length;
    return {
      total: list.length,
      adminCount,
      deptCount,
    };
  };

  const stats = getStats();

  return (
    <PageShell
      title="用户与角色数据范围管理"
      description="支撑 GA-SVC-COMPLIANCE-01。物理绑定医院员工身份、角色权限及多维组织数据隔离范围，操作实时留痕。"
    >
      <div className={styles.container}>
        {/* 指标看板 */}
        <div className={styles.grid}>
          <div className={styles.card}>
            <div className={styles.description}>当前已绑定关系数</div>
            <div className={`${styles.title} ${styles.fontSize28}`}>
              {isLoading ? "..." : stats.total} 个
            </div>
          </div>
          <div className={styles.card}>
            <div className={styles.description}>高层管理员级授权</div>
            <div className={`${styles.title} ${styles.fontSize28}`}>
              {isLoading ? "..." : stats.adminCount} 个
            </div>
          </div>
          <div className={styles.card}>
            <div className={styles.description}>科室与专科级细粒度授权</div>
            <div className={`${styles.title} ${styles.fontSize28}`}>
              {isLoading ? "..." : stats.deptCount} 个
            </div>
          </div>
        </div>

        {/* 提示信息 */}
        {message && (
          <div className={message.type === "success" ? styles.alertSuccess : styles.alertError}>
            {message.text}
          </div>
        )}

        {/* 平台租户开通卡片（平台管理员：建新医院租户 + 首个管理员） */}
        <div className={styles.card}>
          <div className={styles.flexBetween}>
            <div className={styles.title}>平台租户开通（新建医院租户）</div>
            <button
              onClick={() => setShowTenantForm(!showTenantForm)}
              className={styles.btnPrimary}
            >
              {showTenantForm ? "收起开通面板" : "开通新租户"}
            </button>
          </div>

          {showTenantForm && (
            <form onSubmit={handleProvisionTenant}>
              <div className={styles.grid}>
                <div className={styles.formGroup}>
                  <label className={styles.formLabel}>租户标识（小写字母/数字/连字符）</label>
                  <input
                    type="text"
                    value={newTenantId}
                    onChange={(e) => setNewTenantId(e.target.value)}
                    placeholder="如 t-renmin"
                    className={styles.formInput}
                  />
                </div>
                <div className={styles.formGroup}>
                  <label className={styles.formLabel}>租户名称</label>
                  <input
                    type="text"
                    value={newTenantName}
                    onChange={(e) => setNewTenantName(e.target.value)}
                    placeholder="如 人民医院"
                    className={styles.formInput}
                  />
                </div>
                <div className={styles.formGroup}>
                  <label className={styles.formLabel}>首个管理员登录名</label>
                  <input
                    type="text"
                    value={newTenantAdmin}
                    onChange={(e) => setNewTenantAdmin(e.target.value)}
                    placeholder="如 renmin-admin"
                    className={styles.formInput}
                  />
                </div>
              </div>
              <div className={styles.btnGroup}>
                <button
                  type="submit"
                  disabled={provisionTenantMutation.isPending}
                  className={styles.btnPrimary}
                >
                  {provisionTenantMutation.isPending ? "正在开通..." : "确认开通租户"}
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setShowTenantForm(false);
                    setMessage(null);
                  }}
                  className={styles.btnPrimary}
                >
                  取消
                </button>
              </div>
            </form>
          )}

          {!tenants || tenants.length === 0 ? (
            <div className={styles.description}>暂无租户记录（需平台管理员权限查看/开通）。</div>
          ) : (
            <div className={styles.tableWrap}>
              <table className={styles.table}>
                <thead>
                  <tr>
                    <th>租户标识</th>
                    <th>租户名称</th>
                    <th>状态</th>
                  </tr>
                </thead>
                <tbody>
                  {tenants.map((t) => (
                    <tr key={t.tenantId}>
                      <td className={styles.fontMonospace}>{t.tenantId}</td>
                      <td className={styles.fontWeight600}>{t.name}</td>
                      <td>
                        <span
                          className={
                            t.status === "ACTIVE" ? styles.badgeActive : styles.badgeInactive
                          }
                        >
                          {t.status}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* 成员账号管理卡片（开通登录账号 / 重置密码 / 启停） */}
        <div className={styles.card}>
          <div className={styles.flexBetween}>
            <div className={styles.title}>成员账号管理（开通登录账号）</div>
            <button
              onClick={() => setShowMemberForm(!showMemberForm)}
              className={styles.btnPrimary}
            >
              {showMemberForm ? "收起开通面板" : "开通新成员"}
            </button>
          </div>

          {showMemberForm && (
            <form onSubmit={handleCreateMember}>
              <div className={styles.grid}>
                <div className={styles.formGroup}>
                  <label className={styles.formLabel}>登录名 / 工号</label>
                  <input
                    type="text"
                    value={memberUsername}
                    onChange={(e) => setMemberUsername(e.target.value)}
                    placeholder="如 drwang"
                    className={styles.formInput}
                  />
                </div>
                <div className={styles.formGroup}>
                  <label className={styles.formLabel}>初始角色</label>
                  <select
                    value={memberRole}
                    onChange={(e) => setMemberRole(e.target.value)}
                    className={styles.formInput}
                  >
                    {ROLE_OPTIONS.map((role) => (
                      <option key={role.code} value={role.code}>
                        {role.name} ({role.code})
                      </option>
                    ))}
                  </select>
                </div>
                <div className={styles.formGroup}>
                  <label className={styles.formLabel}>初始密码（留空自动生成临时密码）</label>
                  <input
                    type="text"
                    value={memberInitPwd}
                    onChange={(e) => setMemberInitPwd(e.target.value)}
                    placeholder="留空则系统生成并一次性返回"
                    className={styles.formInput}
                  />
                </div>
              </div>
              <div className={styles.btnGroup}>
                <button
                  type="submit"
                  disabled={createMemberMutation.isPending}
                  className={styles.btnPrimary}
                >
                  {createMemberMutation.isPending ? "正在开通..." : "确认开通成员"}
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setShowMemberForm(false);
                    setMessage(null);
                  }}
                  className={styles.btnPrimary}
                >
                  取消
                </button>
              </div>
            </form>
          )}

          {!credentials || credentials.length === 0 ? (
            <div className={styles.description}>
              当前租户暂无平台自建账号，可通过上方面板开通成员登录账号。
            </div>
          ) : (
            <div className={styles.tableWrap}>
              <table className={styles.table}>
                <thead>
                  <tr>
                    <th>登录名</th>
                    <th>用户标识</th>
                    <th>状态</th>
                    <th>须改密</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  {credentials.map((c) => (
                    <tr key={c.userId}>
                      <td className={styles.fontWeight600}>{c.username}</td>
                      <td className={styles.fontMonospace}>{c.userId}</td>
                      <td>
                        <span
                          className={
                            c.status === "ACTIVE" ? styles.badgeActive : styles.badgeInactive
                          }
                        >
                          {credStatusLabel(c.status)}
                        </span>
                      </td>
                      <td>
                        {c.mustChangePwd ? <span className={styles.scopeTag}>待改密</span> : "—"}
                      </td>
                      <td>
                        <button
                          onClick={() => handleResetPassword(c.userId)}
                          disabled={resetPwdMutation.isPending}
                          className={styles.btnPrimary}
                        >
                          重置密码
                        </button>{" "}
                        <button
                          onClick={() => handleToggleStatus(c.userId, c.status)}
                          disabled={setStatusMutation.isPending}
                          className={styles.btnDanger}
                        >
                          {c.status === "ACTIVE" ? "停用" : "启用"}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* 新增绑定卡片 */}
        <div className={styles.card}>
          <div className={styles.flexBetween}>
            <div className={styles.title}>用户角色与数据隔离范围配置</div>
            <button onClick={() => setShowAddForm(!showAddForm)} className={styles.btnPrimary}>
              {showAddForm ? "收起配置面板" : "新增角色分配关系"}
            </button>
          </div>

          {showAddForm && (
            <form onSubmit={handleSubmit}>
              <div className={styles.grid}>
                <div className={styles.formGroup}>
                  <label className={styles.formLabel}>User ID / 账号标识</label>
                  <input
                    type="text"
                    value={userId}
                    onChange={(e) => setUserId(e.target.value)}
                    placeholder="请输入用户ID，如 doctor-1"
                    className={styles.formInput}
                  />
                </div>

                <div className={styles.formGroup}>
                  <label className={styles.formLabel}>系统核心角色</label>
                  <select
                    value={roleCode}
                    onChange={(e) => setRoleCode(e.target.value)}
                    className={styles.formInput}
                  >
                    {ROLE_OPTIONS.map((role) => (
                      <option key={role.code} value={role.code}>
                        {role.name} ({role.code})
                      </option>
                    ))}
                  </select>
                </div>

                <div className={styles.formGroup}>
                  <label className={styles.formLabel}>作用域级别</label>
                  <select
                    value={scopeLevel}
                    onChange={(e) => setScopeLevel(e.target.value)}
                    className={styles.formInput}
                  >
                    {SCOPE_LEVEL_OPTIONS.map((level) => (
                      <option key={level.code} value={level.code}>
                        {level.name}
                      </option>
                    ))}
                  </select>
                </div>

                <div className={styles.formGroup}>
                  <label className={styles.formLabel}>作用域编码</label>
                  <input
                    type="text"
                    value={scopeCode}
                    onChange={(e) => setScopeCode(e.target.value)}
                    placeholder="不填时使用当前登录画像的租户编码"
                    className={styles.formInput}
                  />
                </div>
              </div>

              <div className={styles.btnGroup}>
                <button
                  type="submit"
                  disabled={createMutation.isPending}
                  className={styles.btnPrimary}
                >
                  {createMutation.isPending ? "正在提交..." : "确认并物理入库保存"}
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setShowAddForm(false);
                    setMessage(null);
                  }}
                  className={styles.btnPrimary}
                >
                  取消
                </button>
              </div>
            </form>
          )}
        </div>

        {/* 现有绑定台账列表 */}
        <div className={styles.card}>
          <div className={styles.title}>用户角色分配台账</div>
          {isLoading && <div>正在加载安全底座数据...</div>}
          {!isLoading && (!assignments || assignments.length === 0) && (
            <div className={styles.description}>
              当前租户暂无用户分配记录，可通过上方配置面板进行物理授权绑定。
            </div>
          )}
          {!isLoading && assignments && assignments.length > 0 && (
            <div className={styles.tableWrap}>
              <table className={styles.table}>
                <thead>
                  <tr>
                    <th>记录ID</th>
                    <th>租户代码</th>
                    <th>用户ID</th>
                    <th>绑定角色</th>
                    <th>隔离作用域级别</th>
                    <th>隔离作用域编码</th>
                    <th>运行状态</th>
                    <th>创建者</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  {assignments.map((item) => (
                    <tr key={item.id}>
                      <td className={styles.fontMonospace}>{item.id}</td>
                      <td>{item.tenantId}</td>
                      <td className={styles.fontWeight600}>{item.userId}</td>
                      <td>
                        <span className={styles.roleTag}>{getRoleName(item.roleCode)}</span>
                      </td>
                      <td>
                        <span className={styles.scopeTag}>{item.scopeLevel}</span>
                      </td>
                      <td className={styles.fontMonospace}>{item.scopeCode}</td>
                      <td>
                        <span
                          className={
                            item.activeFlag === "Y" ? styles.badgeActive : styles.badgeInactive
                          }
                        >
                          {item.activeFlag === "Y" ? "激活运行中" : "已停用"}
                        </span>
                      </td>
                      <td>{item.createdBy}</td>
                      <td>
                        <button
                          onClick={() => item.id && handleDelete(item.id)}
                          disabled={deleteMutation.isPending}
                          className={styles.btnDanger}
                        >
                          解除绑定
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </PageShell>
  );
}
