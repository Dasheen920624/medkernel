# MedKernel 单机部署与一键发布手册

适用场景：一台 Linux 服务器承载 Nginx、MedKernel 后端、前端静态资源，并连接本机 PostgreSQL 或现场 Oracle。当前腾讯云轻量服务器采用 PostgreSQL + HTTPS 443；老医院现场仍可通过参数覆盖为 Oracle + HTTPS 8443。

真实口令、JWT 密钥、集成凭证密钥、字段级加密密钥、bootstrap 令牌、数据库密码不写入仓库。它们只存在服务器 `/zoesoft/medkernel/conf/medkernel.env`、`bootstrap-init-token.txt` 或安全凭据清单中。

## 目录内容

```text
deploy/onprem/
├── ollama/
│   └── MedKernel.Qwen25-1.5B.Modelfile # 可重放的本地知识草案模型定义
├── mk-publish.ps1                 # Windows / PowerShell 一键构建、上传、发布
├── mk-publish.sh                  # macOS / Linux 一键构建、上传、发布
├── medkernel-deploy.sh            # 服务器端发布/回滚/状态脚本
├── medkernel-fresh-deploy.sh      # PostgreSQL 备份、隔离恢复、清库和 V1 全新部署
├── medkernel-post-rehearsal-verify.sh # 六阶段演练后的重启、数据库和恢复验收
├── purge-schema.sql               # Oracle 重建/灾备清库脚本
├── tests/
│   ├── validate-medkernel-fresh-deploy.sh
│   ├── validate-medkernel-post-rehearsal-verify.sh
│   └── validate-ollama-model.sh    # 本地模型定义安全与确定性门禁
└── templates/
    ├── medkernel.service          # systemd 单元模板
    ├── medkernel.nginx.conf       # nginx HTTPS 模板
    └── medkernel.env.example      # 环境变量模板，无真实密钥
```

## 日常更新

日常更新只替换程序包。数据库结构由应用启动时 Flyway 自动前向迁移；发布脚本不会清库、不会直接改业务数据。

### Windows

推荐使用 SSH 密钥：

```powershell
cd D:\vibeCoding\codex\medkernel\deploy\onprem
.\mk-publish.ps1 -KeyFile C:\tmp\medkernel_deploy_ed25519
```

常用变体：

```powershell
.\mk-publish.ps1 -Frontend -KeyFile C:\tmp\medkernel_deploy_ed25519
.\mk-publish.ps1 -Backend -KeyFile C:\tmp\medkernel_deploy_ed25519
.\mk-publish.ps1 -StageOnly -KeyFile C:\tmp\medkernel_deploy_ed25519
.\mk-publish.ps1 -Server 192.168.8.191 -User root -KeyFile C:\path\to\old-site-key
```

不传 `-KeyFile` 时，脚本优先使用 `Posh-SSH` 密码登录；没有 `Posh-SSH` 时退回系统 `ssh/scp` 交互认证。不要把密码写入脚本或提交到仓库。

### macOS / Linux

```bash
cd /path/to/medkernel/deploy/onprem
bash ./mk-publish.sh --key-file ~/.ssh/medkernel_deploy
```

常用变体：

```bash
bash ./mk-publish.sh --frontend --key-file ~/.ssh/medkernel_deploy
bash ./mk-publish.sh --backend --key-file ~/.ssh/medkernel_deploy
bash ./mk-publish.sh --stage-only --key-file ~/.ssh/medkernel_deploy
bash ./mk-publish.sh --server 192.168.8.191 --user root --key-file ~/.ssh/old-site-key
```

发布入口只接受当前干净工作树的完整 40 位 `HEAD` 哈希，并始终从该提交重新构建所选前后端；禁止用旧 `target`/`dist` 冒充新版本。完整测试与门禁须在调用发布入口前单独通过。

### 服务器端手动发布

如果只想上传包后在服务器执行：

```bash
sudo medkernel-deploy
sudo medkernel-deploy --status
sudo medkernel-deploy --rollback
sudo medkernel-deploy --rollback /zoesoft/medkernel/backups/deploy-YYYYmmdd-HHMMSS
```

约定上传目录：

```text
/zoesoft/medkernel/incoming/
```

后端 jar 可任意命名为 `*.jar`；前端包命名为 `dist.tar.gz`，内部必须包含 `dist/index.html`。

