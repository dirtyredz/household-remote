import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

export function privateData(text) {
  const subnet = ['192', '168', '68', ''].join('.');
  if (text.includes(subnet)) return true;
  // Only explicit angle-bracket placeholders are allowed in documentation.
  return [...text.matchAll(/op:\/\/[^\s"'`]+/g)].some(([reference]) =>
    !/^op:\/\/[^/]+\/<[^>]+>\//.test(reference));
}

export function checkTree() {
  const root = fileURLToPath(new URL('../../', import.meta.url));
  const files = execFileSync('git', ['ls-files', '-z', '--cached', '--others', '--exclude-standard'], { cwd: root, encoding: 'utf8' }).split('\0').filter(Boolean);
  const violations = [...new Set(files)].filter(file => {
    try { return privateData(readFileSync(new URL(file.replaceAll('\\', '/'), new URL('../../', import.meta.url)), 'utf8')); }
    catch (error) { if (error.code === 'ENOENT') return false; throw error; }
  });
  if (violations.length) throw new Error(`Private household data in tracked/candidate files: ${violations.join(', ')}`);
}
