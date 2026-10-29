/*
 * Copyright (C) 2026 Exeris Systems.
 * SPDX-License-Identifier: Apache-2.0
 */
package eu.exeris.sdk.brief;

import com.fasterxml.jackson.annotation.JsonInclude;
import eu.exeris.sdk.brief.SchemaRules.ConcernHint;
import eu.exeris.sdk.brief.SchemaRules.Kinds;
import eu.exeris.sdk.brief.SchemaRules.NonBlank;
import eu.exeris.sdk.brief.SchemaRules.Required;
import eu.exeris.sdk.sourcemodel.ast.DataScope;

import java.util.List;

/**
 * A candidate domain term: a name, what it means, and the data-scope intent it carries.
 *
 * <p>A term is not an entity draft. It carries no fields, types or relationships; once an entity
 * realises the term, {@link #fulfilledBy()} points at it and the source is the truth from then on.
 *
 * @param term the term as the area's people say it
 * @param meaning what the term means, in one line
 * @param dataScope who may see the data the term stands for, in the SDK's own tiers
 * @param concernHints annotations of the SDK catalog the term is expected to need, by simple name
 *        (such as {@code Saga}, {@code EventSourced} or {@code UI}); a hint names the annotation and
 *        never its attributes
 * @param fulfilledBy the entities that realise the term, once they exist; only the {@code entity}
 *        kind is admitted here
 * @since 0.13
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GlossaryTerm(
        @Required @NonBlank String term,
        @Required @NonBlank String meaning,
        DataScope dataScope,
        @ConcernHint List<String> concernHints,
        @Kinds("entity") List<FulfilledBy> fulfilledBy
) {

    /**
     * Compact constructor; normalizes an absent list to an empty one.
     *
     * @throws NullPointerException if a list holds a {@code null} element
     */
    public GlossaryTerm {
        concernHints = BriefLists.copy(concernHints, "concernHints");
        fulfilledBy = BriefLists.copy(fulfilledBy, "fulfilledBy");
    }
}
