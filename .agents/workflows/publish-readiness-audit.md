---
description: Audit repository release readiness for Maven Central deployment and Sonatype Portal rules.
argument-hint: Release commit, POM diff, or tag
---

Audit release readiness before deploying `exeris-sdk` to Maven Central.

Priorities:
1. Javadoc completeness: check that `failOnWarnings` on the 6 modules and `JavadocCompletenessTest` pass cleanly.
2. Artifact attachments: verify `-sources` and `-javadoc` are attached for all published jars.
3. Build-tool exclusion: verify `exeris-sdk-annotation-catalog` has `maven.deploy.skip=true` and `<excludeArtifacts>`.
4. Compiler baseline: ensure `maven.compiler.release=25` across all modules.
5. POM metadata: verify `<scm>`, `<licenses>`, `<developers>`, and `<url>` tags are populated.

Release scope:
$ARGUMENTS

Please produce:
- Javadoc gate status
- POM deployment metadata check
- Module exclusion check
- Final readiness verdict: READY / NOT READY
