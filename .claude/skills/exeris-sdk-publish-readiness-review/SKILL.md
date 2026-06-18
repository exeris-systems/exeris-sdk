---
name: exeris-sdk-publish-readiness-review
description: Maven Central publish-readiness review for exeris-sdk. Use whenever you bump a module version, touch `<distributionManagement>` or required POM metadata (`<url>`, `<licenses>`, `<developers>`, `<scm>`, `<issueManagement>`, `<organization>`), or add a new publishable Maven module — before a release goes out.
---

# Exeris SDK Maven Central Publish-Readiness Review

## Purpose
Enforce: the SDK publishes to **Sonatype Central Portal** (NOT GitHub Packages). Downstream Exeris repos resolve SDK artifacts from Maven Central and pin exact 0.x versions. A version bump shipped with an empty required POM field, a missing `-sources`/`-javadoc` jar, or a wrong `<distributionManagement>` endpoint fails Central validation at the publish step — after the tag is cut.

## When to Use
- Any change bumping a module version (root / BOM / parent / publishable jar).
- Any change touching `<distributionManagement>` (Sonatype Central Portal endpoints).
- Any change touching required POM metadata: `<url>`, `<organization>`, `<licenses>`, `<developers>`, `<scm>`, `<issueManagement>`.
- Any change adding a new publishable Maven module.
- Any change to the reactor `<modules>` list (e.g. risk of re-adding `exeris-sdk-ui-kit`).

## Required Inputs
- PR diff / version-bump diff.
- Root `pom.xml` `<distributionManagement>` + metadata state.
- Publishable-module pom state (`attach-sources` / `attach-javadocs` executions).

## Canonical Rules (per repo `CLAUDE.md`)
- Snapshots → `https://central.sonatype.com/repository/maven-snapshots`; releases → `https://central.sonatype.com`.
- The parent `~/exeris-systems/CLAUDE.md` `GITHUB_TOKEN` / `PACKAGES_READ_TOKEN` flow does NOT apply here.
- Publishable: `exeris-sdk-bom` (pom), `exeris-sdk-parent` (pom), `exeris-sdk-annotations` (jar — MUST attach `-sources` + `-javadoc`), `exeris-sdk-source-model` (jar — same).
- `exeris-sdk-ui-kit` is npm-only (`@exeris/ui-kit`); it is NOT in the Maven reactor.
- New publishable module → mirror `attach-sources` + `attach-javadocs` from `exeris-sdk-annotations/pom.xml`.

## Review Procedure
1. **Distribution endpoints** — `<distributionManagement>` points at Sonatype Central Portal, not GitHub Packages.
2. **Required metadata** — `<url>`, `<organization>`, `<licenses>`, `<developers>`, `<scm>`, `<issueManagement>` all populated. Any empty → reject.
3. **SCM alignment** — `<scm>` `<url>` / `<connection>` / `<developerConnection>` match the actual GitHub repo URL.
4. **Sources + javadoc** — every publishable jar module has `attach-sources` + `attach-javadocs` executions. New publishable module without them → reject.
5. **Reactor integrity** — `exeris-sdk-ui-kit` not re-added to `<modules>` (intentionally npm-only).
6. **Version policy** — bump follows stability policy (`0.x` may break; `1.0.0`+ semver). See [[exeris-sdk-deprecation-pipeline-review]] when the bump removes public API.
7. **Downstream pin coordination** — flag consumer repos that pin exact SDK versions when a release ships.
8. **Decision and report** — `APPROVE` / `CONDITIONAL` / `REJECT`.

## Decision Logic
- **APPROVE**: Central endpoints correct; all required metadata populated; sources+javadoc on every jar; ui-kit out of reactor; version policy honoured.
- **CONDITIONAL**: One specific gap (e.g. SCM URL stale after rename) — propose the exact fix.
- **REJECT**: GitHub Packages endpoint; empty required metadata field; publishable jar missing sources/javadoc; ui-kit re-added to reactor.

## Completion Criteria
- Endpoint check, metadata check, SCM check, sources/javadoc check, reactor check, version-policy check done.
- Verdict and remediation recorded.

## Review Output Template
1. **Scope analysed** (poms / version / metadata touched)
2. **Distribution endpoints** (Central / GitHub Packages — reject)
3. **Required metadata** (complete / empty field)
4. **SCM alignment** (matches repo / stale)
5. **Sources + javadoc** (present on all jars / missing)
6. **Reactor integrity** (ui-kit excluded / re-added)
7. **Version policy** (compliant / violation)
8. **Downstream pin coordination** (none / consumers to update)
9. **Verdict** (`APPROVE` / `CONDITIONAL` / `REJECT`)
10. **Required actions** (precise and minimal)

## Non-Negotiable Rules
- Never approve a GitHub Packages endpoint for SDK distribution.
- Never approve a version bump with an empty required POM metadata field.
- Never approve a publishable jar module without `-sources` + `-javadoc` attachments.
- Never approve re-adding `exeris-sdk-ui-kit` to the Maven reactor.
