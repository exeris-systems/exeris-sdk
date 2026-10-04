# Contributing to @exeris/ui-kit

Three checks guard this package's published surface, and CI runs all three
(`tsdoc-gate` in `.github/workflows/guardrails.yml`):

```bash
npm run lint         # TSDoc rules — the shared exeris-systems/.github fragment
npm run build        # tsc, which the next two read
npm run docs:check   # typedoc: every export and every token documented
npm run api:check    # api-extractor: api/ui-kit.api.md matches the built .d.ts
npm test             # the name-snapshot and drift suites
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
