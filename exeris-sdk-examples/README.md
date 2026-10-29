# exeris-sdk-examples

Annotated example sources that `docs/guides` and `docs/examples` quote. Each scenario is a package
`eu.exeris.sdk.examples.<scenario>` under `src/main/java`, with a test class
`<Scenario>ExampleTest` that reads the same `.java` files through `SourceModelReader` and asserts the
metadata they declare. javac compiles the sources against `exeris-sdk-annotations`, so a removed or
renamed annotation attribute fails the build here before it fails a documentation page.

Scenarios: `invoicing`, `ordering`, `graph`, `presentation`, `studioedit`, `tckbinding` and
`capability`. The `capability` scenario also drives its lifecycle owner through
`CompositionConductor` from `src/test/resources/capability/cap-manifest.json`, so
`exeris-sdk-composition-lifecycle` is a compile dependency of this module alone.

This module is not published: it carries `maven.deploy.skip`, is excluded from the central-publishing
bundle, and is not in `exeris-sdk-bom`.

```bash
mvn -pl exeris-sdk-examples -am verify
```

The tests resolve source files relative to the module base directory (surefire's working directory),
for example `Path.of("src/main/java/eu/exeris/sdk/examples/invoicing/Invoice.java")`.
