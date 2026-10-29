/*
 * Copyright (C) 2026 Exeris Systems.
 * SPDX-License-Identifier: Apache-2.0
 */
/**
 * The pre-construction brief contract (ADR-106): what one bounded area of an application is for,
 * written before or beside its {@code @ExerisDomain} source.
 *
 * <p>{@link eu.exeris.sdk.brief.Brief} is the root. A brief lives in an application project as
 * {@code docs/briefs/<area>.brief.yaml}, is read as YAML 1.2, and is validated as the JSON
 * document it denotes against {@code META-INF/exeris/brief-schema.json}, which
 * {@code BriefSchemaProcessor} emits from these records into this module's jar.
 * {@link eu.exeris.sdk.brief.BriefReadiness} is the readiness gate.
 *
 * <h2>It references the source model and never re-encodes it</h2>
 * <p>ADR-003 makes the annotated class the only definition of the domain. A brief therefore has
 * no place for anything {@code DomainMetadata} carries: no field list, type, relationship,
 * validation bound, action signature, saga step or view layout. It names the SDK's vocabulary
 * instead: a glossary term's data-scope intent is a
 * {@link eu.exeris.sdk.sourcemodel.ast.DataScope}, its concern hints are annotation names from the
 * SDK catalog, and a {@link eu.exeris.sdk.brief.FulfilledBy} points at an entity, action, rule or
 * saga by name once one exists. Every object in the schema is {@code additionalProperties: false},
 * so a re-encoded structure fails validation rather than review.
 *
 * <h2>Dependency direction</h2>
 * <p>This package may use enums from {@code eu.exeris.sdk.sourcemodel.ast}; nothing under
 * {@code eu.exeris.sdk.sourcemodel} may use this package. A test holds the direction.
 *
 * <h2>Wire format</h2>
 * <p>The records follow the AST's wire discipline: records only, class-level
 * {@code @JsonInclude(NON_NULL)}, components grown only at the tail. Lists are never {@code null}
 * on a constructed record. Enum values are written in lower case, except
 * {@link eu.exeris.sdk.sourcemodel.ast.DataScope}, which is reused by type and keeps the
 * AST's names. A Jackson reader binding a brief must not ignore unknown properties, or a
 * re-encoded structure is dropped silently instead of rejected; validating against the schema
 * first is the portable way to guarantee that.
 *
 * <h2>Freeze discipline</h2>
 * <p>Before SDK 1.0 a change to the brief is recorded in the changelog and ships with its fixtures
 * updated in the same change. From 1.0 it is additive only: removing or renaming a property is a
 * breaking change under the same rule as an annotation attribute. A consumer pins to the
 * schema's {@code briefSchemaFormat}, which moves only when a brief valid under the previous
 * number would stop validating.
 *
 * @since 0.13
 */
package eu.exeris.sdk.brief;
