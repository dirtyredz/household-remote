import test from 'node:test';
import assert from 'node:assert/strict';
import { privateData, checkTree } from './privacy.mjs';

test('privacy guard detects household subnet and non-placeholder secret references', () => {
  assert.equal(privateData(['192', '168', '68', '123'].join('.')), true);
  assert.equal(privateData('op:' + '//Personal/abcdefghijklmnopqrstuvwxyz/password'), true);
  assert.equal(privateData('op:' + '//Personal/TV token/password'), true);
  assert.equal(privateData('op:' + '//Personal/<private-password-id>/password'), false);
  assert.equal(privateData('192.168.1.5 AA:BB:CC:DD:EE:FF'), false);
});
test('tracked tree and unignored candidate files contain no household data', checkTree);
