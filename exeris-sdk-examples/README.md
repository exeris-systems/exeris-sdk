# exeris-sdk-examples

Annotated example sources of the SDK surface, compiled and read back. Each scenario is a package
`eu.exeris.sdk.examples.<scenario>` under `src/main/java`, with a test class
`<Scenario>ExampleTest` that reads the same `.java` files through `SourceModelReader` and asserts the
metadata they declare. javac compiles the sources against `exeris-sdk-annotations`, so a removed or
renamed annotation attribute fails the build here.

The guides in [`docs/guides`](../docs/guides/README.md) and the index in
[`docs/examples`](../docs/examples/README.md) quote these sources by path and symbol.

The module page [`docs/modules/10-examples.md`](../docs/modules/10-examples.md) owns the Contract,
Hot path, Failure modes and Owning ADRs for this module.

Scenarios: `invoicing`, `ordering`, `graph`, `presentation`, `studioedit`, `tckbinding` and
`capability`. The `capability` scenario also drives its lifecycle owner through
`CompositionConductor` from `src/test/resources/capability/cap-manifest.json`, so
`exeris-sdk-composition-lifecycle` is a compile dependency of this module alone.

This module is not published: it carries `maven.deploy.skip`, is excluded from the central-publishing
bundle, and is not in `exeris-sdk-bom`. It is teaching material, not API reference: an example shows
one idiomatic use, and the contract of each surface is in its module page and javadoc. The cost is
that every example is one more source to keep in step with the surface it shows.

```bash
mvn -pl exeris-sdk-examples -am verify
```

The tests resolve source files relative to the module base directory (surefire's working directory),
for example `Path.of("src/main/java/eu/exeris/sdk/examples/invoicing/Invoice.java")`.
