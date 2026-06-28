declare module "*.mjs" {
  export function resolveEmbedAppBase(baseUrl?: string): string;
  export function resolveEmbedOrigin(baseUrl?: string): string;
}
