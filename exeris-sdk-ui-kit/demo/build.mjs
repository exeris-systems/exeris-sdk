// Builds the static component demo: compiles the four CSS entries with Tailwind v4, the way a
// consumer's stylesheet would, and writes build/demo/{index.html,kit.css}. Open
// build/demo/index.html in a browser; it needs no server.
import { copyFileSync, mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import postcss from 'postcss';
import tailwind from '@tailwindcss/postcss';

const here = dirname(fileURLToPath(import.meta.url));
const out = join(here, '..', 'build', 'demo');

// source(none): the demo's layout is its own inline CSS, and every class it shows comes from
// the component layer, which Tailwind emits without scanning for candidates.
const input = [
  '@import "tailwindcss" source(none);',
  '@import "../src/styles/theme.css";',
  '@import "../src/styles/index.css";',
  '@import "../src/styles/preview.css";',
  '@import "../src/styles/theme-exeris.css";',
].join('\n');

const { css } = await postcss([tailwind()]).process(input, { from: join(here, 'demo.css') });
mkdirSync(out, { recursive: true });
writeFileSync(join(out, 'kit.css'), css);
copyFileSync(join(here, 'index.html'), join(out, 'index.html'));
console.log(`demo written to ${join(out, 'index.html')}`);
