# MedKernel 现场（单机 Oracle）部署与运维手册

> 适用：医院内网 **单机 + Oracle** 的现场部署（区别于 `deploy/docker/` 的容器化开发平台）。
> 当前现场：`192.168.8.191`（麒麟 Kylin V10），HTTPS `https://192.168.8.191:8443/`。
> **真实口令 / 密钥不写入本仓库**，见现场交接的安全凭据清单。

## 0. 本部署包内容

```text
deploy/onprem/
├── README.md                      # 本手册
├── mk-publish.ps1                 # 【本机】一键：构建→打包→上传→远程发布
├── medkernel-deploy.sh            # 【服务器】发布/回滚/状态（装到 /zoesoft/medkernel/bin/）
├── purge-schema.sql               # 首发/灾备：清空 MEDKERNEL schema（Flyway 随后重建）
└── templates/
    ├── medkernel.service          # systemd 单元模板
    ├── medkernel.nginx.conf       # nginx HTTPS 8443 站点模板
    └── medkernel.env.example      # 运行环境变量模板（占位，无真实密钥）
```

---

## 1. 日常发布更新（最常用）

数据库结构升级由应用启动时 **Flyway 自动前向迁移**，常规更新**不需要也不会**动数据库。

### 方式 A — 本机一键（推荐）

在开发/构建机（Windows + PowerShell 7）：

```powershell
cd deploy\onprem
.\mk-publish.ps1                 # 构建并发布前后端
.\mk-publish.ps1 -Backend        # 只后端
.\mk-publish.ps1 -Frontend       # 只前端
.\mk-publish.ps1 -SkipBuild      # 用已有产物直接发布
.\mk-publish.ps1 -StageOnly      # 只构建+上传，不触发发布
.\mk-publish.ps1 -RepoRoot D:\代码目录 -Source 9a1b2c3
```

它会：本机 `mvn`(JDK21) 出 jar、`npm` 出 dist 并打 `dist.tar.gz` → 上传到服务器 `incoming/` → 远程调用 `medkernel-deploy`（备份→替换→重启→健康检查→**失败自动回滚**）。

- 依赖：PowerShell 7、`Posh-SSH` 模块（`Install-Module Posh-SSH -Scope CurrentUser`）、JDK21、Maven、Node。
- 密码：优先 `-Password`，其次环境变量 `MEDKERNEL_DEPLOY_PASSWORD`，否则交互输入；生产建议配 SSH 密钥免密。
- 关键参数：`-Server`/`-User`/`-RepoRoot`/`-JavaHome`/`-MvnCmd`。

### 方式 B — 服务器端

把新后端 `*.jar`、前端 `dist.tar.gz`（内含 `dist/`）传到 `/zoesoft/medkernel/incoming/`，然后：

```bash
sudo medkernel-deploy                 # 自动取 incoming 下最新包发布
sudo medkernel-deploy --status        # 看当前版本/服务/健康/备份（只读，免 root）
sudo medkernel-deploy --rollback      # 回滚到最新一次备份
sudo medkernel-deploy --rollback <备份目录>
sudo medkernel-deploy --help          # 全部参数
# 微调：--no-restart / --skip-health / --no-rollback / --health-timeout N / --source <git短哈希>
```

发布前会自动备份到 `/zoesoft/medkernel/backups/deploy-<时间戳>/`；发布日志在 `/zoesoft/medkernel/logs/deploy.log`。

---

## 2. 运行结构（服务器拓扑）

| 项 | 值 |
|---|---|
| 部署根目录 | `/zoesoft/medkernel` |
| 后端 | Spring Boot fat jar `lib/medkernel.jar`（内置 ojdbc11），context-path `/medkernel`，端口 `18080` |
| 运行时 | 自带 `jre/`（Temurin 21）；服务用户 `medkernel` |
| 进程托管 | systemd `medkernel.service`（`enable` 开机自启；env 来自 `conf/medkernel.env`） |
| 前端 | SPA 静态文件 `frontend/dist`，由 nginx 直接服务 |
| 反向代理 | 系统 nginx，`/etc/nginx/conf.d/medkernel.conf`：**HTTPS 8443**（SPA + `/medkernel/`→`127.0.0.1:18080`），`8088`→301→`8443` |
| 证书 | `nginx/ssl/server.{crt,key}`（现场自签；可换正式证书）|
| 数据库 | Oracle `jdbc:oracle:thin:@//<host>:1521/<service>`，schema/用户 `MEDKERNEL`，方言 oracle，Flyway 启用 |
| 备份 | `backups/deploy-*`（每次发布前自动留底）|
| 暂存 | `incoming/`（上传新包处）、`tmp/`（解包暂存）|

