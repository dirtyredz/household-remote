import { join } from 'node:path';
import { statSync } from 'node:fs';
import { SOURCE_REPO, flavors, sha256 } from './contract.mjs';
import { apkIdentity, apkSigner, signApk, signBundle } from './android-tools.mjs';

export function verifySource({ workflow, commit, localCommit, localDirty, sourceRelease, tag }) {
  if (workflow.conclusion !== 'success' || workflow.status !== 'completed' || workflow.event !== 'push' || workflow.path !== '.github/workflows/android-release.yml' || workflow.head_repository.full_name !== SOURCE_REPO) throw new Error('Expected successful source-repo tag-push Android release workflow');
  if (workflow.head_sha !== commit) throw new Error('CI head does not match tag');
  if (localCommit !== commit || localDirty) throw new Error('Check out the release commit in a clean worktree before signing');
  if (!sourceRelease.isDraft || sourceRelease.tagName !== tag) throw new Error('Unsigned release must still be a draft');
}

export function signFlavors({ tools, ci, signed, key, pin, versionCode, versionName }) {
  const artifacts = [];
  for (const flavor of flavors) {
    const source = join(ci, `household-${flavor}-unsigned.apk`);
    const target = join(signed, `household-${flavor}.apk`);
    const expectedPackage = `com.dirtyredz.householdremote.${flavor}`;
    const identity = apkIdentity(tools, source);
    if (identity.packageName !== expectedPackage || identity.versionCode !== versionCode || identity.versionName !== versionName) throw new Error('Unsigned APK package/version differs from provenance');
    let alreadySigned = false;
    try { apkSigner(tools, source); alreadySigned = true; } catch { /* expected unsigned */ }
    if (alreadySigned) throw new Error('CI APK is already signed');
    signApk(tools, source, target, key);
    if (apkSigner(tools, target) !== pin) throw new Error('Permanent signing fingerprint mismatch');
    const after = apkIdentity(tools, target);
    if (JSON.stringify(after) !== JSON.stringify(identity)) throw new Error('Signed APK metadata changed');
    signBundle(join(ci, `household-${flavor}-unsigned.aab`), join(signed, `household-${flavor}.aab`), key);
    artifacts.push({ flavor, ...identity, sha256: sha256(target), size: statSync(target).size, url: `https://github.com/${SOURCE_REPO}/releases/download/v${versionName}/household-${flavor}.apk` });
  }
  return artifacts;
}

export function buildManifest(artifacts) {
  if (artifacts.length !== flavors.length || artifacts.map(value => value.flavor).sort().join('|') !== [...flavors].sort().join('|')) throw new Error('Expected exactly one signed artifact per flavor');
  for (const artifact of artifacts) {
    if (artifact.packageName !== `com.dirtyredz.householdremote.${artifact.flavor}` || !Number.isSafeInteger(artifact.versionCode) || artifact.versionCode < 1 || artifact.versionCode > 2100000000 || !/^\d+\.\d+\.\d+$/.test(artifact.versionName) || !/^[a-f0-9]{64}$/.test(artifact.sha256) || !Number.isSafeInteger(artifact.size) || artifact.size < 1 || artifact.size > 150000000) throw new Error('Invalid signed artifact metadata');
    if (artifact.url !== `https://github.com/${SOURCE_REPO}/releases/download/v${artifact.versionName}/household-${artifact.flavor}.apk`) throw new Error('Invalid signed artifact URL');
  }
  if (new Set(artifacts.map(value => `${value.versionCode}/${value.versionName}`)).size !== 1) throw new Error('Signed flavor versions differ');
  return { schema: 1, artifacts };
}

export function assertForwardVersion(versionCode, previousManifest) {
  if (!Number.isSafeInteger(versionCode) || !Array.isArray(previousManifest?.artifacts) || previousManifest.artifacts.length !== flavors.length || previousManifest.artifacts.some(artifact => !Number.isSafeInteger(artifact.versionCode) || artifact.versionCode >= versionCode)) throw new Error('Refusing to publish a non-forward version code');
}
