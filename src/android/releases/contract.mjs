import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';

export const SOURCE_REPO = 'dirtyredz/household-remote';
export const PUBLIC_REPO = SOURCE_REPO;
export const flavors = ['remote', 'owner'];
export const sha256 = file => createHash('sha256').update(readFileSync(file)).digest('hex');
export function version(tag, code) {
  if (!/^v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/.test(tag) || !Number.isSafeInteger(code) || code < 1 || code > 2100000000) throw new Error('Invalid tag/version code');
  return tag.slice(1);
}
export function validateProvenance(value, { tag, commit, runId }) {
  version(tag, value.versionCode);
  if (value.schema !== 1 || value.tag !== tag || value.commit !== commit || value.runId !== String(runId) || value.repository !== SOURCE_REPO || !/^[a-f0-9]{40}$/.test(commit)) throw new Error('Provenance does not match successful CI run/tag');
  const expected = flavors.flatMap(flavor => [`household-${flavor}-unsigned.apk`, `household-${flavor}-unsigned.aab`]);
  if (Object.keys(value.files ?? {}).sort().join('|') !== expected.sort().join('|')) throw new Error('Unexpected release file set');
  for (const entry of Object.values(value.files)) if (!/^[a-f0-9]{64}$/.test(entry.sha256) || !Number.isSafeInteger(entry.size) || entry.size < 1 || entry.size > 150000000) throw new Error('Invalid artifact digest/size');
  return value;
}
export function verifyBytes(value, directory, pathJoin, stat) {
  for (const [name, entry] of Object.entries(value.files)) {
    const file = pathJoin(directory, name);
    if (stat(file).size !== entry.size || sha256(file) !== entry.sha256) throw new Error(`Artifact bytes mismatch: ${name}`);
  }
}
