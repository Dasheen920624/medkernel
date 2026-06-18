const LOCAL_TEST_TIMEOUT_MS = 5_000;
const CI_TEST_TIMEOUT_MS = 15_000;

export function resolveVitestTimeout(env: Record<string, string | undefined>): number {
  return env.CI?.trim().toLowerCase() === "true" ? CI_TEST_TIMEOUT_MS : LOCAL_TEST_TIMEOUT_MS;
}
