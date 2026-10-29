/*
 * Copyright (C) 2026 Exeris Systems.
 * SPDX-License-Identifier: Apache-2.0
 */
package eu.exeris.sdk.brief;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import eu.exeris.sdk.brief.SchemaRules.NonBlank;
import eu.exeris.sdk.brief.SchemaRules.Required;
import eu.exeris.sdk.brief.SchemaRules.When;

/**
 * One acceptance criterion in EARS form (Easy Approach to Requirements Syntax), held as fields
 * rather than as one sentence.
 *
 * <p>Which fields a criterion carries is decided by its {@link #kind()}, and that is the whole
 * check: a criterion is valid when it has the fields its kind requires and none its kind forbids.
 * No text is inspected.
 * <ul>
 *   <li>{@code ubiquitous} (THE SYSTEM SHALL &lt;response&gt;): a response only.</li>
 *   <li>{@code when} (WHEN &lt;trigger&gt; THE SYSTEM SHALL &lt;response&gt;): a trigger; a
 *       condition is optional and states the state in which the trigger applies.</li>
 *   <li>{@code if} (IF &lt;trigger&gt; THEN THE SYSTEM SHALL &lt;response&gt;): the unwanted
 *       event as the trigger; a condition is optional, as for {@code when}.</li>
 *   <li>{@code while} (WHILE &lt;condition&gt; THE SYSTEM SHALL &lt;response&gt;): a condition
 *       and no trigger.</li>
 * </ul>
 *
 * @param kind the EARS pattern the criterion follows
 * @param trigger the event that starts the response, for {@code when} and {@code if}
 * @param condition the state in which the response holds; required for {@code while}, optional
 *        for {@code when} and {@code if}, absent for {@code ubiquitous}
 * @param response what the system does
 * @since 0.13
 */
@When(property = "kind", values = "ubiquitous", forbid = {"trigger", "condition"})
@When(property = "kind", values = {"when", "if"}, require = "trigger")
@When(property = "kind", values = "while", require = "condition", forbid = "trigger")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EarsCriterion(
        @Required Kind kind,
        @NonBlank String trigger,
        @NonBlank String condition,
        @Required @NonBlank String response
) {

    /** The EARS pattern of a criterion, written in lower case on the wire. */
    public enum Kind {
        /** A response that always holds. */
        @JsonProperty("ubiquitous") UBIQUITOUS,
        /** A response to a triggering event. */
        @JsonProperty("when") WHEN,
        /** A response to an unwanted event. */
        @JsonProperty("if") IF,
        /** A response that holds while a state lasts. */
        @JsonProperty("while") WHILE
    }
}
