import { spawnSync } from 'node:child_process';
import { join } from 'node:path';
import { copyFileSync, existsSync } from 'node:fs';

export function run(file, args, options = {}) {
  const result = spawnSync(file, args, { encoding: 'utf8', windowsHide: true, ...options });
  if (result.error || result.status !== 0) throw new Error(`${file} failed: ${result.error?.message ?? (result.stderr || result.stdout).slice(0, 3000)}`);
  return result.stdout;
}
export function androidTools(sdk) {
  if (!sdk) throw new Error('ANDROID_HOME must point to the installed SDK');
  const dir = join(sdk, 'build-tools/35.0.0');
  const aapt = join(dir, process.platform === 'win32' ? 'aapt.exe' : 'aapt');
  const zipalign = join(dir, process.platform === 'win32' ? 'zipalign.exe' : 'zipalign');
  const signer = join(dir, 'lib/apksigner.jar');
  for (const path of [aapt, zipalign, signer]) if (!existsSync(path)) throw new Error(`Missing installed Android tool: ${path}`);
  return { aapt, zipalign, signer };
}
export function apkIdentity(tools, file) {
  const badging = run(tools.aapt, ['dump', 'badging', file]);
  const match = /^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'/m.exec(badging);
  if (!match) throw new Error('APK metadata unavailable');
  return { packageName: match[1], versionCode: Number(match[2]), versionName: match[3] };
}
export function apkSigner(tools, file) {
  const output = run('java', ['-jar', tools.signer, 'verify', '--verbose', '--print-certs', file]);
  const matches = [...output.matchAll(/Signer #\d+ certificate SHA-256 digest: ([a-f0-9]+)/gi)];
  if (matches.length !== 1) throw new Error('Expected exactly one APK signer');
  return matches[0][1].toLowerCase();
}
export function signApk(tools, source, target, key) {
  const aligned = `${target}.aligned`;
  run(tools.zipalign, ['-f', '-p', '4', source, aligned]);
  run('java', ['-jar', tools.signer, 'sign', '--ks', key, '--ks-key-alias', 'household', '--ks-pass', 'env:HOUSEHOLD_SIGN_PASSWORD', '--key-pass', 'env:HOUSEHOLD_SIGN_PASSWORD', '--out', target, aligned]);
  run(tools.zipalign, ['-c', '-p', '4', target]);
}
export function signBundle(source, target, key) {
  copyFileSync(source, target);
  run('jarsigner', ['-keystore', key, '-storepass:env', 'HOUSEHOLD_SIGN_PASSWORD', '-keypass:env', 'HOUSEHOLD_SIGN_PASSWORD', target, 'household']);
  const output = run('jarsigner', ['-verify', target]);
  if (!output.includes('jar verified.')) throw new Error('Bundle signature verification failed');
}
