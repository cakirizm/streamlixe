import assert from "node:assert/strict";
import test from "node:test";

// 9f329e9 dropped the SVG and .ico icons in favour of the PNG set; assert what the
// branding actually ships now instead of the retired favicon.svg link.
const faviconLink = /<link(?=[^>]*\brel=["']icon["'])(?=[^>]*\bhref=["'][^"']*favicon-32x32\.png["'])[^>]*>/i;
const appleTouchIconLink = /<link(?=[^>]*\brel=["']apple-touch-icon["'])(?=[^>]*\bhref=["'][^"']*apple-touch-icon\.png["'])[^>]*>/i;

test("renders site favicon metadata", async () => {
  const workerUrl = new URL("../dist/server/index.js", import.meta.url);
  workerUrl.searchParams.set("test", `${process.pid}-${Date.now()}`);
  const { default: worker } = await import(workerUrl.href);

  const response = await worker.fetch(
    new Request("http://localhost/", {
      headers: { accept: "text/html" },
    }),
    {
      ASSETS: {
        fetch: async () => new Response("Not found", { status: 404 }),
      },
    },
    {
      waitUntil() {},
      passThroughOnException() {},
    },
  );

  assert.equal(response.status, 200);
  assert.match(
    response.headers.get("content-type") ?? "",
    /^text\/html\b/i,
  );
  const html = await response.text();
  assert.match(html, faviconLink);
  assert.match(html, appleTouchIconLink);
});
