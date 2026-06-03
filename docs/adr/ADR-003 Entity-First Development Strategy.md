# ADR-003: Entity-First Development Strategy

| Atrybut        | Wartość                                                                                                                                                                  |
|:---------------|:-------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Status**     | **ACCEPTED**                                                                                                                                                             |
| **Deciders**   | Arkadiusz Przychocki                                                                                                                                                     |
| **Date**       | 2025-11-12                                                                                                                                                               |
| **Owning Repo**| `exeris-sdk`                                                                                                                                                             |
| **Driven By**  | [2025-11-12 - RFC - Entity-First Development Strategy](https://exeris.atlassian.net/wiki/spaces/ENG/pages/15663105/2025-11-12+-+RFC+-+Entity-First+Development+Strategy) |
| **Compliance** | [Strategic Pillar: Transparency](https://exeris.atlassian.net/wiki/spaces/HUB/pages/6094854/Strategic+Pillars+Architecture+Principles#Strategic-Pillars)                 |

## Context and Problem Statement
Traditional high-performance systems often adopt a "Schema-First" approach (defining `.proto` or OpenAPI specs first), leading to boilerplate code, manual synchronization between layers, and a "Black Box" feeling for business developers.

This contradicts our **"Glass Box"** value proposition. We need a development model that keeps the business domain as the single source of truth while generating the necessary high-performance infrastructure code automatically.

## 🏁 The Decision
We adopt the **Entity-First Development** paradigm.

**Core Principles:**
* **Source of Truth:** The Java Class annotated with `@ExerisDomain` is the only definition.
* **No Proto Files:** We do **not** write `.proto` or OpenAPI files manually. These are generated artifacts, not source code.
* **Annotation Driven:** Behavior, validation, and storage are defined via annotations (`@Action`, `@Field`, `@Relationship`) on the entity itself.
* **Code Generation:** The SDK generates SQL migrations, REST/GraphQL endpoints, and DTOs during the build process.

## Positive Outcomes
* **Velocity:** Drastic reduction in boilerplate. Developers focus purely on business logic.
* **Consistency:** The API, Database, and UI are always in sync because they are derived from the same class.
* **Glass Box:** The generated code is human-readable and checkable, unlike opaque binary blobs.

## Trade-offs / Risks
* **Tight Coupling:** The domain model is tightly coupled to the Exeris SDK annotations.
* **Magic:** Heavy reliance on annotation processors can make it harder to understand *how* the code works under the hood for junior developers (mitigation: "Instant Context Switch" to view generated code).

## Engineering Protocol
Once this decision is ACCEPTED, it must be committed to the repository to maintain the Single Source of Truth.
