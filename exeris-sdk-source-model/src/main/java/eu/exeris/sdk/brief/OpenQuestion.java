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
 * A question discovery has not settled, and whether authoring may proceed without it.
 *
 * <p>A blocking question in status {@code open} keeps the brief from being ready. Answering it
 * ({@code resolved}) or deciding it on credit ({@code assumed}) releases the gate; an
 * {@code assumed} question must record the {@link #assumption()} made and the {@link #owner()}
 * who answers for it, so a decision taken on credit stays visible as one.
 *
 * @param id the question's identifier, unique within the brief
 * @param question the question itself
 * @param blocking whether authoring must wait for an answer; always written, so a reader never
 *        has to guess what an absent value means
 * @param status where the question stands
 * @param assumption the assumption authoring proceeds on; required when the status is
 *        {@code assumed}
 * @param owner who answers for the assumption; required when the status is {@code assumed}
 * @param answer the answer, once there is one
 * @since 0.13
 */
@When(property = "status", values = "assumed", require = {"assumption", "owner"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenQuestion(
        @Required @NonBlank String id,
        @Required @NonBlank String question,
        @Required Boolean blocking,
        @Required Status status,
        @NonBlank String assumption,
        @NonBlank String owner,
        @NonBlank String answer
) {

    /** Where an open question stands, written in lower case on the wire. */
    public enum Status {
        /** Not answered; a blocking question in this status keeps the brief from being ready. */
        @JsonProperty("open") OPEN,
        /** Decided on credit, with a recorded assumption and owner. */
        @JsonProperty("assumed") ASSUMED,
        /** Answered. */
        @JsonProperty("resolved") RESOLVED
    }
}
