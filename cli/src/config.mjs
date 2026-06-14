/**
 * CLI 配置加载（DATASVC-01 FR-3）。
 *
 * CLI 只从环境读取「后端 API 基址 + 鉴权令牌」，走后端 API 鉴权——
 * **绝不读取本地数据库连接串**（JDBC / DATASOURCE / DATABASE_URL 等一律不读不用，治理边界）。
 */

export class CliConfigError extends Error {
  constructor(message) {
    super(message);
    this.name = 'CliConfigError';
  }
}

export function loadConfig(env = process.env) {
  const baseUrl = env.MEDKERNEL_API_BASE;
  const token = env.MEDKERNEL_API_TOKEN;
  if (!baseUrl) {
    throw new CliConfigError('缺少 MEDKERNEL_API_BASE：CLI 走后端 API，不读本地库连接串');
  }
  if (!token) {
    throw new CliConfigError('缺少 MEDKERNEL_API_TOKEN：CLI 须用后端鉴权令牌');
  }
  // 只取 API 基址与令牌；库连接串等环境变量一律不读取（治理边界，FR-3）。
  return { baseUrl: baseUrl.replace(/\/+$/, ''), token };
}
