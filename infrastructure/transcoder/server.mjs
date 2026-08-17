import http from "node:http";
import { spawn } from "node:child_process";
import { createReadStream, existsSync } from "node:fs";
import { mkdir, readFile, rm, stat, writeFile } from "node:fs/promises";
import { extname, join, normalize } from "node:path";
import { randomUUID, timingSafeEqual } from "node:crypto";

const port=Number(process.env.PORT||3100);
const token=process.env.TRANSCODE_TOKEN||"";
const root=process.env.TRANSCODE_ROOT||"/var/cache/streamlivex-transcoder";
const publicBase=(process.env.PUBLIC_BASE||"https://relay.streamlivex.com/transcode").replace(/\/$/,"");
const sessions=new Map();
await mkdir(root,{recursive:true});

function json(res,status,data){res.writeHead(status,{"content-type":"application/json; charset=utf-8","access-control-allow-origin":"*","cache-control":"no-store"});res.end(JSON.stringify(data))}
function authorized(req){const supplied=(req.headers.authorization||"").replace(/^Bearer\s+/i,"");if(!token||supplied.length!==token.length)return false;return timingSafeEqual(Buffer.from(supplied),Buffer.from(token))}
function safeUrl(value){const url=new URL(String(value||""));if(!/^https?:$/.test(url.protocol))throw new Error("Yalnızca HTTP/HTTPS kaynakları desteklenir");if(/^(localhost|127\.|0\.|10\.|192\.168\.|169\.254\.|172\.(1[6-9]|2\d|3[01])\.|\[?::1\]?)/i.test(url.hostname))throw new Error("Özel ağ adresi engellendi");return url.href}
function run(file,args,timeout=30000){return new Promise((resolve,reject)=>{const child=spawn(file,args,{stdio:["ignore","pipe","pipe"]});let out="",err="";const timer=setTimeout(()=>{child.kill("SIGKILL");reject(new Error("Kaynak incelemesi zaman aşımına uğradı"))},timeout);child.stdout.on("data",chunk=>out+=chunk);child.stderr.on("data",chunk=>err+=chunk);child.on("error",reject);child.on("close",code=>{clearTimeout(timer);code===0?resolve(out):reject(new Error(err.trim().slice(-500)||`İşlem ${code} koduyla durdu`))})})}
function quote(value){return String(value).replace(/[\r\n"]/g," ")}
async function waitFor(paths,child){for(let attempt=0;attempt<60;attempt++){if(paths.every(existsSync))return;if(child.exitCode!==null)throw new Error("Dönüştürücü başlangıçta durdu");await new Promise(resolve=>setTimeout(resolve,250))}throw new Error("Dönüştürücü zamanında hazırlanamadı")}
async function startSession(source){
  for(const session of sessions.values())if(session.source===source&&session.child?.exitCode===null){session.lastAccess=Date.now();return session}
  for(const session of sessions.values())if(session.child?.exitCode===null)throw Object.assign(new Error("Dönüştürücü şu anda başka bir içerik hazırlıyor"),{status:429});
  const probe=JSON.parse(await run("ffprobe",["-v","error","-user_agent","VLC/3.0 StreamLiveX-Transcoder","-show_streams","-of","json",source],45000));
  const streams=probe.streams||[];const video=streams.find(row=>row.codec_type==="video");
  if(!video)throw Object.assign(new Error("Kaynakta görüntü parçası bulunamadı"),{status:422});
  if(video.codec_name!=="h264")throw Object.assign(new Error(`Görüntü formatı ${video.codec_name||"bilinmiyor"}; düşük kapasiteli VPS'te güvenli dönüştürme yapılamaz`),{status:422});
  const audios=streams.filter(row=>row.codec_type==="audio").slice(0,8);
  const subtitles=streams.filter(row=>row.codec_type==="subtitle"&&/^(subrip|ass|ssa|webvtt|mov_text|text)$/.test(row.codec_name||"")).slice(0,8);
  if(!audios.length)throw Object.assign(new Error("Kaynakta ses parçası bulunamadı"),{status:422});
  const id=randomUUID().replaceAll("-","");const dir=join(root,id);await mkdir(dir,{recursive:true});
  const args=["-hide_banner","-loglevel","warning","-user_agent","VLC/3.0 StreamLiveX-Transcoder","-i",source,"-map","0:v:0","-c:v","copy","-an","-sn","-f","hls","-hls_time","6","-hls_list_size","0","-hls_playlist_type","event","-hls_segment_filename",join(dir,"video_%06d.ts"),join(dir,"video.m3u8")];
  audios.forEach((audio,index)=>args.push("-map",`0:${audio.index}`,"-vn","-sn","-c:a","aac","-b:a","128k","-ac","2","-f","hls","-hls_time","6","-hls_list_size","0","-hls_playlist_type","event","-hls_segment_filename",join(dir,`audio${index}_%06d.ts`),join(dir,`audio${index}.m3u8`)));
  subtitles.forEach((subtitle,index)=>args.push("-map",`0:${subtitle.index}`,"-vn","-an","-c:s","webvtt","-f","segment","-segment_time","6","-segment_list",join(dir,`subtitle${index}.m3u8`),"-segment_list_type","m3u8","-segment_format","webvtt",join(dir,`subtitle${index}_%06d.vtt`)));
  const child=spawn("ffmpeg",args,{stdio:["ignore","ignore","pipe"]});let stderr="";child.stderr.on("data",chunk=>stderr=(stderr+chunk).slice(-4000));
  const session={id,source,dir,child,lastAccess:Date.now(),stderr};sessions.set(id,session);
  child.on("close",code=>{session.finishedAt=Date.now();session.stderr=stderr;if(code)console.error(`FFmpeg session ${id} exited ${code}: ${stderr}`)});
  await waitFor([join(dir,"video.m3u8"),...audios.map((_,i)=>join(dir,`audio${i}.m3u8`))],child);
  const audioLines=audios.map((audio,index)=>{const lang=quote(audio.tags?.language||"und");const name=quote(audio.tags?.title||audio.tags?.handler_name||`${lang.toUpperCase()} ses ${index+1}`);return `#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",NAME="${name}",LANGUAGE="${lang}",DEFAULT=${index===0?"YES":"NO"},AUTOSELECT=YES,URI="audio${index}.m3u8"`});
  const subtitleLines=subtitles.map((sub,index)=>{const lang=quote(sub.tags?.language||"und");const name=quote(sub.tags?.title||`${lang.toUpperCase()} altyazı ${index+1}`);return `#EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="subs",NAME="${name}",LANGUAGE="${lang}",DEFAULT=NO,AUTOSELECT=YES,FORCED=NO,URI="subtitle${index}.m3u8"`});
  const attrs=[`BANDWIDTH=${Math.max(800000,Number(video.bit_rate||4000000)+128000)}`,`CODECS="avc1.640028,mp4a.40.2"`,`AUDIO="audio"`];if(subtitles.length)attrs.push(`SUBTITLES="subs"`);
  await writeFile(join(dir,"master.m3u8"),["#EXTM3U","#EXT-X-VERSION:3",...audioLines,...subtitleLines,`#EXT-X-STREAM-INF:${attrs.join(",")}`,"video.m3u8",""] .join("\n"));
  return session;
}
function mime(file){return file.endsWith(".m3u8")?"application/vnd.apple.mpegurl":file.endsWith(".ts")?"video/mp2t":file.endsWith(".vtt")?"text/vtt":"application/octet-stream"}
const server=http.createServer(async(req,res)=>{try{
  const url=new URL(req.url,"http://localhost");if(req.method==="OPTIONS"){res.writeHead(204,{"access-control-allow-origin":"*","access-control-allow-methods":"GET,POST,DELETE,OPTIONS","access-control-allow-headers":"authorization,content-type"});res.end();return}
  if(req.method==="GET"&&url.pathname==="/health"){json(res,200,{ok:true,active:[...sessions.values()].filter(s=>s.child?.exitCode===null).length});return}
  if(req.method==="POST"&&url.pathname==="/start"){if(!authorized(req)){json(res,401,{error:"Yetkisiz"});return}let body="";for await(const chunk of req){body+=chunk;if(body.length>16384)throw new Error("İstek çok büyük")};const source=safeUrl(JSON.parse(body||"{}").url);const session=await startSession(source);json(res,200,{sessionId:session.id,manifest:`${publicBase}/${session.id}/master.m3u8`});return}
  const match=url.pathname.match(/^\/session\/([a-f0-9]{32})\/(.+)$/);if(req.method==="GET"&&match){const session=sessions.get(match[1]);if(!session){json(res,404,{error:"Oturum bulunamadı"});return}const relative=normalize(match[2]).replace(/^(\.\.(\/|\\|$))+/,"");const file=join(session.dir,relative);if(!file.startsWith(session.dir)||!existsSync(file)){json(res,404,{error:"Parça henüz hazır değil"});return}session.lastAccess=Date.now();const info=await stat(file);res.writeHead(200,{"content-type":mime(file),"content-length":String(info.size),"access-control-allow-origin":"*","cache-control":file.endsWith(".m3u8")?"no-store":"public, max-age=3600"});createReadStream(file).pipe(res);return}
  const stop=url.pathname.match(/^\/session\/([a-f0-9]{32})$/);if(req.method==="DELETE"&&stop){if(!authorized(req)){json(res,401,{error:"Yetkisiz"});return}const session=sessions.get(stop[1]);session?.child?.kill("SIGTERM");json(res,200,{ok:true});return}
  json(res,404,{error:"Bulunamadı"});
}catch(error){json(res,error.status||500,{error:error instanceof Error?error.message:"Dönüştürme hatası"})}});
setInterval(async()=>{const now=Date.now();for(const [id,session] of sessions){if(session.child?.exitCode===null&&now-session.lastAccess>120000)session.child.kill("SIGTERM");if((session.finishedAt&&now-session.finishedAt>3600000)||now-session.lastAccess>7200000){sessions.delete(id);await rm(session.dir,{recursive:true,force:true}).catch(()=>{})}}},30000).unref();
server.listen(port,"127.0.0.1",()=>console.log(`StreamLiveX transcoder listening on ${port}`));
