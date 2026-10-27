import { mkdirSync, mkdtempSync, rmSync } from 'node:fs';
import { join, resolve, sep } from 'node:path';
import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { runScript } from '../../../tools/script-run/script-run.mjs';
import { androidTools, apkIdentity, apkSigner, run, signApk, signBundle } from './android-tools.mjs';
await runScript({ name: 'signing-smoke', from: import.meta.url }, async log => {
  const tools = androidTools(process.env.ANDROID_HOME);
  mkdirSync(join(log.root, '.tmp'), { recursive: true });
  const work = mkdtempSync(join(log.root, '.tmp/signing-smoke-'));
  process.env.HOUSEHOLD_SIGN_PASSWORD = randomBytes(24).toString('hex');
  log.addSecrets(process.env.HOUSEHOLD_SIGN_PASSWORD);
  try {
    const keys = [join(work, 'first.p12'), join(work, 'wrong.p12')];
    for (const key of keys) run('keytool', ['-genkeypair', '-keystore', key, '-storetype', 'PKCS12', '-alias', 'household', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '2', '-dname', 'CN=Disposable Smoke Only', '-storepass:env', 'HOUSEHOLD_SIGN_PASSWORD', '-keypass:env', 'HOUSEHOLD_SIGN_PASSWORD']);
    let pinned;
    for (const flavor of ['remote', 'owner']) {
      const debug = join(log.root, `src/android/app/build/outputs/apk/${flavor}/debug/app-${flavor}-debug.apk`);
      assert.equal(apkIdentity(tools, debug).packageName, `com.dirtyredz.householdremote.${flavor}.debug`);
      assert.match(apkSigner(tools, debug), /^[a-f0-9]{64}$/);
      const source = join(log.root, `src/android/app/build/outputs/apk/${flavor}/release/app-${flavor}-release-unsigned.apk`);
      assert.throws(() => apkSigner(tools, source), 'Release must be unsigned');
      const identity = apkIdentity(tools, source);
      assert.equal(identity.packageName, `com.dirtyredz.householdremote.${flavor}`);
      const target = join(work, `${flavor}.apk`);
      signApk(tools, source, target, keys[0]);
      const signer = apkSigner(tools, target);
      if (pinned) assert.equal(signer, pinned); else pinned = signer;
      assert.deepEqual(apkIdentity(tools, target), identity);
      const wrong = join(work, `${flavor}-wrong.apk`);
      signApk(tools, source, wrong, keys[1]);
      assert.notEqual(apkSigner(tools, wrong), pinned);
      signBundle(join(log.root, `src/android/app/build/outputs/bundle/${flavor}Release/app-${flavor}-release.aab`), join(work, `${flavor}.aab`), keys[0]);
      log.step(`${flavor}: debug APK signature/package, unsigned release input, aligned signed APK, package/version retention, signer equality and wrong-key rejection, signed AAB verified`);
    }
    console.log('PASS: both flavors signed and verified; wrong key distinguished; APK metadata preserved; AAB signatures verified');
  } finally {
    delete process.env.HOUSEHOLD_SIGN_PASSWORD;
    if (!resolve(work).startsWith(resolve(log.root, '.tmp') + sep + 'signing-smoke-')) throw new Error('Unsafe smoke cleanup path');
    rmSync(work, { recursive: true, force: true });
  }
});
