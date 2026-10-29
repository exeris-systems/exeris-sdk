/*
 * Copyright (C) 2026 Exeris Systems.
 * SPDX-License-Identifier: Apache-2.0
 */
package eu.exeris.sdk.brief;

import com.fasterxml.jackson.annotation.JsonInclude;
import eu.exeris.sdk.brief.SchemaRules.MinItems;
import eu.exeris.sdk.brief.SchemaRules.NonBlank;
import eu.exeris.sdk.brief.SchemaRules.Pattern;
import eu.exeris.sdk.brief.SchemaRules.Required;

import java.util.List;

/**
 * A pre-construction brief: what one bounded area of an application is for, before or beside its
 * {@code @ExerisDomain} source (ADR-106).
 *
 * <p>The brief carries what the source model cannot: purpose, goals and non-goals, actors, a
 * glossary of candidate terms, workflows as structured EARS criteria, non-functional
 * requirements, integrations, candidate capabilities, and the questions still open. It references
 * the source model and never re-encodes it: no field list, type, relationship, validation bound,
 * action signature, saga step or view layout has a place in it, and the emitted schema rejects
 * each of them.
 *
 * <p>A brief lives in an application project as {@code docs/briefs/<area>.brief.yaml}, one per
 * bounded area, read as YAML 1.2 and validated as the JSON document it denotes against
 * {@code META-INF/exeris/brief-schema.json}. {@link BriefReadiness} decides whether it is ready
 * for authoring.
 *
 * @param briefSchema the SDK version whose emitted brief schema this brief was written against,
 *        such as {@code 0.13.0}; written as the document's first key
 * @param purpose what the area is for, in a sentence or a paragraph
 * @param goals the outcomes the area is meant to achieve
 * @param nonGoals what the area deliberately does not do; at least one, because a brief that
 *        names no boundary has not been asked for one
 * @param actors the people and systems that use the area; at least one
 * @param glossary the candidate domain terms, each with its meaning and data-scope intent
 * @param workflows what the area must do, each as EARS criteria; at least one
 * @param nonFunctional the load, availability, retention, residency and compliance requirements
 * @param integrations the external systems the area exchanges data with
 * @param candidateCapabilities capabilities the area may compose, by name
 * @param openQuestions the questions still being answered, each marked blocking or not
 * @since 0.13
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Brief(
        @Required @Pattern(Brief.SDK_VERSION_PATTERN) String briefSchema,
        @Required @NonBlank String purpose,
        @NonBlank List<String> goals,
        @MinItems(1) @NonBlank List<String> nonGoals,
        @MinItems(1) List<Actor> actors,
        List<GlossaryTerm> glossary,
        @MinItems(1) List<Workflow> workflows,
        List<NonFunctionalRequirement> nonFunctional,
        List<Integration> integrations,
        @NonBlank List<String> candidateCapabilities,
        List<OpenQuestion> openQuestions
) {

    /**
     * The shape of {@link #briefSchema()}: a release version, optionally with a pre-release
     * suffix.
     */
    public static final String SDK_VERSION_PATTERN = "^[0-9]+\\.[0-9]+\\.[0-9]+(-[0-9A-Za-z.-]+)?$";

    /**
     * Compact constructor; normalizes an absent list to an empty one.
     *
     * @throws NullPointerException if a list holds a {@code null} element
     */
    public Brief {
        goals = BriefLists.copy(goals, "goals");
        nonGoals = BriefLists.copy(nonGoals, "nonGoals");
        actors = BriefLists.copy(actors, "actors");
        glossary = BriefLists.copy(glossary, "glossary");
        workflows = BriefLists.copy(workflows, "workflows");
        nonFunctional = BriefLists.copy(nonFunctional, "nonFunctional");
        integrations = BriefLists.copy(integrations, "integrations");
        candidateCapabilities = BriefLists.copy(candidateCapabilities, "candidateCapabilities");
        openQuestions = BriefLists.copy(openQuestions, "openQuestions");
    }
}
