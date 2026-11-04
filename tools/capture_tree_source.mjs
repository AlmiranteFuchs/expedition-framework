// Read-only capture through the existing authenticated MCP bridge.
// Writes exact block states; native NBT conversion is a separate offline step.
import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const output=resolve(root,'docs/art-direction/player-tree/source-blocks.json');
if(existsSync(output)&&!process.argv.includes('--replace'))throw Error('Authored source already exists; use --replace only for a requested new capture.');
const sdk=resolve(root,'run/mcp-tools/mcpfabric-neoforge-v0.8.0/mcp-server/node_modules/@modelcontextprotocol/sdk/dist/esm');
const {Client}=await import(pathToFileURL(resolve(sdk,'client/index.js')));
const {StdioClientTransport}=await import(pathToFileURL(resolve(sdk,'client/stdio.js')));
const client=new Client({name:'player-tree-source-capture',version:'1.0.0'});
const transport=new StdioClientTransport({command:process.execPath,args:[resolve(root,'tools/minecraft_mcp.mjs')],stderr:'pipe'});
function data(r){if(r.isError)throw Error(JSON.stringify(r.content));if(r.structuredContent)return r.structuredContent;
    for(const c of r.content??[])if(c.type==='text'){try{return JSON.parse(c.text);}catch{}}throw Error('No structured reply');}
const call=async(name,args={})=>data(await client.callTool({name,arguments:args}));
try {
    await client.connect(transport);
    const screen=await call('get_screen');
    if(!screen.inWorld||screen.lastWorldId!=='sp:MCP-Debug-Test-20261029')throw Error('Expected authoring debug world');
    const region={from:{x:1230,y:200,z:1020},to:{x:1275,y:275,z:1070},includeAir:false,maxBlocks:20000};
    const before=await call('get_blocks_region',region);
    if(before.truncated||!before.blocks.length)throw Error('Incomplete or empty capture');
    const selected=before.blocks.filter(p=>!['minecraft:moss_block','minecraft:structure_block'].includes(p.id));
    const blocks=[];
    for(let i=0;i<selected.length;i+=16){
        const batch=selected.slice(i,i+16);
        const results=await Promise.allSettled(batch.map(p=>call('get_block',{x:p.x,y:p.y,z:p.z})));
        for(let j=0;j<results.length;j++){
            const r=results[j];if(r.status!=='fulfilled')throw r.reason;
            if(r.value.id!==batch[j].id)throw Error('Build changed during capture; retry without editing');
            blocks.push(r.value);
        }
    }
    const after=await call('get_blocks_region',region);
    const key=p=>`${p.x},${p.y},${p.z}:${p.id}`;
    if(after.truncated||JSON.stringify(after.blocks.map(key).sort())!==JSON.stringify(before.blocks.map(key).sort()))
        throw Error('Build changed during capture; no source was written');
    const result={world:screen.lastWorldId,capturedAt:new Date().toISOString(),blocks,
        portalMarkers:blocks.filter(p=>p.id==='minecraft:orange_stained_glass').map(p=>({x:p.x,y:p.y,z:p.z})),
        note:'Exact player-authored source. Do not regenerate or procedurally reshape.'};
    const out=output;mkdirSync(dirname(out),{recursive:true});
    writeFileSync(out,JSON.stringify(result,null,2)+'\n');
    console.log(JSON.stringify({captured:blocks.length,output:out}));
} finally {await client.close();}
