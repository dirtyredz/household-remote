import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve, sep } from 'node:path';
import { pathToFileURL } from 'node:url';
import { runScript } from '../../tools/script-run/script-run.mjs';

export function exportHousehold(inventory) {
  const tvs = Object.entries(inventory.tvs).map(([id, tv]) => ({ id, name: tv.name, model: tv.model ?? '', mac: tv.mac.toUpperCase(), ip: tv.ip }));
  if (!tvs.length || tvs.length > 32 || tvs.some(tv => !/^[\w-]{1,64}$/.test(tv.id) || !/^([A-F0-9]{2}:){5}[A-F0-9]{2}$/.test(tv.mac) || !tv.name)) throw new Error('Invalid inventory');
  return { tvs, selected: inventory.default ?? null };
}
if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  await runScript({ name: 'household-export', from: import.meta.url }, async log => {
    const [input, output] = log.args;
    if (!input || !output) throw new Error('Usage: node src/household/export.mjs <inventory.json> <output.json> (relative paths resolve from repo root)');
    const result = exportHousehold(JSON.parse(readFileSync(resolve(log.root, input), 'utf8')));
    const destination = resolve(log.root, output);
    if (!destination.startsWith(resolve(log.root, '.tmp') + sep)) throw new Error('Export must stay under this repo .tmp/');
    if (!log.dryRun) { mkdirSync(dirname(destination), { recursive: true }); writeFileSync(destination, JSON.stringify(result, null, 2) + '\n'); }
    log.step(`Exported ${result.tvs.length} TVs; credentials omitted`);
  });
}
