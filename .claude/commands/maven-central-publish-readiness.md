---
description: Verify Maven Central publish-readiness — Sonatype Central Portal `<distributionManagement>`, required POM metadata, attached `-sources` + `-javadoc` jars on publishable modules.
argument-hint: PR diff or version bump / pom change touching publish pipeline
---

Audit this change for Maven Central publish-readiness.

Distribution rules (per repo `CLAUDE.md`):
- SDK publishes to **Sonatype Central Portal**, NOT GitHub Packages.
  - Snapshots → `https://central.sonatype.com/repository/maven-snapshots`
  - Releases → `https://central.sonatype.com`
- The parent `~/exeris-systems/CLAUDE.md` `GITHUB_TOKEN` / `PACKAGES_READ_TOKEN` flow does NOT apply here.
- Downstream Exeris repos resolve SDK artifacts from Maven Central.

Required POM metadata (Maven Central validates these):
- `<url>`
- `<organization>`
- `<licenses>`
- `<developers>`
- `<scm>`
- `<issueManagement>`
- `<distributionManagement>` (Sonatype Central Portal endpoints)

Publishable Maven modules:
- `exeris-sdk-bom` (pom)
- `exeris-sdk-parent` (pom)
- `exeris-sdk-annotations` (jar — MUST attach `-sources` + `-javadoc`)
- `exeris-sdk-source-model` (jar — MUST attach `-sources` + `-javadoc`)
- `exeris-sdk-ui-kit` — NOT in Maven reactor; published as `@exeris/ui-kit` to npm.

When adding a new publishable Maven module, mirror the `attach-sources` + `attach-javadocs` executions from `exeris-sdk-annotations/pom.xml`.

Change:
$ARGUMENTS

Please review:
1. Does the change touch `<distributionManagement>`? Verify Sonatype Central Portal endpoints, not GitHub Packages.
2. Does the change touch any required POM metadata field (`<url>`, `<organization>`, `<licenses>`, `<developers>`, `<scm>`, `<issueManagement>`)? All MUST stay populated.
3. If a new publishable module is added: are `attach-sources` + `attach-javadocs` executions present? Without them Maven Central rejects.
4. Is `exeris-sdk-ui-kit` accidentally re-added to the Maven reactor? It's intentionally excluded — npm-only.
5. Does the version bump follow stability policy (0.x can break; 1.0.0+ semver)?
6. Are SCM `<url>`, `<connection>`, `<developerConnection>` aligned with the actual GitHub repo URL?
7. Does the change require coordinated downstream version pin updates (consumer repos pin exact SDK versions)?
8. Minimal correction if publish-readiness is at risk.

Don't ship a version bump while a required POM field is empty — the Maven Central publish step will fail validation.