## 服务器运行结构

| 项 | 当前约定 |
|---|---|
| 部署根目录 | `/zoesoft/medkernel` |
| 后端 jar | `/zoesoft/medkernel/lib/medkernel.jar` |
| 前端 dist | `/zoesoft/medkernel/frontend/dist` |
| 环境变量 | `/zoesoft/medkernel/conf/medkernel.env` |
| 发布脚本 | `/zoesoft/medkernel/bin/medkernel-deploy.sh`，软链 `/usr/local/bin/medkernel-deploy` |
| 服务托管 | `systemd`：`medkernel.service` |
| 反向代理 | Nginx：`/etc/nginx/conf.d/medkernel.conf` |
| 应用端口 | `127.0.0.1:18080`，不对公网开放 |
| PostgreSQL | `127.0.0.1:5432`，不对公网开放 |
| HTTPS | 当前腾讯云使用 `443`；老现场可保留 `8443` |
| 备份目录 | `/zoesoft/medkernel/backups/deploy-*` |
| 发布日志 | `/zoesoft/medkernel/logs/deploy.log` |

## Ollama 本地知识草案模型

本地模型只生成待责任人确认的知识草案，不得自动确认或发布医学知识。模型定义固定基础模型、采样参数和安全约束；真实权重 digest、provider 配置和版本三元组仍须在目标环境留证，不能只凭模型标签宣告一致。

提交或部署前先执行定义门禁：

```bash
bash deploy/onprem/tests/validate-ollama-model.sh
```

目标服务器安装受控 Ollama 后，以服务用户完成模型准备：

```bash
ollama pull qwen2.5:1.5b
ollama create medkernel-qwen25:1.5b-v1 \
  -f deploy/onprem/ollama/MedKernel.Qwen25-1.5B.Modelfile
ollama show medkernel-qwen25:1.5b-v1 --modelfile
```

Ollama 仅监听回环地址，模型目录放在受管数据目录。应用侧 provider 完成真实健康检查后仍保持停用，直至当前启用医学基准集评测、能力策略、版本三元组和出域策略全部通过技术门禁。

## 首次部署要点

1. 建运行用户和目录：

```bash
useradd -r -m -d /zoesoft/medkernel medkernel
mkdir -p /zoesoft/medkernel/{lib,frontend,conf,logs,tmp,bin,incoming,backups,nginx/ssl,jre}
chown -R medkernel:medkernel /zoesoft/medkernel
```

2. 安装 JRE 21、PostgreSQL/Nginx，或接入现场 Oracle。
3. 复制 `medkernel-deploy.sh` 到 `/zoesoft/medkernel/bin/` 并建立软链：

```bash
install -m 755 deploy/onprem/medkernel-deploy.sh /zoesoft/medkernel/bin/medkernel-deploy.sh
ln -sf /zoesoft/medkernel/bin/medkernel-deploy.sh /usr/local/bin/medkernel-deploy
```

4. 按 `templates/medkernel.env.example` 创建 `/zoesoft/medkernel/conf/medkernel.env`，填真实数据库连接和随机密钥，权限设为 `600`。
5. 安装 systemd / Nginx 模板，准备证书：

```bash
cp deploy/onprem/templates/medkernel.service /etc/systemd/system/medkernel.service
cp deploy/onprem/templates/medkernel.nginx.conf /etc/nginx/conf.d/medkernel.conf
systemctl daemon-reload
nginx -t
```

6. 上传首次 jar/dist 后执行：

```bash
sudo medkernel-deploy
systemctl enable medkernel nginx postgresql
```

## 首次接管

当前腾讯云：

```text
https://193.112.107.134/bootstrap
```

接管码只在服务器读取：

```bash
cat /zoesoft/medkernel/conf/bootstrap-init-token.txt
```

不要把接管码发到聊天工具或群里。自签 HTTPS 证书会触发浏览器安全提示，正式环境应绑定域名并替换为可信证书。

## 状态检查

```bash
sudo medkernel-deploy --status
systemctl is-active medkernel nginx postgresql
curl -s http://127.0.0.1:18080/medkernel/actuator/health/readiness
curl -ks https://127.0.0.1/medkernel/actuator/health/readiness
curl -ks https://127.0.0.1/medkernel/api/v1/auth/login-tenants
```

