// Flat ESLint config for @exeris-systems/ui-kit.
//
// The doc-comment rules are NOT written here: they come from the shared bundle
// (exeris-systems/.github), and this import IS the adoption — a flat config cannot be injected
// from outside, so tsdoc-gate.yml greps this file for the reference rather than assuming it.
// `.guardrails` is the bundle: checked out there by the workflow, a local symlink otherwise.
import tseslint from 'typescript-eslint';
import exerisTsdoc from './.guardrails/ts/eslint.tsdoc.mjs';

export default tseslint.config(
  { ignores: ['dist/**', 'coverage/**', 'build/**', '.guardrails/**', 'tailwind.preset.js'] },
  ...tseslint.configs.recommended,
  // `gated` names the published surface — tsdoc-conventions.md rule 1 is an error there and a
  // warning elsewhere. `src/index.ts` is the whole of it: package.json points `types` at the
  // declarations built from this one file.
  ...exerisTsdoc({ gated: ['src/index.ts'] }),
);
