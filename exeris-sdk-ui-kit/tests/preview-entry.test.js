import { describe, expect, it } from 'vitest';
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { PACKAGE_ROOT } from './support/tailwind.js';

/**
 * `@exeris/ui-kit/preview` holds the component classes no generator emits yet. They are outside
 * the names the package freezes at its 1.0, so the split has to stay clean: a class lives in
 * exactly one of the two entries, the frozen snapshot never names a preview class, and a class
 * moves from preview to styles — additively — only when a generator uses it.
 */
const read = (path) => readFileSync(join(PACKAGE_ROOT, path), 'utf8');
const stripComments = (css) => css.replace(/\/\*[\s\S]*?\*\//g, '');
const classesIn = (css) => new Set([...stripComments(css).matchAll(/\.(exeris-[a-z0-9-]+)/g)].map(([, name]) => name));

const indexCss = read('src/styles/index.css');
const previewCss = read('src/styles/preview.css');
const stable = classesIn(indexCss);
const preview = classesIn(previewCss);

/** The `@apply` list of the first rule whose selector is `selector`, whitespace collapsed. */
function applyOf(css, selector) {
  const body = stripComments(css);
  const start = body.indexOf(`${selector} {`);
  if (start < 0) throw new Error(`no '${selector}' rule`);
  const rule = body.slice(start, body.indexOf('}', start));
  const match = rule.match(/@apply([^;]+);/);
  if (!match) throw new Error(`'${selector}' has no @apply`);
  return match[1].trim().replace(/\s+/g, ' ');
}

describe('@exeris/ui-kit/preview', () => {
  it('is exported and points at a file that exists', () => {
    const pkg = JSON.parse(read('package.json'));
    expect(pkg.exports['./preview']).toBe('./src/styles/preview.css');
    expect(existsSync(join(PACKAGE_ROOT, 'src/styles/preview.css'))).toBe(true);
  });

  it('declares classes, and none of them is also declared in …/styles', () => {
    expect(preview.size, 'an empty preview entry makes this guard vacuous').toBeGreaterThan(20);
    const both = [...preview].filter((name) => stable.has(name));
    expect(both, 'a class declared in both entries has two definitions and an unclear freeze status')
      .toEqual([]);
  });

  it('is not in the frozen snapshot', () => {
    const frozen = read('tests/public-surface.txt').split('\n').map((line) => line.trim());
    const listed = [...preview].filter((name) => frozen.includes(`class:${name}`));
    expect(listed, 'a preview class is pinned as frozen; move it to …/styles or drop the line').toEqual([]);
  });

  it('gives .exeris-textarea the field base of .exeris-input and .exeris-select', () => {
    // Tailwind v4 cannot @apply a custom class, so the textarea repeats the field base; a field
    // edited in one entry and not the other would render a textarea unlike the inputs beside it.
    expect(applyOf(previewCss, '.exeris-textarea')).toBe(applyOf(indexCss, '.exeris-input,\n  .exeris-select'));
  });
});
