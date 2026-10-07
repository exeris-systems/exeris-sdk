# Contributing to @exeris/ui-kit

This page is for changing the package in this repository. To use the package, read the
[README](README.md). Running the lint locally needs a clone of the organisation's guardrails
repository, described below.

These commands guard the package's published surface. CI runs all of them: the lint, typedoc and
api-extractor checks as `tsdoc-gate` in `.github/workflows/tsdoc.yml`, after the build they
read, and the tests with the coverage gate in `.github/workflows/build.yml`:

```bash
npm run lint         # TSDoc rules — the shared exeris-systems/.github fragment
npm run build        # tsc, which the next two read
npm run docs:check   # typedoc: every export and every token documented
npm run api:check    # api-extractor: api/ui-kit.api.md matches the built .d.ts
npm test             # the name-snapshot, drift and Tailwind v4 compile suites
```

`npm run lint` loads its rules from [`exeris-systems/.github`](https://github.com/exeris-systems/.github),
which has to sit at `./.guardrails` — the path the gate checks it out to. Once, from this
directory:

```bash
git clone --depth 1 https://github.com/exeris-systems/.github .guardrails
```

If you already have that repository cloned, a worktree of your clone at the same path works
too, and `git pull` then keeps both current.

It has to be a real directory, **not a symlink** to a checkout elsewhere. Node resolves the
config's realpath before it looks for `node_modules`, so through a symlink it searches beside
the bundle instead of beside this package and never finds `eslint-plugin-jsdoc`. `.guardrails`
is git-ignored.

When a change to `src/index.ts` alters the surface, accept it deliberately and commit the
result — `npm run api:accept` rewrites `api/ui-kit.api.md`. A `-` line in that diff removes
a name, which is a **major** for this package (see [Versioning](README.md#versioning)).