公网只需要开放 `443/tcp` 给系统访问。`22/tcp` 仅用于运维和数据库 SSH 隧道，建议限制来源 IP；`5432/tcp` 和 `18080/tcp` 不要开放公网。

## 回滚

```bash
sudo medkernel-deploy --rollback
sudo medkernel-deploy --rollback /zoesoft/medkernel/backups/deploy-YYYYmmdd-HHMMSS
```

回滚只恢复 jar 和前端 dist。已经执行成功的 Flyway 数据库迁移不会被自动回退；如果新版本迁移失败，需要查看 `/zoesoft/medkernel/logs/stdout.log` 并按 Flyway 修复流程处理。

## Oracle 重建/清库

只适用于老 Oracle 现场或需要重建 Oracle schema 的上线。该操作会删除 `MEDKERNEL` schema 下对象，必须先备份并单独确认。

```bash
systemctl stop medkernel
sqlplus medkernel/<密码>@//<host>:1521/<service> @deploy/onprem/purge-schema.sql
systemctl start medkernel
```

当前腾讯云 PostgreSQL 上线不使用该 SQL；PostgreSQL 数据库初始化由服务器首次部署流程和 Flyway 完成。

## PostgreSQL 全新清库发布

只在产品明确要求丢弃旧运行数据、从当前迁移基线重新初始化时使用。脚本先保存数据库、程序、前端、配置与服务文件，并把数据库备份恢复到隔离临时库验证；只有恢复成功后才停服清库。候选必须显式指定，清库后禁用旧程序自动回滚。

先单独预检生产运行环境。环境文件权限必须为 `600`；JWT、集成凭证、D3/D4 字段级加密和 bootstrap 四类密钥必须各自只配置一次、长度不少于 32 位且不能保留模板占位符。校验只报告键名和错误原因，不输出密钥值：

```bash
sudo bash deploy/onprem/medkernel-fresh-deploy.sh --validate-environment-only
```

预检通过后再执行全新发布：

```bash
BUSINESS_TABLES="$(node -e 'const schema=require("./medkernel-backend/src/main/resources/db/schema/medkernel.schema.json"); console.log(schema.tables.length)')"

sudo bash deploy/onprem/medkernel-fresh-deploy.sh \
  --jar /path/to/medkernel.jar \
  --frontend /path/to/dist.tar.gz \
  --service-unit /path/to/medkernel.service \
  --deploy-script /path/to/medkernel-deploy.sh \
  --nginx-conf /path/to/medkernel.nginx.conf \
  --source <40位提交哈希> \
  --expected-host <目标机hostname> \
  --external-base-url https://<正式域名>/medkernel \
  --expected-flyway-version 1 \
  --expected-business-tables "$BUSINESS_TABLES" \
  --confirm-fresh \
  --confirm-database medkernel
```

如果本轮同时要求清理历史发布备份，必须额外给出双重确认；脚本仍会保留本次清库前备份与隔离恢复证据：

```bash
  --prune-old-backups --confirm-prune-backups
```

该流程不会删除 `/zoesoft/medkernel/conf`，不会输出环境文件中的密钥、接管码或数据库口令。失败时不得把旧程序包自动恢复到已清空的新数据库；应根据本次备份目录中的 `evidence/` 和服务日志定位问题。

## 全功能与全知识上线演练

清库部署完成后，使用 `scripts/release/full-system-rehearsal.mjs` 依次执行账号接管、真实
Provider、沙盘、11 域全知识、Provider 降级恢复和全量浏览器旅程。演练通过后必须执行：

```bash
BUSINESS_TABLES="$(node -e 'const schema=require("./medkernel-backend/src/main/resources/db/schema/medkernel.schema.json"); console.log(schema.tables.length)')"

sudo bash deploy/onprem/medkernel-post-rehearsal-verify.sh \
  --expected-host <目标机hostname> \
  --expected-source <40位提交哈希> \
  --external-base-url https://<正式域名>/medkernel \
  --provider-code ollama-launch \
  --expected-business-tables "$BUSINESS_TABLES" \
  --expected-flyway-version 1 \
  --confirm-restart \
  --confirm-database medkernel
```

该入口会严格验证 TLS、服务重启、四职责、全知识、模型生产、沙盘、审计和演练后备份恢复。
完整环境变量、阶段定义和证据边界见 [部署与上线演练](../../docs/DEPLOYMENT_AND_REHEARSAL.md)。
