declare module "cloudflare:workers" {
  export const env: {
    DB?: D1Database;
    [key: string]: unknown;
  };
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
