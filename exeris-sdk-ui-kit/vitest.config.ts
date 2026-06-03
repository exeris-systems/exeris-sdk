import { defineConfig } from 'vitest/config';

/**
 * Vitest config for @exeris/ui-kit.
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
 * Coverage thresholds are applied per-file at 85% to match the Java side.
 * For a package this small a small number of tests reaches the threshold
 * easily — the value of the gate is regression detection, not bulk metric.
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
