// Local development bridge only; never included in the Minecraft mod.
import { readFileSync, existsSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';

const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const configPath = resolve(projectRoot, 'run/config/mcpfabric.config.json');
const entry = resolve(projectRoot,
  'run/mcp-tools/mcpfabric-neoforge-v0.8.0/mcp-server/dist/index.js');
if (!existsSync(configPath) || !existsSync(entry)) {
  throw new Error('Install the local MCPFabric bridge and launch the development client first.');
}
const config = JSON.parse(readFileSync(configPath, 'utf8'));
if (config.host !== '127.0.0.1' || !config.requireAuth || !config.token) {
  throw new Error('The Minecraft bridge must use authenticated localhost access.');
}
const bridge = spawn(process.execPath, [entry], {
  cwd: projectRoot,
  stdio: 'inherit',
  windowsHide: true,
  env: {
    ...process.env,
    MCPFABRIC_URL: `http://127.0.0.1:${config.port}`,
    MCPFABRIC_TOKEN: config.token,
    MCPFABRIC_TRANSPORT: 'stdio',
    MCPFABRIC_AGENT: 'false',
    MCPFABRIC_DATA_DIR: resolve(projectRoot, 'run/mcp-tools/data'),
  },
});
bridge.on('error', error => { console.error(error.message); process.exitCode = 1; });
bridge.on('exit', code => { process.exitCode = code ?? 1; });
for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => bridge.kill(signal));
}
