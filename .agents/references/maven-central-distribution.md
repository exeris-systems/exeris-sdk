# Reference: Maven Central & Release Distribution

`exeris-sdk` publishes to Maven Central via Sonatype Central Portal (configured in root `pom.xml` under `<distributionManagement>`).

## Module Publication Matrix

| Module | Type | Published to Central? | Notes |
|:---|:---|:---:|:---|
| `exeris-sdk-bom` | pom | **Yes** | BOM coordinate for consumers |
| `exeris-sdk-parent` | pom | **Yes** | Build configuration parent |
| `exeris-sdk-annotations` | jar | **Yes** | Attaches `-sources` and `-javadoc` |
| `exeris-sdk-source-model` | jar | **Yes** | Attaches `-sources` and `-javadoc` |
| `exeris-sdk-source-model-io` | jar | **Yes** | JavaParser-based parser/writer (ADR-037) |
| `exeris-sdk-composition-spec` | jar | **Yes** | `cap-manifest.json` schema & bindings (ADR-024) |
| `exeris-sdk-composition-lifecycle` | jar | **Yes** | `CapabilityLifecycleHooks` interface (ADR-024) |
| `exeris-sdk-composition-runtime` | jar | **Yes** | SKU boot conductor and stamp checker |
| `exeris-sdk-tck` | jar | **Yes** | Test harness for downstream consumers |
| `exeris-sdk-annotation-catalog` | jar | **No** | Build-time processor; excluded via `maven.deploy.skip` and `<excludeArtifacts>` |
| `exeris-sdk-ui-kit` | npm | **No** (npm registry) | Standalone npm package; published to GitHub Packages / npm |

## Requirements for Central

1. **Javadocs and Sources:** Every published jar module must configure `attach-sources` and `attach-javadocs` executions. Without them, Sonatype Central Portal rejects deployment.
2. **Exclusion of Build Tools:** Build-time tools (such as `exeris-sdk-annotation-catalog`) must be excluded in both `maven.deploy.skip` and root `central-publishing-maven-plugin` `<excludeArtifacts>`, as central-publishing does not respect `maven.deploy.skip` alone.
3. **Artifact Signatures:** Releases are signed via `maven-gpg-plugin` using the `release` profile in root `pom.xml`.
