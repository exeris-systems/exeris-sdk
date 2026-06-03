# Contributing to Exeris SDK

## Build & test

```bash
mvn clean install            # full reactor build + JaCoCo 85% gate
mvn -pl exeris-sdk-source-model-io -am verify   # one module + deps

cd exeris-sdk-ui-kit && npm ci && npm run test:coverage   # ui-kit (npm-only)
```

JDK **26** is the floor across the reactor — see `README.md` / `ROADMAP.md` for
the rationale (do not lower `maven.compiler.release`).

## Dependencies & licensing

The SDK is **Apache-2.0** and publishes to Maven Central. Two rules keep that
clean and keep the zero-runtime-coupling invariant intact:

1. **Zero runtime coupling.** `exeris-sdk-annotations` has no compile/runtime
   dependencies; `exeris-sdk-source-model` pulls only `jackson-annotations`.
   Heavy dependencies live in leaf modules that pure AST-record consumers don't
   pull. In particular (ADR-037) **JavaParser is a compile dependency of
   `exeris-sdk-source-model-io` only** — it must never appear on the dependency
   tree of `annotations` or `source-model`.

2. **JavaParser license election.** `com.github.javaparser:javaparser-core` is
   dual-licensed **Apache-2.0 OR LGPL-3.0**. The SDK **elects Apache-2.0**. When
   bumping the `javaparser.version` property in `exeris-sdk-bom`, keep the
   election comment on that entry and do not introduce an LGPL-only variant.

## Conventions

- Code, identifiers, comments, commit messages, PR titles/bodies: **English**.
- AST records are a wire-format contract — adding a field to `DomainMetadata`
  is a contract change (see `CLAUDE.md` and `AstJsonRoundTripTest`).
- Public-API removals/renames go through the deprecation pipeline in
  `MIGRATION.md`.
