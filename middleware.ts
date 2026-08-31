// TV uygulaması (webOS/Tizen paketleri) /api/* uçlarını kendi paket origin'inden
// (streamlivex.com değil) çağırıyor; bu yüzden bu uçların tarayıcı CORS kısıtlaması
// olmadan erişilebilir olması gerekiyor. Bu rotalar çerez/oturum kullanmıyor —
// kimlik bilgileri her istekte açıkça body içinde geliyor — bu yüzden geniş CORS
// izni burada güvenli.
import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

export function middleware(request: NextRequest) {
  if (request.method === "OPTIONS") {
    return new NextResponse(null, {
      status: 204,
      headers: {
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
        "Access-Control-Allow-Headers": "content-type",
      },
    });
  }
  const response = NextResponse.next();
  response.headers.set("Access-Control-Allow-Origin", "*");
  return response;
}

export const config = { matcher: "/api/:path*" };
