---
name: publish-readiness-audit
description: Audit repository release readiness for Maven Central deployment and Sonatype Portal rules.
argument-hint: Release commit, POM diff, or tag
steps:
  - {agent: exeris-sdk-architect, skill: exeris-sdk-publish-readiness-review}
  - {agent: exeris-sdk-implementer, when: "a POM or module attribute has to change before the release"}
---

Audit release readiness before deploying `exeris-sdk` to Maven Central.

Priorities:
1. Javadoc completeness: check that `failOnWarnings` on the 6 modules and `JavadocCompletenessTest` pass cleanly.
2. Artifact attachments: verify `-sources` and `-javadoc` are attached for all published jars.
3. Unpublished-module exclusion: verify `exeris-sdk-annotation-catalog` and `exeris-sdk-examples` each have `maven.deploy.skip=true` and an `<excludeArtifacts>` entry.
4. Compiler baseline: ensure `maven.compiler.release=25` across all modules.
5. POM metadata: verify `<scm>`, `<licenses>`, `<developers>`, and `<url>` tags are populated.

Release scope:
$ARGUMENTS

Please produce:
- Javadoc gate status
- POM deployment metadata check
- Module exclusion check
- Final readiness verdict: READY / NOT READY
