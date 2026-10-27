import { mkdirSync, mkdtempSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { runScript } from '../../../tools/script-run/script-run.mjs';
import { SOURCE_REPO, flavors, sha256, validateProvenance, verifyBytes } from './contract.mjs';
import { androidTools, run } from './android-tools.mjs';
import { publishDraft } from './draft.mjs';
import { assertForwardVersion, buildManifest, signFlavors, verifySource } from './publication.mjs';

await runScript({ name: 'publish-release', from: import.meta.url, kind: 'owner' }, async log => {
  const [tag, runId] = log.args;
  if (!/^v\d+\.\d+\.\d+$/.test(tag) || !/^\d+$/.test(runId)) throw new Error('Expected version tag and CI run ID');
  if (log.dryRun) { log.step('Would verify, sign and publish selected release'); return; }
  const tools = androidTools(process.env.ANDROID_HOME);
  const key = process.env.HOUSEHOLD_KEY_FILE;
  if (!key || !process.env.HOUSEHOLD_SIGN_PASSWORD) throw new Error('Use attended scripts/release/sign-and-publish.ps1');
  const pin = JSON.parse(readFileSync(join(log.root, 'src/android/releases/signing-certificate.json'), 'utf8').replace(/^\uFEFF/, '')).certificateSha256;
  if (!/^[a-f0-9]{64}$/.test(pin ?? '')) throw new Error('Provision and commit permanent signing fingerprint first');
  const gh = args => run('gh', args, { cwd: log.root });
  const workflow = JSON.parse(gh(['api', `repos/${SOURCE_REPO}/actions/runs/${runId}`]));
  const commit = gh(['api', `repos/${SOURCE_REPO}/commits/${tag}`, '--jq', '.sha']).trim();
  const localCommit = run('git', ['rev-parse', 'HEAD'], { cwd: log.root }).trim();
  const localDirty = run('git', ['status', '--porcelain'], { cwd: log.root }).trim();
  const sourceRelease = JSON.parse(gh(['release', 'view', tag, '-R', SOURCE_REPO, '--json', 'isDraft,tagName,assets']));
  verifySource({ workflow, commit, localCommit, localDirty, sourceRelease, tag });
  mkdirSync(join(log.root, '.tmp'), { recursive: true });
  const work = mkdtempSync(join(log.root, '.tmp/release-'));
  const ci = join(work, 'ci'); const draft = join(work, 'draft'); const signed = join(work, 'signed'); const checked = join(work, 'downloaded');
  for (const dir of [ci, draft, signed, checked]) mkdirSync(dir);
  gh(['run', 'download', runId, '-R', SOURCE_REPO, '-n', `unsigned-${tag}`, '-D', ci]);
  gh(['release', 'download', tag, '-R', SOURCE_REPO, '-D', draft]);
  const value = validateProvenance(JSON.parse(readFileSync(join(ci, 'unsigned.json'), 'utf8')), { tag, commit, runId });
  if (sha256(join(ci, 'unsigned.json')) !== sha256(join(draft, 'unsigned.json'))) throw new Error('Draft provenance differs from CI artifact');
  verifyBytes(value, ci, join, statSync); verifyBytes(value, draft, join, statSync);
  const artifacts = signFlavors({ tools, ci, signed, key, pin, versionCode: value.versionCode, versionName: tag.slice(1) });
  writeFileSync(join(signed, 'update.json'), JSON.stringify(buildManifest(artifacts), null, 2) + '\n');
  // Public provenance is deliberately only commit/tag and file hashes, with no inventory or 1Password references.
  const names = [...flavors.flatMap(f => [`household-${f}.apk`, `household-${f}.aab`]), 'update.json'];
  writeFileSync(join(signed, 'SHA256SUMS'), names.map(name => `${sha256(join(signed, name))}  ${name}`).join('\n') + '\n');
  names.push('SHA256SUMS');
  const notes = join(work, 'notes.md');
  writeFileSync(notes, `Household Remote ${tag}\n\nSource commit: ${commit}\n\nManual update; select the flavor already installed. Owner edition has no parent-control features in v1.\n`);
  const repo = JSON.parse(gh(['repo', 'view', SOURCE_REPO, '--json', 'visibility']));
  if (repo.visibility !== 'PUBLIC') throw new Error('The releases repository must be public');
  const published = JSON.parse(gh(['release', 'list', '-R', SOURCE_REPO, '--json', 'tagName,isDraft,isPrerelease', '--limit', '100'])).filter(release => !release.isDraft && !release.isPrerelease);
  if (published.length) {
    const previous = join(work, 'previous'); mkdirSync(previous);
    gh(['release', 'download', published[0].tagName, '-R', SOURCE_REPO, '-p', 'update.json', '-D', previous]);
    const previousManifest = JSON.parse(readFileSync(join(previous, 'update.json'), 'utf8'));
    assertForwardVersion(value.versionCode, previousManifest);
  }
  // Mutate only the verified CI draft. Never overwrite a published release or unknown assets.
  const unsignedNames = [...Object.keys(value.files), 'unsigned.json', 'SHA256SUMS'];
  publishDraft({ gh, repo: SOURCE_REPO, tag, release: sourceRelease, unsignedNames, names,
    signedPath: name => join(signed, name), checkedPath: checked, notes,
    verify: () => {
      for (const name of names) if (sha256(join(signed, name)) !== sha256(join(checked, name))) throw new Error(`Public download verification failed: ${name}`);
    } });
  log.step(`Published ${tag} after verifying downloaded bytes; local evidence ${work}`);
});
