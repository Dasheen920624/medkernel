/**
 * MCP 服务配置加载（DATASVC-01 FR-4 / FR-5）。
 *
 * MCP 服务只从环境读取「后端 API 基址 + 鉴权令牌」，经后端受控合同取数——
 * **绝不读取本地数据库连接串、不直连库、不绕治理**（工具清单/权限/脱敏/审计/降级由后端裁决）。
 */

export class McpConfigError extends Error {
  constructor(message) {
    super(message);
    this.name = 'McpConfigError';
  }
}

export function loadConfig(env = process.env) {
  const baseUrl = env.MEDKERNEL_API_BASE;
  const token = env.MEDKERNEL_API_TOKEN;
  if (!baseUrl) {
    throw new McpConfigError('缺少 MEDKERNEL_API_BASE：MCP 服务经后端 API，不读本地库连接串');
  }
  if (!token) {
    throw new McpConfigError('缺少 MEDKERNEL_API_TOKEN：MCP 服务须用后端鉴权令牌');
  }
  return { baseUrl: baseUrl.replace(/\/+$/, ''), token };
}