> ⚠️ Oracle 实例可能与其它产品**共用**（如现场 `ZOECDSS` 同时有 `zoe-cdss`）。任何清库/维护**只能针对 `MEDKERNEL` 这个 schema**，绝不触碰实例内其它用户。

配置要点（`conf/medkernel.env`，详见 `templates/medkernel.env.example`）：
`SPRING_PROFILES_ACTIVE=govcloud` + `MEDKERNEL_GOV_DATABASE_DIALECT=oracle` → Flyway 跑 `db/migration/oracle`；`MEDKERNEL_GOV_DB_*` 配 Oracle 连接；`MEDKERNEL_AUTH_JWT_SECRET` / `MEDKERNEL_BOOTSTRAP_INIT_TOKEN` 为安全密钥；`JAVA_OPTS` 配 JVM。

---

## 3. 全新机器首次安装

1. **建用户与目录**：`useradd -r -m -d /zoesoft/medkernel medkernel`（或沿用现场约定）；建 `lib conf frontend logs tmp bin incoming backups nging/ssl`。
2. **放运行时**：把 Temurin 21 解到 `jre/`；把 `medkernel-deploy.sh` 放 `bin/` 并 `chmod 755`，软链 `ln -sf /zoesoft/medkernel/bin/medkernel-deploy.sh /usr/local/bin/medkernel-deploy`。
3. **配环境**：`cp templates/medkernel.env.example conf/medkernel.env`，填真实 Oracle 连接与随机密钥，`chmod 600 && chown medkernel conf/medkernel.env`。
4. **装 systemd / nginx**：`cp templates/medkernel.service /etc/systemd/system/`；`cp templates/medkernel.nginx.conf /etc/nginx/conf.d/medkernel.conf`；备好 `nginx/ssl/server.{crt,key}`。
5. **首发数据**：见下节「首次发布 / 清库重建」。
6. **放包并启动**：上传 jar/dist 到 `incoming/`，`sudo medkernel-deploy`；`systemctl enable medkernel`；`nginx -t && systemctl reload nginx`。

---

## 4. 首次发布 / 清库重建（当首次发布、生成初始化数据）

适用「全新首发」或「现场库需要清空重来」。**只动 MEDKERNEL schema**。

```bash
# 1) 停应用，释放连接、避免半迁移
systemctl stop medkernel

# 2) 清空 MEDKERNEL schema（medkernel 自身权限即可，无需 DBA）
#    任意能连库、装了 sqlplus 的机器执行：
sqlplus medkernel/<密码>@//<host>:1521/<service> @deploy/onprem/purge-schema.sql
#    期望末尾 OBJ_AFTER=0 / RB_AFTER=0

# 3) 启动应用：Flyway 从 V1 重建全部表 + 种子
systemctl start medkernel
```

启动后 Flyway 会建好全部表并写入**初始化种子**：内置超级管理员（5 维 RBAC，`user_role_assignment` 多条）、平台主租户 `t-1`、菜单权限目录、系统配置。
注意：超管**口令不预置**，需在 `/bootstrap` 首登设置（见下节），故 bootstrap 前 `platform_credential` 为空属正常。

> Windows 上 `sqlplus` 若报 `Error 46 / HTTP proxy`，先清空 `HTTP_PROXY`/`HTTPS_PROXY` 环境变量再执行。

---

## 5. 首次登录 / Bootstrap

1. 浏览器开 `https://<host>:8443/`（自签证书会提示一次，继续即可）。
2. 进入首次部署接管页 `/bootstrap`，输入 **bootstrap 令牌**（即 `conf/medkernel.env` 里的 `MEDKERNEL_BOOTSTRAP_INIT_TOKEN`，默认 TTL 1440 分钟）。
3. 设置内置超级管理员口令并绑定 MFA（支持内网离线认证器）。
4. 之后用超管登录。无客户/集团租户时登录页默认只显示唯一平台主租户 `t-1`。

