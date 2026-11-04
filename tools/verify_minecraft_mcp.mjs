// Live integration checks in a disposable world copy, using the real MCP protocol.
import { writeFileSync, mkdirSync, readdirSync, existsSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { homedir } from 'node:os';
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
const bundledPython = resolve(homedir(), '.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe');
const python = process.env.EXPEDITIONS_PYTHON ?? (existsSync(bundledPython) ? bundledPython : 'python3');
const biomeTables = JSON.parse(execFileSync(python, [resolve(root, 'tools/read_mod_biomes.py'),
  ...readdirSync(resolve(root, 'run/mods')).filter(name => name.endsWith('.jar')).map(name => resolve(root, 'run/mods', name))],
  {encoding:'utf8', maxBuffer: 16 * 1024 * 1024}));
let verifiedWorld = false;
let previousMobSpawning;

function uuid(parts) {
  if (typeof parts === 'string') return parts;
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
  assert(verifiedWorld, 'No live writes before the disposable world has been verified');
  const screen = await call('get_screen');
  assert(screen.inWorld && screen.lastWorldId === report.world, 'Stop if the user leaves the disposable test world');
  const result = await call('run_command', {command});
  if (requireSuccess) assert.equal(result.success, true, JSON.stringify(result));
  return result;
}
async function snapshot() {
  const result = await command('execute as Dev at @s run expeditions diagnostics');
  const json = result.output.find(line => line.trim().startsWith('{'));
  assert(json, 'The live server must return an authoritative diagnostic snapshot');
  return JSON.parse(json);
}
async function waitForClient() {
  for (let n = 0; n < 150; n++) {
    const screen = await call('get_screen');
    assert.equal(screen.lastWorldId, report.world, 'Stay inside the disposable test world');
    if (screen.inWorld && !['loading', 'connecting'].includes(screen.screen)) return;
    await pause(200);
  }
  throw new Error('The client did not finish loading the dimension');
}
async function nativeBiome(dimension, point) {
  const namespace = dimension.split(':')[0];
  for (const [id, categories] of Object.entries(biomeTables).filter(([id]) => id.startsWith(namespace + ':'))) {
    // Execute a real terminal command so the bridge's result callback fires
    // for the successful branch as well as failed conditions.
    const match = await command(`execute in ${dimension} positioned ${point.x} ${point.y} ${point.z} if biome ~ ~ ~ ${id} run time query gametime`, false);
    if (match.success && match.resultValue > 0) {
      return { id, mobs: [...new Set(Object.values(categories).flat().map(entry => entry.type))] };
    }
  }
  throw new Error(`Could not identify the checkpoint's native biome in ${dimension}`);
}
async function clickMarker(point, dimension) {
  await waitForClient();
  await call('close_screen');
  // The route guarantees the marker's own floor, not terrain two blocks
  // beside it. Stand on the solid marker and look down instead of falling
  // off an island or cave ledge between teleport and right-click.
  await command(`execute in ${dimension} run tp Dev ${point.x + .5} ${point.y + 1} ${point.z + .5} 0 90`);
  let scene;
  for (let n = 0; n < 150; n++) {
    await pause(200);
    scene = await call('describe_scene', {maxDistance: 6, rayColumns: 1, rayRows: 1});
    const hit = scene.lookingAt;
    if (hit?.type === 'block' && hit.pos.x === point.x && hit.pos.y === point.y && hit.pos.z === point.z) break;
  }
  assert(scene.lookingAt?.type === 'block' && scene.lookingAt.pos.x === point.x
    && scene.lookingAt.pos.y === point.y && scene.lookingAt.pos.z === point.z,
    `Client must load and actually aim at the marker before clicking: ${JSON.stringify(scene.lookingAt)}`);
  const use = await call('use_item');
  await pause(250);
  return use;
}
try {
  await client.connect(transport);
  report.bridge = await call('get_status');
  const screen = await call('get_screen');
  assert.equal(screen.lastWorldId, report.world, 'Live writes are restricted to the disposable debug copy');
  assert(screen.inWorld, 'The debug world must be loaded');
  verifiedWorld = true;
  await call('close_screen');
  await call('stop_movement');
  report.dimensions = await call('list_dimensions');
  report.missions = await command('expeditions dimensions');
  await command('execute as Dev at @s run expeditions abandon', false);
  previousMobSpawning = (await command('gamerule doMobSpawning')).resultValue !== 0;
  await command('gamerule doMobSpawning false');
  await command('gamemode creative Dev');
  await command('item replace entity Dev weapon.mainhand with minecraft:air');
  // Vanilla /fill reports failure when the fixture already matches.
  await command('execute in minecraft:overworld run fill -4 179 -4 4 179 4 minecraft:stone', false);
  await command('execute in minecraft:overworld run fill -4 180 -4 4 184 4 minecraft:air', false);
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
    test.nativeBiome = await nativeBiome(dimension, test.checkpoint);
    data = await snapshot();
    const active = data.Runs.find(entry => uuid(entry.RunId) === test.runId);
    assert.equal(active.Done, 1, 'The real right-click must activate the checkpoint');
    const tracked = active.ChallengeEntities.map(entry => uuid(entry.EntityId));
    assert(tracked.length > 0, 'Checkpoint must track an actual horde');
    test.horde = [];
    for (const id of tracked) {
      let entity;
      for (let n = 0; n < 40; n++) {
        try { entity = await call('get_entity', {uuid:id}); break; }
        catch (error) {
          if (!error.message.includes('[not_found]') || n === 39) throw error;
          // Spawned entities can wait for a newly arrived player's chunk tickets.
          await pause(200);
        }
      }
      assert.equal(entity.dimension, dimension);
      test.horde.push({uuid: id, type: entity.type, x:entity.x, y:entity.y, z:entity.z});
    }
    assert(test.horde.every(entity => test.nativeBiome.mobs.includes(entity.type)),
      `Horde types must belong to the actual ${test.nativeBiome.id} spawn table`);
    const first = test.horde[0];
    await call('look_at', {x:first.x, y:first.y + .7, z:first.z});
    await pause(200);
    test.screenshot = await call('screenshot');
    await clickMarker(run.Exit, dimension);
    data = await snapshot();
    let ended = data.Runs.find(entry => uuid(entry.RunId) === test.runId);
    for (let n = 0; n < 20 && ended.ChallengeEntities.length > 0; n++) {
      await pause(200);
      ended = (await snapshot()).Runs.find(entry => uuid(entry.RunId) === test.runId);
    }
    assert.equal(ended.Status, 'COMPLETED', 'Extraction must complete the run');
    assert.equal(ended.Rewarded, 1, 'Extraction must record rewards exactly once');
    assert.equal(ended.ChallengeEntities.length, 0, 'Run end must untrack its discarded horde');
    test.status = ended.Status;
    test.tokensAwarded = ended.TokensAwarded;
    test.cleanup = true;
    await waitForClient();
    console.log(`PASS ${dimension}: ${test.horde.length} ${first.type}, reward ${test.tokensAwarded}, horde cleaned`);
    writeFileSync(resolve(directory, 'live-report.json'), JSON.stringify(report, null, 2));
  }
  report.success = true;
} catch (error) {
  report.error = error.stack;
  if (verifiedWorld) {
    try { report.failureState = await call('get_self'); report.failureScreenshot = await call('screenshot'); } catch { }
  }
  console.error(error.message);
  process.exitCode = 1;
} finally {
  writeFileSync(resolve(directory, 'live-report.json'), JSON.stringify(report, null, 2));
  try { await call('stop_movement'); } catch { }
  if (verifiedWorld) {
    try { await command('execute as Dev at @s run expeditions abandon', false); } catch { }
    if (previousMobSpawning !== undefined) {
      try { await command(`gamerule doMobSpawning ${previousMobSpawning}`); } catch { }
    }
  }
  await client.close();
}
