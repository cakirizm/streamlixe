/** Cloudflare Worker entry point for the vinext-starter template. */
import { handleImageOptimization, DEFAULT_DEVICE_SIZES, DEFAULT_IMAGE_SIZES } from "vinext/server/image-optimization";
import handler from "vinext/server/app-router-entry";

interface Env {
  ASSETS: Fetcher;
  DB: D1Database;
  IMAGES: {
    input(stream: ReadableStream): {
      transform(options: Record<string, unknown>): {
        output(options: { format: string; quality: number }): Promise<{ response(): Response }>;
      };
    };
  };
}

interface ExecutionContext {
  waitUntil(promise: Promise<unknown>): void;
  passThroughOnException(): void;
}

// Image security config. SVG sources with .svg extension auto-skip the
// optimization endpoint on the client side (served directly, no proxy).
// To route SVGs through the optimizer (with security headers), set
// dangerouslyAllowSVG: true in next.config.js and uncomment below:
// const imageConfig: ImageConfig = { dangerouslyAllowSVG: true };

// TV (webOS/Tizen) paketi streamlivex.com'dan farklı bir origin olduğundan /api/* uçlarına
// çapraz-origin erişim için CORS gerekir. Uçlar durum bilgisi tutmaz/çerez kullanmaz, bu yüzden
// "*" güvenli. Native web (same-origin) davranışı değişmez.
function corsHeaders(): Record<string, string> {
  return {
    "access-control-allow-origin": "*",
    "access-control-allow-methods": "GET, POST, OPTIONS",
    "access-control-allow-headers": "content-type, range",
    "access-control-expose-headers": "content-length, content-range, accept-ranges, x-streamlivex-attempts",
    "access-control-max-age": "86400",
  };
}

const worker = {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    if (env && typeof env === "object") {
      const g = globalThis as unknown as { process?: { env?: Record<string, unknown> } };
      g.process = g.process || { env: {} };
      g.process.env = g.process.env || {};
      for (const [key, val] of Object.entries(env)) {
        if (typeof val === "string") {
          g.process.env[key] = val;
        }
      }
    }
    const url = new URL(request.url);

    if (url.pathname === "/_vinext/image") {
      const allowedWidths = [...DEFAULT_DEVICE_SIZES, ...DEFAULT_IMAGE_SIZES];
      return handleImageOptimization(request, {
        fetchAsset: (path) => env.ASSETS.fetch(new Request(new URL(path, request.url))),
        transformImage: async (body, { width, format, quality }) => {
          const result = await env.IMAGES.input(body).transform(width > 0 ? { width } : {}).output({ format, quality });
          return result.response();
        },
      }, allowedWidths);
    }

    if (url.pathname.startsWith("/api/")) {
      // Preflight'ı kısa devre yap.
      if (request.method === "OPTIONS") {
        return new Response(null, { status: 204, headers: corsHeaders() });
      }
      // Gerçek yanıta CORS başlıklarını ekle (yayın akışı dahil gövde korunur).
      const response = await handler.fetch(request, env, ctx);
      const headers = new Headers(response.headers);
      for (const [key, value] of Object.entries(corsHeaders())) headers.set(key, value);
      return new Response(response.body, { status: response.status, statusText: response.statusText, headers });
    }

    return handler.fetch(request, env, ctx);
  },
};

export default worker;
