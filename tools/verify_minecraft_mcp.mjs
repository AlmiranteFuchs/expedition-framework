// Live integration checks in a disposable world copy, using the real MCP protocol.
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { gunzipSync } from 'node:zlib';
import { dirname, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import assert from 'node:assert/strict';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const sdk = resolve(root, 'run/mcp-tools/mcpfabric-neoforge-v0.8.0/mcp-server/node_modules/@modelcontextprotocol/sdk/dist/esm/client');
const { Client } = await import(pathToFileURL(resolve(sdk, 'index.js')));
const { StdioClientTransport } = await import(pathToFileURL(resolve(sdk, 'stdio.js')));
const client = new Client({ name: 'expedition-live-tests', version: '1.0.0' });
const transport = new StdioClientTransport({command: process.execPath,
  args: [resolve(root, 'tools/minecraft_mcp.mjs')], stderr: 'pipe'});
const directory = resolve(root, 'build/mcp-verification');
mkdirSync(directory, { recursive: true });
const report = { world: 'sp:MCP-Debug-Test-20261029', checks: [], expeditions: [] };
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));

// Read-only NBT decoding lets us compare live entities to the exact UUIDs the
// authoritative run tracks, instead of confusing ambient fauna with its horde.
function savedData() {
  const buffer = gunzipSync(readFileSync(resolve(root,
    'run/saves/MCP-Debug-Test-20261029/data/expeditions_runs.dat')));
  let offset = 0;
  const number = (size, method) => { const result = buffer[method](offset); offset += size; return result; };
  const byte = () => number(1, 'readUInt8');
  const int = () => number(4, 'readInt32BE');
  const string = () => { const size = number(2, 'readUInt16BE'); const result = buffer.toString('utf8', offset, offset + size); offset += size; return result; };
  function value(type) {
    switch (type) {
      case 1: return number(1, 'readInt8');
      case 2: return number(2, 'readInt16BE');
      case 3: return int();
      case 4: return number(8, 'readBigInt64BE').toString();
      case 5: return number(4, 'readFloatBE');
      case 6: return number(8, 'readDoubleBE');
      case 7: { const size = int(); const result = buffer.subarray(offset, offset + size); offset += size; return [...result]; }
      case 8: return string();
      case 9: { const child = byte(), size = int(); return Array.from({length: size}, () => value(child)); }
      case 10: { const result = {}; for (let child; (child = byte()) !== 0;) { const key = string(); result[key] = value(child); } return result; }
      case 11: return Array.from({length: int()}, () => int());
      case 12: return Array.from({length: int()}, () => number(8, 'readBigInt64BE').toString());
      default: throw new Error(`Unsupported NBT tag ${type}`);
    }
  }
  const type = byte(); string();
  return value(type).data;
}
function uuid(parts) {
  const hex = parts.map(n => (n >>> 0).toString(16).padStart(8, '0')).join('');
  return `${hex.slice(0,8)}-${hex.slice(8,12)}-${hex.slice(12,16)}-${hex.slice(16,20)}-${hex.slice(20)}`;
}
async function call(name, args = {}) {
  const result = await client.callTool({ name, arguments: args });
  const texts = (result.content ?? []).filter(part => part.type === 'text').map(part => part.text);
  if (result.isError || texts.some(text => text.startsWith('Bridge error'))) {
    throw new Error(`${name}: ${texts.join('\n')}`);
  }
  if (name === 'screenshot') {
    const part = result.content.find(part => part.type === 'image');
    assert(part, 'MCP must return a real screenshot');
    const path = resolve(directory, `${args.label ?? 'live'}-${Date.now()}.png`);
    writeFileSync(path, Buffer.from(part.data, 'base64'));
    return path;
  }
  try { return JSON.parse(texts[0]); } catch { return texts.join('\n'); }
}
async function command(command, requireSuccess = true) {
  const result = await call('run_command', {command});
  if (requireSuccess) assert.equal(result.success, true, JSON.stringify(result));
  return result;
}
async function snapshot() {
  await command('save-all flush');
  return savedData();
}
async function clickMarker(point, dimension) {
  await call('close_screen');
  await command(`execute in ${dimension} run tp Dev ${point.x + .5} ${point.y + 1} ${point.z + 2.5}`);
  await pause(250);
  await call('look_at', {x: point.x + .5, y: point.y + .5, z: point.z + .5});
  await pause(200);
  const use = await call('use_item');
  await pause(250);
  return use;
}
try {
  await client.connect(transport);
  const screen = await call('get_screen');
  assert.equal(screen.lastWorldId, report.world, 'Live writes are restricted to the disposable debug copy');
  await call('close_screen');
  await call('stop_movement');
  report.dimensions = await call('list_dimensions');
  report.missions = await command('expeditions dimensions');
  await command('execute as Dev at @s run expeditions abandon', false);
  await command('gamerule doMobSpawning false');
  await command('gamemode creative Dev');
  await command('item replace entity Dev weapon.mainhand with minecraft:air');
  await command('execute in minecraft:overworld run fill -4 179 -4 4 179 4 minecraft:stone');
  await command('execute in minecraft:overworld run fill -4 180 -4 4 184 4 minecraft:air');
  await command('execute in minecraft:overworld run tp Dev 0.5 180 0.5');

  for (const dimension of ['aether:the_aether', 'the_bumblezone:the_bumblezone', 'twilightforest:twilight_forest']) {
    const test = { dimension, attempts: 0 };
    report.expeditions.push(test);
    console.log(`Testing ${dimension}`);
    const mission = `expeditions:auto/${dimension.replace(':','/')}`;
    let started = false;
    for (let attempt = 0; attempt < 3 && !started; attempt++) {
      test.attempts++;
      const start = await command(`execute as Dev at @s run expeditions start ${mission} standard 1`, false);
      started = start.success && start.resultValue > 0;
      if (!started) console.log(`Route refused safely on attempt ${attempt + 1}`);
    }
    assert(started, `No generated safe route resolved in ${dimension}`);
    let data = await snapshot();
    const run = data.Runs.find(run => run.Status === 'ACTIVE' && run.Dimension === dimension);
    assert(run, 'Start must persist an active run in the requested dimension');
    test.runId = uuid(run.RunId);
    test.checkpoint = run.Checkpoints[0];
    test.exit = run.Exit;
    await command(`execute in ${run.PortalDimension} run tp Dev ${run.Portal.x + .5} ${run.Portal.y + .1} ${run.Portal.z + .5}`);
    let self;
    for (let i = 0; i < 20; i++) {
      await pause(150);
      self = await call('get_self');
      if (self.dimension === dimension) break;
    }
    assert.equal(self.dimension, dimension, 'The real portal must teleport the player');
    test.portalEntry = true;
    if (report.checks.length === 0) {
      await call('look', {yaw: 0, pitch: 0});
      const before = await call('get_self');
      await call('set_movement', {forward: true});
      await pause(500);
      await call('stop_movement');
      const after = await call('get_self');
      const distance = Math.hypot(after.x-before.x, after.y-before.y, after.z-before.z);
      assert(distance > .1, 'MCP movement must actually move the player');
      report.checks.push({movementDistance: distance});
    }
    test.click = await clickMarker(run.Checkpoints[0], dimension);
    data = await snapshot();
    const active = data.Runs.find(entry => uuid(entry.RunId) === test.runId);
    assert.equal(active.Done, 1, 'The real right-click must activate the checkpoint');
    const tracked = active.ChallengeEntities.map(entry => uuid(entry.EntityId));
    assert(tracked.length > 0, 'Checkpoint must track an actual horde');
    test.horde = [];
    for (const id of tracked) {
      const entity = await call('get_entity', {uuid:id});
      assert.equal(entity.dimension, dimension);
      test.horde.push({uuid: id, type: entity.type, x:entity.x, y:entity.y, z:entity.z});
    }
    if (dimension.startsWith('aether:')) {
      assert(test.horde.every(entity => entity.type.startsWith('aether:')), 'Aether hordes must use native custom hostile categories');
    }
    if (dimension.startsWith('the_bumblezone:')) {
      assert(test.horde.every(entity => entity.type === 'minecraft:bee' || entity.type.startsWith('the_bumblezone:')),
        'Bumblezone hordes must use local bees rather than generic monsters');
    }
    const first = test.horde[0];
    await call('look_at', {x:first.x, y:first.y + .7, z:first.z});
    await pause(200);
    test.screenshot = await call('screenshot');
    await clickMarker(run.Exit, dimension);
    data = await snapshot();
    const ended = data.Runs.find(entry => uuid(entry.RunId) === test.runId);
    assert.equal(ended.Status, 'COMPLETED', 'Extraction must complete the run');
    assert.equal(ended.Rewarded, 1, 'Extraction must record rewards exactly once');
    assert.equal(ended.ChallengeEntities.length, 0, 'Run end must untrack its discarded horde');
    test.status = ended.Status;
    test.tokensAwarded = ended.TokensAwarded;
    test.cleanup = true;
    console.log(`PASS ${dimension}: ${test.horde.length} ${first.type}, reward ${test.tokensAwarded}, horde cleaned`);
    writeFileSync(resolve(directory, 'live-report.json'), JSON.stringify(report, null, 2));
  }
  report.success = true;
} catch (error) {
  report.error = error.stack;
  console.error(error.message);
  process.exitCode = 1;
} finally {
  writeFileSync(resolve(directory, 'live-report.json'), JSON.stringify(report, null, 2));
  try { await call('stop_movement'); } catch { }
  await client.close();
}
