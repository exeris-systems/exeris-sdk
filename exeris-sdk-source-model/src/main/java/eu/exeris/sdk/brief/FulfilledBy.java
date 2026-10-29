/*
 * Copyright (C) 2026 Exeris Systems.
 * SPDX-License-Identifier: Apache-2.0
 */
package eu.exeris.sdk.brief;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import eu.exeris.sdk.brief.SchemaRules.Required;
import eu.exeris.sdk.brief.SchemaRules.When;

/**
 * A typed reference from the brief to the source element that realises part of it.
 *
 * <p>For {@code entity}, {@link #ref()} is the entity's qualified class name; for {@code action},
 * {@code rule} and {@code saga} it is {@code <entity qualified name>#<member name>}. The kind is
 * part of the reference because an action, a rule and a saga of one entity may share a name. The
 * source wins any disagreement: the reference records how a requirement was met, not what the
 * source must say.
 *
 * @param kind which kind of source element the reference names
 * @param ref the source element's name, in the form the kind requires
 * @since 0.13
 */
@When(property = "kind", values = "entity",
        patternProperty = "ref", pattern = FulfilledBy.ENTITY_REF_PATTERN)
@When(property = "kind", values = {"action", "rule", "saga"},
        patternProperty = "ref", pattern = FulfilledBy.MEMBER_REF_PATTERN)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FulfilledBy(
        @Required Kind kind,
        @Required String ref
) {

    /** An entity reference: a qualified Java class name. */
    public static final String ENTITY_REF_PATTERN =
            "^[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*$";

    /** A member reference: an entity's qualified class name, {@code #}, and the member's name. */
    public static final String MEMBER_REF_PATTERN =
            "^[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*#[A-Za-z_$][A-Za-z0-9_$]*$";

    /** The kind of source element a reference names, written in lower case on the wire. */
    public enum Kind {
        /** An {@code @ExerisDomain} entity. */
        @JsonProperty("entity") ENTITY,
        /** An {@code @Action} of an entity. */
        @JsonProperty("action") ACTION,
        /** A {@code @Rule} of an entity. */
        @JsonProperty("rule") RULE,
        /** The {@code @Saga} of an entity. */
        @JsonProperty("saga") SAGA
    }
}
