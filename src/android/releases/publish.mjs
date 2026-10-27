import { mkdirSync, mkdtempSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { runScript } from '../../../tools/script-run/script-run.mjs';
import { SOURCE_REPO, PUBLIC_REPO, flavors, sha256, validateProvenance, verifyBytes } from './contract.mjs';
import { androidTools, apkIdentity, apkSigner, run, signApk, signBundle } from './android-tools.mjs';

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
  if (workflow.conclusion !== 'success' || workflow.status !== 'completed' || workflow.event !== 'push' || workflow.path !== '.github/workflows/android-release.yml' || workflow.head_repository.full_name !== SOURCE_REPO) throw new Error('Expected successful source-repo tag-push Android release workflow');
  const commit = gh(['api', `repos/${SOURCE_REPO}/commits/${tag}`, '--jq', '.sha']).trim();
  if (workflow.head_sha !== commit) throw new Error('CI head does not match tag');
  // Bind the reviewed signing record and scripts to the exact source release, not an arbitrary local checkout.
  if (run('git', ['rev-parse', 'HEAD'], { cwd: log.root }).trim() !== commit || run('git', ['status', '--porcelain'], { cwd: log.root }).trim()) throw new Error('Check out the release commit in a clean worktree before signing');
  mkdirSync(join(log.root, '.tmp'), { recursive: true });
  const work = mkdtempSync(join(log.root, '.tmp/release-'));
  const ci = join(work, 'ci'); const draft = join(work, 'draft'); const signed = join(work, 'signed'); const checked = join(work, 'downloaded');
  for (const dir of [ci, draft, signed, checked]) mkdirSync(dir);
  gh(['run', 'download', runId, '-R', SOURCE_REPO, '-n', `unsigned-${tag}`, '-D', ci]);
  const privateRelease = JSON.parse(gh(['release', 'view', tag, '-R', SOURCE_REPO, '--json', 'isDraft,tagName']));
  if (!privateRelease.isDraft || privateRelease.tagName !== tag) throw new Error('Private release must still be a draft');
  gh(['release', 'download', tag, '-R', SOURCE_REPO, '-D', draft]);
  const value = validateProvenance(JSON.parse(readFileSync(join(ci, 'unsigned.json'), 'utf8')), { tag, commit, runId });
  if (sha256(join(ci, 'unsigned.json')) !== sha256(join(draft, 'unsigned.json'))) throw new Error('Draft provenance differs from CI artifact');
  verifyBytes(value, ci, join, statSync); verifyBytes(value, draft, join, statSync);
  const artifacts = [];
  for (const flavor of flavors) {
    const source = join(ci, `household-${flavor}-unsigned.apk`);
    const target = join(signed, `household-${flavor}.apk`);
    const expectedPackage = `com.dirtyredz.householdremote.${flavor}`;
    const identity = apkIdentity(tools, source);
    if (identity.packageName !== expectedPackage || identity.versionCode !== value.versionCode || identity.versionName !== tag.slice(1)) throw new Error('Unsigned APK package/version differs from provenance');
    let alreadySigned = false;
    try { apkSigner(tools, source); alreadySigned = true; } catch { /* expected unsigned */ }
    if (alreadySigned) throw new Error('CI APK is already signed');
    signApk(tools, source, target, key);
    if (apkSigner(tools, target) !== pin) throw new Error('Permanent signing fingerprint mismatch');
    const after = apkIdentity(tools, target);
    if (JSON.stringify(after) !== JSON.stringify(identity)) throw new Error('Signed APK metadata changed');
    signBundle(join(ci, `household-${flavor}-unsigned.aab`), join(signed, `household-${flavor}.aab`), key);
    artifacts.push({ flavor, ...identity, sha256: sha256(target), size: statSync(target).size, url: `https://github.com/${PUBLIC_REPO}/releases/download/${tag}/household-${flavor}.apk` });
  }
  writeFileSync(join(signed, 'update.json'), JSON.stringify({ schema: 1, artifacts }, null, 2) + '\n');
  // Public provenance is deliberately only commit/tag and file hashes, with no inventory or 1Password references.
  const names = [...flavors.flatMap(f => [`household-${f}.apk`, `household-${f}.aab`]), 'update.json'];
  writeFileSync(join(signed, 'SHA256SUMS'), names.map(name => `${sha256(join(signed, name))}  ${name}`).join('\n') + '\n');
  names.push('SHA256SUMS');
  const notes = join(work, 'notes.md');
  writeFileSync(notes, `Household Remote ${tag}\n\nSource commit: ${commit}\n\nManual update; select the flavor already installed. Owner edition has no parent-control features in v1.\n`);
  const repo = JSON.parse(gh(['repo', 'view', PUBLIC_REPO, '--json', 'visibility']));
  if (repo.visibility !== 'PUBLIC') throw new Error('The releases repository must be public');
  const published = JSON.parse(gh(['release', 'list', '-R', PUBLIC_REPO, '--json', 'tagName,isDraft,isPrerelease', '--limit', '100'])).filter(release => !release.isDraft && !release.isPrerelease);
  if (published.length) {
    const previous = join(work, 'previous'); mkdirSync(previous);
    gh(['release', 'download', published[0].tagName, '-R', PUBLIC_REPO, '-p', 'update.json', '-D', previous]);
    const previousManifest = JSON.parse(readFileSync(join(previous, 'update.json'), 'utf8'));
    if (previousManifest.artifacts.some(artifact => artifact.versionCode >= value.versionCode)) throw new Error('Refusing to publish a non-forward version code');
  }
  // Existing tags are never overwritten. A failed publish leaves a reviewable draft for the owner.
  gh(['release', 'create', tag, '-R', PUBLIC_REPO, '--draft', '--title', `Household Remote ${tag}`, '--notes-file', notes, ...names.map(name => join(signed, name))]);
  gh(['release', 'download', tag, '-R', PUBLIC_REPO, '-D', checked]);
  for (const name of names) if (sha256(join(signed, name)) !== sha256(join(checked, name))) throw new Error(`Public download verification failed: ${name}`);
  gh(['release', 'edit', tag, '-R', PUBLIC_REPO, '--draft=false', '--latest']);
  log.step(`Published ${tag} after verifying downloaded bytes; local evidence ${work}`);
});
