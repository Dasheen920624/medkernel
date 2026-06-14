# MedKernel MCP 服务（`@medkernel/mcp-server`）

把后端**受控工具**经 **MCP 协议（stdio JSON-RPC）**暴露给外部 Agent（DATASVC-01 FR-4）。
是 [AIK-STD-14](../docs/cards/wave2/AIK-STD-14.md) 第三方 Agent 协助知识生产的技术底座。**初版传输层骨架**。

## 治理边界（不绕治理）

- **经后端受控合同**：`tools/list` 取后端真实受控工具目录，`tools/call` 派发后端 `/tools/{name}:execute`。
- **不直连库、不读本地库连接串**：仅用 `MEDKERNEL_API_BASE` + `MEDKERNEL_API_TOKEN`。
- **不绕治理**：权限/脱敏/审计/降级全由后端裁决；工具执行失败以 MCP `isError` 结果返回结构化原因，不泄漏内部异常/SQL。
- **后端不可达诚实报错不伪造数据**（铁律 #1）。

## 配置

| 环境变量 | 说明 |
|---|---|
| `MEDKERNEL_API_BASE` | 后端 API 基址 |
| `MEDKERNEL_API_TOKEN` | Bearer 鉴权令牌（须持 `engine-data.read`） |

## 运行（MCP 客户端 stdio 接入）

```jsonc
// 例：MCP 客户端配置
{
  "command": "node",
  "args": ["mcp-server/src/server.mjs"],
  "env": { "MEDKERNEL_API_BASE": "https://medkernel.example.org", "MEDKERNEL_API_TOKEN": "<token>" }
}
```

服务读取 stdin 的换行分隔 JSON-RPC 消息，响应写回 stdout。支持方法：

| 方法 | 行为 |
|---|---|
| `initialize` | 返回协议版本 / `tools` 能力 / serverInfo |
| `tools/list` | 经后端 `/tools` 列出 7 个受控工具（含 `inputSchema`，`purpose` 必填） |
| `tools/call` | 派发后端 `/tools/{name}:execute`，以 text content 返回治理信封；失败以 `isError` 返回 |
| `notifications/initialized` | 通知，无响应 |

## 诚实分寸（初版骨架）

- 仅实现 stdio JSON-RPC 传输 + `initialize`/`tools/list`/`tools/call`；SSE/HTTP 传输、资源（resources）、提示（prompts）、采样（sampling）等暂未实现。
- 运行时需后端已合并对应受控工具（[PR #612](https://github.com/Dasheen920624/medkernel/pull/612)，7/7 工具）。
- 工具调用审计在**后端**完成（FR-6）；MCP 服务不承担首要脱敏（后端脱敏，FR-2），默认不返回可拼提示词的患者上下文（视角 11，由后端 D4 工具脱敏裁决）。

## 测试

```
cd mcp-server && npm test    # node --test test/*.test.mjs（零运行时依赖，fetch/stdio 经注入替身）
```
