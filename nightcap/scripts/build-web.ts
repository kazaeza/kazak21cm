import { copyFileSync, cpSync, existsSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';

const root = join(import.meta.dir, '..');
const distDir = join(root, 'dist');
mkdirSync(distDir, { recursive: true });

const result = await Bun.build({
  entrypoints: [join(root, 'src', 'main.ts')],
  outdir: distDir,
  naming: 'app.js',
  target: 'browser',
  minify: true,
  sourcemap: 'none',
});

if (!result.success) {
  console.error('Build failed:');
  for (const log of result.logs) console.error(log);
  process.exit(1);
}

copyFileSync(join(root, 'index.html'), join(distDir, 'index.html'));
copyFileSync(join(root, 'src', 'styles.css'), join(distDir, 'styles.css'));

const fontsSrc = join(root, 'assets', 'fonts');
if (existsSync(fontsSrc)) {
  cpSync(fontsSrc, join(distDir, 'fonts'), { recursive: true });
}

console.log('Built web bundle to dist/:', result.outputs.map((o) => `${o.path} (${(o.size / 1024).toFixed(1)} KB)`).join(', '));