**重新生成 bootstrap 令牌**（过期或需轮换）：改 `conf/medkernel.env` 的 `MEDKERNEL_BOOTSTRAP_INIT_TOKEN`（随机串，可 `openssl rand -base64 32 | tr '+/' '-_' | tr -d '='`），`systemctl restart medkernel`。

---

## 6. 回滚

```bash
sudo medkernel-deploy --rollback            # 回滚到最近一次备份
sudo medkernel-deploy --rollback /zoesoft/medkernel/backups/deploy-<时间戳>
```

回滚只还原**程序包**（jar + 前端）。⚠️ 若某版本的**数据库迁移**已执行，回滚程序不会回退库结构；迁移失败需人工 `flyway repair` 后再处理。

---

## 7. 健康检查与验证

```bash
# 服务 / 健康
systemctl status medkernel
curl -s http://127.0.0.1:18080/medkernel/actuator/health/readiness     # {"status":"UP"}
# 经 nginx 的 HTTPS
curl -sk https://127.0.0.1:8443/medkernel/actuator/health/readiness
curl -sk -o /dev/null -w '%{http_code}\n' https://127.0.0.1:8443/       # 200 (SPA)
curl -sk https://127.0.0.1:8443/medkernel/api/v1/auth/login-tenants     # 应含 t-1
# 日志
tail -n 100 /zoesoft/medkernel/logs/stdout.log
tail -n 50  /zoesoft/medkernel/logs/deploy.log
```

DB 侧（sqlplus）：`select count(*) from "flyway_schema_history";`（应等于迁移总数，当前 58），全部 `"success"=1`。

---

## 8. 常见故障排查

| 现象 | 原因 / 处理 |
|---|---|
| 服务崩溃重启循环；日志 `Found non-empty schema(s) "MEDKERNEL" but no schema history table` | schema 有残留对象但无 Flyway 历史（多为「删表未 PURGE」留下回收站对象 + 孤立 `ISEQ$$` 序列）。按 §4 用 `purge-schema.sql` 清空后再启动。 |
| `start request repeated too quickly` / 一直 failed | 崩溃循环耗尽 start-limit。`systemctl reset-failed medkernel` 后再 `start`（`medkernel-deploy` 已内置）。 |
| Windows `sqlplus` 报 `Error 46 / HTTP proxy` | 先清 `HTTP_PROXY`/`HTTPS_PROXY` 环境变量。 |
| PowerShell 里 `tar -z` 报 `gzip: command not found` | 用 Windows 自带 `System32\tar.exe`（bsdtar，内置 gzip）；`mk-publish.ps1` 已如此处理。 |
| 健康检查失败、自动回滚后仍不健康 | 多为数据库迁移失败：查 `stdout.log`，必要时 `flyway repair`；确认 Oracle 连通与 `MEDKERNEL_GOV_DB_*`。 |
| 前端更新后页面没变 | 浏览器缓存；`/assets/` 带长缓存，强刷或确认 `dist/index.html` 已更新。 |

---

## 9. 凭据与安全

- 真实口令/密钥**不入库**：Oracle 口令、服务器 SSH 口令、JWT 密钥、bootstrap 令牌均存于现场 `conf/medkernel.env`（600）与安全凭据清单，按现场交接。
- `conf/medkernel.env`、`bootstrap-init-token.txt` 权限 600、属主 `medkernel`。
- 首发/重置后建议轮换 `MEDKERNEL_AUTH_JWT_SECRET` 与 `MEDKERNEL_BOOTSTRAP_INIT_TOKEN`。
- 生产建议：SSH 改密钥登录、nginx 换正式证书、按需开启 `MEDKERNEL_BACKUP_ENABLED` 与定时备份。

---

## 10. 本机构建工具链（参考）

- 后端：JDK 21 + Maven。`mvn -f medkernel-backend/pom.xml "-Dmaven.test.skip=true" clean package` → `target/medkernel-backend-1.0.0-SNAPSHOT.jar`（fat jar，含 ojdbc11）。
- 前端：Node。`npm ci && npm run build` → `frontend/dist`；打包 `tar -czf dist.tar.gz -C frontend dist`（Windows 用 `System32\tar.exe`）。
- 这些步骤已被 `mk-publish.ps1` 串好；手动构建仅供排查参考。
