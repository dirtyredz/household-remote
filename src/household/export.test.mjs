import test from 'node:test';
import assert from 'node:assert/strict';
import { exportHousehold } from './export.mjs';
test('explicit whitelist never exports token, account, pairing or password references', () => {
  const result = exportHousehold({ default: 'example', tvs: { example: { name: 'Example', ip: '192.168.1.2', mac: 'aa:bb:cc:dd:ee:ff', model: 'Test', token: 'SECRET', onePassword: { item: 'SECRET' }, paired: true } } });
  assert.deepEqual(Object.keys(result.tvs[0]), ['id', 'name', 'model', 'mac', 'ip']);
  assert.equal(JSON.stringify(result).includes('SECRET'), false);
});
