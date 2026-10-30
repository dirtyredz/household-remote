import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { runScript } from '../../../tools/script-run/script-run.mjs';
await runScript({ name: 'icons', from: import.meta.url }, async log => {
  const palette = JSON.parse(readFileSync(join(log.root, 'src/android/icons/palette.json'), 'utf8'));
  const svg = readFileSync(join(log.root, 'src/android/icons/remote.svg'), 'utf8').replaceAll('currentColor', palette.accent);
  const paths = [...svg.matchAll(/<path fill="(#[A-Fa-f0-9]{6})" d="([^"]+)"\s*\/>/g)];
  if (paths.length !== 2) throw new Error('Expected two source SVG paths');
  const vector = '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n' + paths.map(([, color, path]) => `    <path android:fillColor="${color}" android:pathData="${path}" />`).join('\n') + '\n</vector>\n';
  const monochrome = vector.replace(/android:fillColor="#[A-Fa-f0-9]{6}"/g, 'android:fillColor="#FFFFFF"');
  const files = {
    'drawable/ic_remote.xml': vector,
    'drawable/ic_remote_monochrome.xml': monochrome,
    'mipmap-anydpi-v26/ic_launcher.xml': '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n    <background android:drawable="@color/icon_background" />\n    <foreground android:drawable="@drawable/ic_remote" />\n</adaptive-icon>\n',
    'mipmap-anydpi-v33/ic_launcher.xml': '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n    <background android:drawable="@color/icon_background" />\n    <foreground android:drawable="@drawable/ic_remote" />\n    <monochrome android:drawable="@drawable/ic_remote_monochrome" />\n</adaptive-icon>\n',
    'values/colors.xml': `<resources><color name="icon_background">${palette.background}</color></resources>\n`,
    'values/remote_colors.xml': '<!-- Generated from icons/palette.json; do not edit. -->\n<resources>\n' + Object.entries(palette).map(([name, color]) => `    <color name="remote_${name.replace(/[A-Z]/g, c => '_' + c.toLowerCase())}">${color}</color>`).join('\n') + '\n</resources>\n',
  };
  for (const [name, content] of Object.entries(files)) {
    const path = join(log.root, 'src/android/app/src/main/res', name);
    if (log.args.includes('--check')) { if (readFileSync(path, 'utf8') !== content) throw new Error(`Stale icon: ${name}`); }
    else if (!log.dryRun) { mkdirSync(dirname(path), { recursive: true }); writeFileSync(path, content); }
  }
  const kotlinPath = join(log.root, 'src/android/app/src/main/java/com/dirtyredz/householdremote/ui/theme/RemotePalette.kt');
  const kotlin = '// Generated from icons/palette.json; do not edit.\npackage com.dirtyredz.householdremote.ui.theme\n\nimport androidx.compose.ui.graphics.Color\n\ninternal object RemotePalette {\n' + Object.entries(palette).map(([name, color]) => `    val ${name} = Color(0xFF${color.slice(1)})`).join('\n') + '\n}\n';
  if (log.args.includes('--check')) {
    if (readFileSync(kotlinPath, 'utf8') !== kotlin) throw new Error('Stale palette: RemotePalette.kt');
  } else if (!log.dryRun) { writeFileSync(kotlinPath, kotlin); }
  log.step('SVG-derived adaptive icon and shared palette verified');
});
