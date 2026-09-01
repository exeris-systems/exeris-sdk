import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import preset from '../tailwind.preset.js';
import { defaultTheme } from '../src/index.ts';

/**
 * The names this package promises, pinned.
 *
 * <h2>Why a snapshot, when three derived tests already run</h2>
 * `theme.test.js`, `default-theme-drift.test.js` and `component-classes.test.js` all compare
 * two of this package's artifacts against each other, and they are good at what they do: a
 * token added on one side and forgotten on another fails. None of them notices a *coordinated*
 * rename — change `--exeris-primary` to `--exeris-brand` in the CSS, the preset and the theme
 * entry together and every one of them stays green, because they agree with each other.
 *
 * A coordinated rename is exactly what breaks a consumer. `bg-exeris-primary` sits in generated
 * components and in hand-written application markup; `.exeris-input` is the class
 * `exeris-tooling`'s TypeScript emitter binds to. Renaming one is a breaking change for both,
 * and nothing here could tell.
 *
 * <h2>What this pins, and what it deliberately does not</h2>
 * Names only — every CSS custom property, every `.exeris-*` class, every Tailwind key that
 * produces a utility, and every `defaultTheme` path. **Values are not pinned**: colours,
 * spacing and shadows are the theming surface Studio and a CMS are meant to override, and
 * freezing them would freeze the wrong thing. Values are guarded for *consistency* by the
 * drift tests, never for *constancy*.
 *
 * <h2>The snapshot is meant to be edited</h2>
 * Adding a name is additive and legal: the test reports it and you record it in the same
 * commit. Removing or renaming one is a breaking change for this package's own semver, which
 * is independent of the SDK's — see `MIGRATION-0.x-to-1.0.md`. Take it at a major, on purpose.
 */
const root = dirname(dirname(fileURLToPath(import.meta.url)));
const SNAPSHOT = join(root, 'tests/public-surface.txt');
const indexCss = readFileSync(join(root, 'src/styles/index.css'), 'utf8');

/** Every name this package exposes, as a sorted, stable list of `kind:name` entries. */
function surface() {
  const names = new Set();

  for (const [, token] of indexCss.matchAll(/(--exeris-[a-z-]+):/g)) {
    names.add(`token:${token}`);
  }
  for (const [, cls] of indexCss.matchAll(/\.(exeris-[a-z-]+)/g)) {
    names.add(`class:${cls}`);
  }

  const extend = preset.theme.extend;
  for (const [group, values] of Object.entries(extend)) {
    const keys = group === 'colors' ? Object.keys(values.exeris).map((k) => `exeris.${k}`)
                                    : Object.keys(values);
    for (const key of keys) names.add(`preset:${group}.${key}`);
  }

  for (const [group, values] of Object.entries(defaultTheme)) {
    for (const key of Object.keys(values)) names.add(`theme:${group}.${key}`);
  }

  return [...names].sort();
}

describe('public surface', () => {
  it('names are added deliberately and never renamed by accident', () => {
    const recorded = readFileSync(SNAPSHOT, 'utf8')
      .split('\n').map((l) => l.trim()).filter((l) => l && !l.startsWith('#'));
    const actual = surface();

    expect(actual, 'an empty walk would make this vacuous').toHaveLength(actual.length);
    expect(actual.length).toBeGreaterThan(50);

    const removed = recorded.filter((n) => !actual.includes(n));
    const added = actual.filter((n) => !recorded.includes(n));

    expect(removed, 'A name disappeared. Consumers reference these in markup and generated '
      + 'components, so this is a breaking change for @exeris/ui-kit — take it at a major and '
      + 're-baseline tests/public-surface.txt, do not quietly drop the line.').toEqual([]);
    expect(added, 'A name is new and unrecorded. Additive and fine — record it in '
      + 'tests/public-surface.txt in this commit so the next change to it is gated.').toEqual([]);
  });
});
