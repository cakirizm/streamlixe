declare module "cloudflare:workers" {
  export const env: {
    DB?: D1Database;
    PAIRING_KV?: KVNamespace;
    [key: string]: unknown;
  };
}

interface KVNamespace {
  get(key: string, options?: { type?: "text" }): Promise<string | null>;
  put(key: string, value: string, options?: { expirationTtl?: number }): Promise<void>;
  delete(key: string): Promise<void>;
}

interface Fetcher {
  fetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response>;
}

interface D1Database {
  prepare(query: string): any;
  batch<T = unknown>(statements: any[]): Promise<T[]>;
  exec(query: string): Promise<any>;
  dump(): Promise<ArrayBuffer>;
}
