import { createServer } from "node:http";

const port = 8788;
const upstreamOrigin = "https://api.themoviedb.org";

createServer(async (request, response) => {
  try {
    if (request.method !== "GET" || !request.url?.startsWith("/3/")) {
      response.writeHead(404, { "content-type": "application/json" });
      response.end(JSON.stringify({ error: "Not found" }));
      return;
    }

    const upstreamUrl = new URL(request.url, upstreamOrigin);
    const headers = { accept: "application/json" };
    if (request.headers.authorization) {
      headers.authorization = request.headers.authorization;
    }

    const upstream = await fetch(upstreamUrl, { headers });
    const body = await upstream.arrayBuffer();
    response.writeHead(upstream.status, {
      "content-type": upstream.headers.get("content-type") || "application/json",
      "cache-control": upstream.headers.get("cache-control") || "no-store",
    });
    response.end(Buffer.from(body));
  } catch (error) {
    console.error("TMDB proxy request failed:", error);
    response.writeHead(502, { "content-type": "application/json" });
    response.end(JSON.stringify({ error: "TMDB upstream request failed" }));
  }
}).listen(port, "0.0.0.0", () => {
  console.log(`TMDB proxy listening on port ${port}`);
});
