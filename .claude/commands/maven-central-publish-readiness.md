---
description: Audit a change for Maven Central publish-readiness (Sonatype Central Portal endpoints, required POM metadata, attached -sources + -javadoc jars, ui-kit out of reactor).
argument-hint: PR diff or version bump / pom change touching publish pipeline
---

Invoke the `exeris-sdk-publish-readiness-review` skill and apply its full review procedure (distribution endpoints, required POM metadata, SCM alignment, sources+javadoc attachments, reactor integrity, version policy, downstream pin coordination) to the change below. Produce the skill's review-output template and verdict.

Change:
$ARGUMENTS
