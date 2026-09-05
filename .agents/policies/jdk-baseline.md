# Policy: JDK Baseline & Compiler Target

`exeris-sdk` aligns its Java compiler baseline with the Exeris Kernel GA LTS track: **JDK 25 LTS** (ADR-069, following kernel ADR-066).

## Hard Rules

1. **`maven.compiler.release=25` across the entire reactor:**
   The property is configured in the root POM and inherited by all modules.
2. **Never raise `--release` above the kernel's GA baseline (25):**
   SDK artifacts reside on downstream consumers' **compile classpath**. Compiling with release 26+ produces class files stamped with major version 70+, which javac on JDK 25 will reject outright (`class file has wrong version 70.0, should be 69.0`).
3. **Never lower `--release` below 25:**
   Lowering the release target is not an acceptable fix for build failures. SDK language features (records, sealed hierarchies) require modern language levels, and lowering below the kernel floor creates cross-repo divergence.
4. **Guarded by class-file bytecode inspection:**
   `ClassFileBaselineTest` verifies the emitted class-file major version (≤ 69) across compiled classes. This ensures plugin defaults or local overrides cannot accidentally raise bytecode versions.

## Non-Negotiable Rules

- Never approve lowering `maven.compiler.release` below 25.
- Never approve raising `maven.compiler.release` above 25 without an organization-wide ADR amending ADR-069.
