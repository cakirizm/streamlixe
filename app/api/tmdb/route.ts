export async function GET(request:Request){
  const token=process.env.TMDB_TOKEN;
  if(!token)return Response.json({results:[],configured:false});
  try{
    const input=new URL(request.url);const locale=getRequestLocale(request);const query=input.searchParams.get("query");const mode=input.searchParams.get("mode");const person=input.searchParams.get("person");const id=input.searchParams.get("id");const kind=input.searchParams.get("kind")==="series"||input.searchParams.get("kind")==="tv"?"tv":"movie";
    const isV3=/^[a-f0-9]{32}$/i.test(token);const headers:Record<string,string>={accept:"application/json"};if(!isV3)headers.authorization=`Bearer ${token}`;
    const tmdbUrl=(path:string)=>{const url=new URL(`https://api.themoviedb.org/3/${path}`);if(isV3)url.searchParams.set("api_key",token);url.searchParams.set("language",locale);return url};
    const get=async(url:URL)=>{const response=await fetch(url,{headers});if(!response.ok)throw new Error(`TMDB ${response.status}`);return response.json()};
    if(mode==="genres"){const data=await get(tmdbUrl(`genre/${kind}/list`));return Response.json({configured:true,genres:data.genres||[]},{headers:{"cache-control":"public, max-age=86400"}})}
    if(mode==="season"){
      let tvId=Number(id)||0;const season=Math.max(0,Number(input.searchParams.get("season")||1));
      if(!tvId&&query){const searchUrl=tmdbUrl("search/tv");searchUrl.searchParams.set("query",query);tvId=Number((await get(searchUrl)).results?.[0]?.id||0)}
      if(!tvId)return Response.json({configured:true,episodes:[]});
      const data=await get(tmdbUrl(`tv/${tvId}/season/${season}`));
      return Response.json({configured:true,episodes:(data.episodes||[]).map((x:any)=>({id:x.id,episode_number:x.episode_number,name:x.name,overview:x.overview,still_path:x.still_path,air_date:x.air_date,runtime:x.runtime,vote_average:x.vote_average}))},{headers:{"cache-control":"public, max-age=86400"}})
    }
    if(mode==="discover"){const url=tmdbUrl(`discover/${kind}`);url.searchParams.set("with_genres",input.searchParams.get("genre")||"");url.searchParams.set("sort_by","popularity.desc");url.searchParams.set("include_adult","false");const data=await get(url);return Response.json({configured:true,results:(data.results||[]).slice(0,12)},{headers:{"cache-control":"public, max-age=1800"}})}
    if(mode==="trending"){const pages=await Promise.all([1,2,3].map(async page=>{const url=tmdbUrl(`trending/${kind}/week`);url.searchParams.set("page",String(page));return get(url)}));const results=pages.flatMap(x=>x.results||[]).filter((x:any,i:number,a:any[])=>a.findIndex(y=>y.id===x.id)===i).slice(0,60);return Response.json({configured:true,results},{headers:{"cache-control":"public, max-age=21600"}})}
    if(person){const url=tmdbUrl(`person/${person}/combined_credits`);const data=await get(url);const results=(data.cast||[]).filter((x:any)=>x.media_type==="movie"||x.media_type==="tv").sort((a:any,b:any)=>(b.popularity||0)-(a.popularity||0)).filter((x:any,i:number,a:any[])=>a.findIndex(y=>y.id===x.id&&y.media_type===x.media_type)===i).slice(0,18);return Response.json({configured:true,results})}
    if(query||id){let found:any=null;if(id)found={id:Number(id)};else{const searchUrl=tmdbUrl(`search/${kind}`);searchUrl.searchParams.set("query",cleanPlaylistTitle(query||"")||query||"");found=(await get(searchUrl)).results?.[0]}if(!found)return Response.json({configured:true,result:null});const detailUrl=tmdbUrl(`${kind}/${found.id}`);detailUrl.searchParams.set("append_to_response","credits,recommendations,similar");const data=await get(detailUrl);const creatorRows=kind==="tv"?(data.created_by||[]):[];const directors=[...creatorRows,...(data.credits?.crew||[]).filter((x:any)=>x.job==="Director"||x.job==="Creator"||x.job==="Executive Producer"||x.department==="Directing")].filter((x:any,i:number,a:any[])=>a.findIndex(y=>y.id===x.id)===i).slice(0,5).map((x:any)=>({id:x.id,name:x.name,profile_path:x.profile_path}));const cast=(data.credits?.cast||[]).slice(0,10).map((x:any)=>({id:x.id,name:x.name,character:x.character,profile_path:x.profile_path}));const recommendations=[...(data.recommendations?.results||[]),...(data.similar?.results||[])].filter((x:any,i:number,a:any[])=>a.findIndex(y=>y.id===x.id)===i).slice(0,12);return Response.json({configured:true,result:{...data,media_type:kind,directors,cast,recommendations}})}
    const trendingUrl=tmdbUrl("trending/all/week");const data=await get(trendingUrl);return Response.json({configured:true,results:(data.results||[]).slice(0,20)},{headers:{"cache-control":"public, max-age=1800"}})
  }catch{return Response.json({results:[],configured:true},{status:502})}
}

