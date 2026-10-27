import test from 'node:test';
import assert from 'node:assert/strict';
import { SOURCE_REPO, flavors, validateProvenance, version, sha256, verifyBytes } from './contract.mjs';
import { mkdirSync, mkdtempSync, writeFileSync, statSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
const expected = { tag: 'v0.1.0', commit: 'a'.repeat(40), runId: '123' };
function fixture() { return { schema: 1, repository: SOURCE_REPO, ...expected, versionCode: 1, files: Object.fromEntries(flavors.flatMap(f => ['apk', 'aab'].map(ext => [`household-${f}-unsigned.${ext}`, { sha256: 'b'.repeat(64), size: 100 }]))) }; }
test('release provenance binds source, run, tag and exact four-file allowlist', () => {
  assert.equal(validateProvenance(fixture(), expected).versionCode, 1);
  for (const field of ['tag', 'commit', 'runId', 'repository']) assert.throws(() => validateProvenance({ ...fixture(), [field]: 'wrong' }, expected));
  const malicious = fixture(); malicious.files['../../secret'] = { size: 1, sha256: 'a'.repeat(64) };
  assert.throws(() => validateProvenance(malicious, expected));
  assert.throws(() => version('v1;evil', 1));
  assert.throws(() => version('v1.2.3', 0));
});
test('downloaded artifact verification rejects tampered and truncated bytes', () => {
  const root = fileURLToPath(new URL('../../../.tmp/', import.meta.url));
  mkdirSync(root, { recursive: true });
  const directory = mkdtempSync(join(root, 'contract-'));
  try {
    const file = join(directory, 'household-remote-unsigned.apk');
    writeFileSync(file, 'known bytes');
    const value = { files: { 'household-remote-unsigned.apk': { size: statSync(file).size, sha256: sha256(file) } } };
    verifyBytes(value, directory, join, statSync);
    writeFileSync(file, 'wrong bytes');
    assert.throws(() => verifyBytes(value, directory, join, statSync), /bytes mismatch/);
    writeFileSync(file, 'x');
    assert.throws(() => verifyBytes(value, directory, join, statSync), /bytes mismatch/);
  } finally { rmSync(directory, { recursive: true, force: true }); }
});
