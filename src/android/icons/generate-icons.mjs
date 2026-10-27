import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { runScript } from '../../../tools/script-run/script-run.mjs';
await runScript({ name: 'icons', from: import.meta.url }, async log => {
  const svg = readFileSync(join(log.root, 'src/android/icons/remote.svg'), 'utf8');
  const paths = [...svg.matchAll(/<path fill="(#[A-Fa-f0-9]{6})" d="([^"]+)"\s*\/>/g)];
  if (paths.length !== 2) throw new Error('Expected two source SVG paths');
  const vector = '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n' + paths.map(([, color, path]) => `    <path android:fillColor="${color}" android:pathData="${path}" />`).join('\n') + '\n</vector>\n';
  const files = {
    'drawable/ic_remote.xml': vector,
    'mipmap-anydpi-v26/ic_launcher.xml': '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n    <background android:drawable="@color/icon_background" />\n    <foreground android:drawable="@drawable/ic_remote" />\n</adaptive-icon>\n',
    'values/colors.xml': '<resources><color name="icon_background">#123040</color></resources>\n',
  };
  for (const [name, content] of Object.entries(files)) {
    const path = join(log.root, 'src/android/app/src/main/res', name);
    if (log.args.includes('--check')) { if (readFileSync(path, 'utf8') !== content) throw new Error(`Stale icon: ${name}`); }
    else if (!log.dryRun) { mkdirSync(dirname(path), { recursive: true }); writeFileSync(path, content); }
  }
  log.step('SVG-derived adaptive icon verified');
});
