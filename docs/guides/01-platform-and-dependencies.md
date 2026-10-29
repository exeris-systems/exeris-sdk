---
title: Platform and dependencies
type: howto
visibility: public
owning-repo: exeris-sdk
status: active
last-verified: 2026-10-29
---

# Platform and dependencies

**Audience:** you are adding `exeris-sdk` to a Maven build or an npm project and need the JDK baseline, the coordinates and the module that fits your job.

**Goal:** pick the smallest set of SDK artifacts for what you do, and resolve them.

> **Verified against** `0.13.0-SNAPSHOT` at commit `0753b7d0`, 2026-10-29, on `main`. Coordinates were read from the module poms and `package.json`, not from memory. If this page and a pom disagree, **the pom wins and this guide is the defect**.

## Prerequisites

- JDK 25 LTS to build against the jars. The class files carry major version 69.
- Maven for the Java modules, and Node with npm for `@exeris/ui-kit`.
- A decision on which job you are doing: write annotated entities, read them, or style generated UI.

## Steps

### 1. Use JDK 25 LTS

The root `pom.xml` sets `maven.compiler.release` to 25, so every jar ships class files with major version 69. The jars sit on your compile classpath, which is why the major is held at the LTS and not raised.

The ruling is [ADR-069](../adr/ADR-069-jdk-baseline-lts.md), and the policy that guards it is [JDK baseline](../../.agents/policies/jdk-baseline.md). The runtime flags of an application that runs on the kernel are the kernel's concern, in its [platform guide](https://github.com/exeris-systems/exeris-kernel/blob/main/docs/guides/01-platform-and-dependencies.md).

### 2. Resolve from Maven Central

Released versions from the 0.12.0 line on resolve from Maven Central under the `eu.exeris` group. A `-SNAPSHOT`, or a line that has no release yet, is built from source and consumed from your local repository.

Source: `exeris-sdk-annotations/src/main/java/eu/exeris/sdk/annotation/package-info.java`, the "Resolve the artifacts" step of the quick start (quoted).

```bash
git clone https://github.com/exeris-systems/exeris-sdk && cd exeris-sdk && mvn -q install
```

The publication matrix, including the modules that are not published, is in [Maven Central and release distribution](../../.agents/references/maven-central-distribution.md).

### 3. Import the BOM

`eu.exeris:exeris-sdk-bom` manages the version of every published SDK module and of the third-party libraries they share. Import it once and leave versions off the individual dependencies.

Source: `exeris-sdk-bom/pom.xml`, the `exeris-sdk-bom` coordinates (adapted — no file contains this stanza; it is composed from the group, the artifact and the packaging, and the version is a placeholder for the release you resolve).

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>eu.exeris</groupId>
            <artifactId>exeris-sdk-bom</artifactId>
            <version>${exeris.sdk.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### 4. Add the module for your job

| You want to | Add |
|---|---|
| Annotate entities | `eu.exeris:exeris-sdk-annotations` |
| Handle the metadata records | `eu.exeris:exeris-sdk-source-model` |
| Read or edit Java source | `eu.exeris:exeris-sdk-source-model-io` |
| Test a producer, reader or mapper | `eu.exeris:exeris-sdk-tck`, with test scope |
| Work with capabilities | `eu.exeris:exeris-sdk-composition-spec`, `-composition-lifecycle`, `-composition-runtime` |

The annotations are `@Retention(SOURCE)`. They are needed to compile and never at runtime, which is why `provided` is the honest scope.

Source: `exeris-sdk-annotations/src/main/java/eu/exeris/sdk/annotation/package-info.java`, the dependency stanza of the quick start (adapted — the `<version>` line removed because the BOM manages it).

```xml
<dependency>
    <groupId>eu.exeris</groupId>
    <artifactId>exeris-sdk-annotations</artifactId>
    <scope>provided</scope>
</dependency>
```

Which module depends on which, and on what, is in [`docs/modules/`](../modules/README.md). The capability modules are walked through in [guide 08](08-capabilities-and-composition.md).

### 5. Bring a Jackson 3 mapper only if you read the wire format

`exeris-sdk-source-model` ships Jackson annotations and no `databind`. Serializing `DomainMetadata` to JSON, or reading it back, needs a Jackson 3 `ObjectMapper` that you supply. Its required settings are in the [source model page](../modules/02-source-model.md). The `source-model-io` module already brings `jackson-databind`, so a tool that reads source gets one without a separate dependency.

### 6. Install the UI kit from npm

Source: `exeris-sdk-ui-kit/package.json`, the `name` and `peerDependencies` entries (adapted — the install command is composed from the package name and its peer dependency).

```bash
npm install @exeris/ui-kit tailwindcss
```

`@exeris/ui-kit` declares `tailwindcss` at 4.0.0 or newer as a peer dependency, so Tailwind v4 is yours to install. The kit is CSS plus one typed constant and has its own version line. Its contract is the [UI kit page](../modules/09-ui-kit.md), and [guide 09](09-style-generated-ui.md) walks the import order.

## Verify

From a clone of this repository, build the examples module. It resolves the annotations, the source model and the reader from the reactor:

```bash
mvn -pl exeris-sdk-examples -am verify
```

Expect `BUILD SUCCESS` and no test failures. On a JDK older than 25 the build stops at the first compile with `release version 25 not supported`. In your own project, the same cause surfaces as `class file has wrong version 69.0`.

## Where this does not apply

- **Running an application.** The SDK is design-time and build-time. The kernel runs the application, and its dependencies are in the kernel guides.
- **Releases at or below 0.11.0.** They are a git tag and a GitHub Release only, so step 2 does not resolve them.
- **Trade-off you accept.** Pinning the class-file major at 69 keeps LTS deployments working, and it means the SDK cannot use a language feature that needs a newer baseline until the baseline moves.
