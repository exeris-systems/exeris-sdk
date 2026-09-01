import { defineConfig } from 'vitest/config';

/**
 * Vitest config for @exeris-systems/ui-kit.
 *
 * This package is overwhelmingly CSS + design tokens; the only TypeScript
 * is an interface declaration and a constant object in src/index.ts, plus
 * the Tailwind preset module. Tests guard that:
 *
 *   - the defaultTheme constant has the documented shape and stays in sync
 *     with the CSS custom properties consumers depend on; and
 *   - the Tailwind preset exposes the expected theme.extend keys (any
 *     accidental rename would silently break downstream `bg-exeris-primary`
 *     class lookups).
 *
 * Coverage thresholds are applied per-file at 85% to match the Java side, and it is
 * worth being exact about what that buys, because it is easy to read as more:
 *
 *   - src/index.ts is ONE statement (the `defaultTheme` const; the interface is a type
 *     and is erased), and tailwind.preset.js is ZERO (a single object literal). Measured,
 *     not estimated — see coverage/coverage-summary.json.
 *   - So for today's two files the threshold cannot fail. It reports that the module was
 *     imported, which the tests would fail without anyway.
 *
 * Its value is therefore prospective: the first source file with real logic is held to
 * 85% from the moment it lands, per-file so the others cannot carry it. What actually
 * detects regressions in the current files is the derived drift tests — theme.test.js
 * (preset vs @theme vs index.css) and default-theme-drift.test.js (defaultTheme vs
 * index.css) — which compare the two sides against each other rather than counting lines.
 */
export default defineConfig({
  test: {
    include: ['src/**/*.test.ts', 'tests/**/*.test.ts', 'tests/**/*.test.js'],
    environment: 'node',
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov', 'json-summary'],
      include: ['src/**/*.ts', 'tailwind.preset.js'],
      exclude: ['src/**/*.test.ts', 'dist/**', 'node_modules/**'],
      thresholds: {
        // perFile so the 85% gate holds for each source file individually
        // (matches the Java per-module intent) rather than aggregating — a
        // future utility file can't be carried by the others' coverage.
        perFile: true,
        lines: 85,
        statements: 85,
        functions: 85,
        branches: 85,
      },
    },
  },
});