const cleanPlaylistTitle=(value:string)=>value.replace(/\[[^\]]*]|\([^)]*(?:19|20)\d{2}[^)]*\)|\b(?:19|20)\d{2}\b|\b(?:4K|UHD|FHD|HD|SD|TR|DUBLAJ|ALTYAZILI|MULTI)\b/gi," ").replace(/[._|]+/g," ").replace(/\s+/g," ").trim();
export async function POST(request:Request){
  const token=process.env.TMDB_TOKEN;if(!token)return Response.json({results:[],configured:false});
  try{const locale=getRequestLocale(request);
    const body=await request.json() as {items?:Array<{id:string;name:string;kind:"movie"|"series"}>};
    const items=(body.items||[]).slice(0,72);const isV3=/^[a-f0-9]{32}$/i.test(token);
    const headers:Record<string,string>={accept:"application/json"};if(!isV3)headers.authorization=`Bearer ${token}`;
    const search=async(item:any)=>{
      const kind=item.kind==="series"?"tv":"movie";const url=new URL(`https://api.themoviedb.org/3/search/${kind}`);
      url.searchParams.set("language",locale);url.searchParams.set("query",cleanPlaylistTitle(item.name)||item.name);if(isV3)url.searchParams.set("api_key",token);
      const r=await fetch(url,{headers});if(!r.ok)return null;const found=(await r.json()).results?.[0];
      return found?{playlistId:item.id,kind:item.kind,tmdbId:found.id,title:found.title||found.name,poster_path:found.poster_path,backdrop_path:found.backdrop_path,overview:found.overview,vote_average:found.vote_average,release_date:found.release_date||found.first_air_date,genre_ids:found.genre_ids||[],popularity:found.popularity||0,original_language:found.original_language||"",origin_country:found.origin_country||[]}:null
    };
    const results:any[]=[];for(let i=0;i<items.length;i+=8){const page=await Promise.all(items.slice(i,i+8).map(search));results.push(...page.filter(Boolean))}
    return Response.json({configured:true,results},{headers:{"cache-control":"private, max-age=3600"}})
  }catch{return Response.json({results:[],configured:true},{status:502})}
}

function getRequestLocale(request:Request){const supported=new Set(["tr-TR","en-US","ar-SA","de-DE","fr-FR","es-ES"]);const cookie=request.headers.get("cookie")?.match(/(?:^|;\s*)slx-language=([^;]+)/)?.[1];const query=new URL(request.url).searchParams.get("lang");const value=decodeURIComponent(query||cookie||"tr-TR");return supported.has(value)?value:"tr-TR"}
