// Use the actual MCP protocol for local diagnostics, including before a host reload.
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const sdkRoot = resolve(projectRoot,
  'run/mcp-tools/mcpfabric-neoforge-v0.8.0/mcp-server/node_modules/@modelcontextprotocol/sdk/dist/esm');
const { Client } = await import(pathToFileURL(resolve(sdkRoot, 'client/index.js')));
const { StdioClientTransport } = await import(pathToFileURL(resolve(sdkRoot, 'client/stdio.js')));
const transport = new StdioClientTransport({
  command: process.execPath,
  args: [resolve(projectRoot, 'tools/minecraft_mcp.mjs')],
  stderr: 'pipe',
});
const client = new Client({ name: 'expedition-live-verification', version: '1.0.0' });
try {
  await client.connect(transport);
  const name = process.argv[2] ?? 'get_status';
  if (name === 'tools/list') {
    console.log(JSON.stringify(await client.listTools()));
  } else {
    const result = await client.callTool({ name, arguments: JSON.parse(process.argv[3] ?? '{}') });
    let imageIndex = 0;
    for (const part of result.content ?? []) {
      if (part.type === 'image') {
        const directory = resolve(projectRoot, 'build/mcp-verification');
        mkdirSync(directory, { recursive: true });
        const path = resolve(directory, `screenshot-${Date.now()}-${imageIndex++}.png`);
        writeFileSync(path, Buffer.from(part.data, 'base64'));
        console.log(JSON.stringify({ screenshot: path }));
      } else if (part.type === 'text') {
        console.log(part.text);
      }
    }
    if (result.isError) process.exitCode = 1;
  }
} finally {
  await client.close();
}
