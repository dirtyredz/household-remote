import { copyFileSync, mkdirSync, statSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { execFileSync } from 'node:child_process';
import { runScript } from '../../../tools/script-run/script-run.mjs';
import { SOURCE_REPO, flavors, sha256, version, validateProvenance } from './contract.mjs';
await runScript({ name: 'release-stage', from: import.meta.url }, async log => {
  const tag = process.env.RELEASE_TAG;
  const versionCode = Number(process.env.VERSION_CODE);
  version(tag, versionCode);
  const commit = execFileSync('git', ['rev-parse', 'HEAD'], { cwd: log.root, encoding: 'utf8' }).trim();
  if (process.env.GITHUB_SHA !== commit || process.env.GITHUB_REPOSITORY !== SOURCE_REPO) throw new Error('Stage must run on the selected source commit in CI');
  const output = join(log.root, '.tmp/release-stage');
  mkdirSync(output, { recursive: true });
  const files = {};
  for (const flavor of flavors) for (const extension of ['apk', 'aab']) {
    const source = extension === 'apk' ? `apk/${flavor}/release/app-${flavor}-release-unsigned.apk` : `bundle/${flavor}Release/app-${flavor}-release.aab`;
    const name = `household-${flavor}-unsigned.${extension}`;
    const target = join(output, name);
    copyFileSync(join(log.root, 'src/android/app/build/outputs', source), target);
    files[name] = { size: statSync(target).size, sha256: sha256(target) };
  }
  const provenance = { schema: 1, repository: SOURCE_REPO, tag, versionCode, commit, runId: process.env.GITHUB_RUN_ID, files };
  validateProvenance(provenance, { tag, commit, runId: process.env.GITHUB_RUN_ID });
  writeFileSync(join(output, 'unsigned.json'), JSON.stringify(provenance, null, 2) + '\n');
  log.step('Staged only unsigned flavor artifacts and provenance');
});
