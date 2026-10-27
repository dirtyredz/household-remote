import test from 'node:test';
import assert from 'node:assert/strict';
import { SOURCE_REPO, flavors } from './contract.mjs';
import { assertForwardVersion, buildManifest, verifySource } from './publication.mjs';

const commit = 'a'.repeat(40);
const workflow = {
  conclusion: 'success', status: 'completed', event: 'push',
  path: '.github/workflows/android-release.yml',
  head_repository: { full_name: SOURCE_REPO }, head_sha: commit
};

test('source verification binds successful CI, tag draft, and clean local commit', () => {
  const input = { workflow, commit, localCommit: commit, localDirty: '', sourceRelease: { isDraft: true, tagName: 'v1.2.3' }, tag: 'v1.2.3' };
  assert.doesNotThrow(() => verifySource(input));
  assert.throws(() => verifySource({ ...input, localDirty: ' M publish.mjs' }), /clean worktree/);
  assert.throws(() => verifySource({ ...input, sourceRelease: { isDraft: false, tagName: 'v1.2.3' } }), /draft/);
  assert.throws(() => verifySource({ ...input, workflow: { ...workflow, head_sha: 'b'.repeat(40) } }), /CI head/);
});

function artifact(flavor) {
  return {
    flavor,
    packageName: `com.dirtyredz.householdremote.${flavor}`,
    versionCode: 12,
    versionName: '1.2.3',
    sha256: 'b'.repeat(64),
    size: 123,
    url: `https://github.com/${SOURCE_REPO}/releases/download/v1.2.3/household-${flavor}.apk`
  };
}

test('manifest construction requires exact signed flavor metadata', () => {
  const artifacts = flavors.map(artifact);
  assert.deepEqual(buildManifest(artifacts), { schema: 1, artifacts });
  assert.throws(() => buildManifest([artifact('remote')]), /exactly one/);
  assert.throws(() => buildManifest([artifact('remote'), artifact('remote')]), /exactly one/);
  assert.throws(() => buildManifest([artifact('remote'), { ...artifact('owner'), url: 'https://example.test/owner.apk' }]), /URL/);
  assert.throws(() => buildManifest([artifact('remote'), { ...artifact('owner'), versionCode: 13 }]), /versions differ/);
});

test('forward version check rejects equal or older version codes', () => {
  assert.doesNotThrow(() => assertForwardVersion(12, { artifacts: [{ versionCode: 11 }, { versionCode: 11 }] }));
  assert.throws(() => assertForwardVersion(12, { artifacts: [{ versionCode: 12 }, { versionCode: 11 }] }), /non-forward/);
  assert.throws(() => assertForwardVersion(12, { artifacts: [{ versionCode: 13 }, { versionCode: 11 }] }), /non-forward/);
});
