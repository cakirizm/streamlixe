export type SubtitleCue = { start:number; end:number; text:string };

function parseTime(value:string){
  const clean=value.trim().replace(",",".");
  const parts=clean.split(":").map(Number);
  if(parts.some(Number.isNaN)||parts.length<2||parts.length>3)return null;
  const seconds=parts.length===3?parts[0]*3600+parts[1]*60+parts[2]:parts[0]*60+parts[1];
  return seconds>=0?seconds:null;
}

function cleanText(value:string){
  return value
    .replace(/\{\\[^}]+}/g,"")
    .replace(/<br\s*\/?>/gi,"\n")
    .replace(/<[^>]+>/g,"")
    .replace(/\\[Nn]/g,"\n")
    .replace(/&nbsp;/gi," ")
    .replace(/&amp;/gi,"&")
    .replace(/&lt;/gi,"<")
    .replace(/&gt;/gi,">")
    .replace(/&quot;/gi,'"')
    .replace(/&#(?:39|x27);/gi,"'")
    .split("\n")
    .map(line=>line.trim())
    .filter(Boolean)
    .join("\n")
    .trim();
}

export function parseSubtitleCues(source:string):SubtitleCue[]{
  const normalized=source.replace(/^\uFEFF/,"").replace(/\r/g,"");
  const ass:SubtitleCue[]=[];
  for(const line of normalized.split("\n")){
    if(!/^Dialogue\s*:/i.test(line))continue;
    const fields=line.replace(/^Dialogue\s*:\s*/i,"").split(",");
    if(fields.length<10)continue;
    const start=parseTime(fields[1]);const end=parseTime(fields[2]);const text=cleanText(fields.slice(9).join(","));
    if(start!==null&&end!==null&&end>start&&text)ass.push({start,end,text});
  }
  if(ass.length)return ass.sort((a,b)=>a.start-b.start);

  const cues:SubtitleCue[]=[];
  for(const block of normalized.split(/\n{2,}/)){
    const lines=block.split("\n").map(line=>line.trimEnd());
    const timingIndex=lines.findIndex(line=>line.includes("-->"));
    if(timingIndex<0)continue;
    const match=lines[timingIndex].match(/^\s*(\d{1,2}:)?\d{1,2}:\d{2}[.,]\d{1,3}\s*-->\s*((?:\d{1,2}:)?\d{1,2}:\d{2}[.,]\d{1,3})/);
    if(!match)continue;
    const left=lines[timingIndex].split("-->")[0];const start=parseTime(left);const end=parseTime(match[2]);const text=cleanText(lines.slice(timingIndex+1).join("\n"));
    if(start!==null&&end!==null&&end>start&&text)cues.push({start,end,text});
  }
  return cues.sort((a,b)=>a.start-b.start);
}

export function subtitleAt(cues:SubtitleCue[],time:number){
  let low=0,high=cues.length-1;
  while(low<=high){const middle=(low+high)>>1;const cue=cues[middle];if(time<cue.start)high=middle-1;else if(time>cue.end)low=middle+1;else return cue.text}
  return "";
}
