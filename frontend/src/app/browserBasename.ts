const ON_PREMISE_BASE_PATH = "/medkernel";

export function resolveBrowserBasename(
  pathname = typeof window !== "undefined" ? window.location.pathname : "/",
) {
  const normalizedPathname = pathname.startsWith("/") ? pathname : `/${pathname}`;
  return normalizedPathname === ON_PREMISE_BASE_PATH ||
    normalizedPathname.startsWith(`${ON_PREMISE_BASE_PATH}/`)
    ? ON_PREMISE_BASE_PATH
    : undefined;
}
