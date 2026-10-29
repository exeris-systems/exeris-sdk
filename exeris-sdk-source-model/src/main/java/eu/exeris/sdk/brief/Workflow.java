/*
 * Copyright (C) 2026 Exeris Systems.
 * SPDX-License-Identifier: Apache-2.0
 */
package eu.exeris.sdk.brief;

import com.fasterxml.jackson.annotation.JsonInclude;
import eu.exeris.sdk.brief.SchemaRules.Kinds;
import eu.exeris.sdk.brief.SchemaRules.MinItems;
import eu.exeris.sdk.brief.SchemaRules.NonBlank;
import eu.exeris.sdk.brief.SchemaRules.Required;

import java.util.List;

/**
 * Something the area must do, stated as EARS criteria.
 *
 * <p>The criteria stay the requirement they were after the source exists; {@link #fulfilledBy()}
 * records how it was met.
 *
 * @param name the workflow's name
 * @param actors the actors that take part, by {@link Actor#name()}
 * @param criteria the acceptance criteria; at least one
 * @param fulfilledBy the actions, rules and sagas that meet the workflow, once they exist; the
 *        {@code entity} kind is not admitted here
 * @since 0.13
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Workflow(
        @Required @NonBlank String name,
        @NonBlank List<String> actors,
        @MinItems(1) List<EarsCriterion> criteria,
        @Kinds({"action", "rule", "saga"}) List<FulfilledBy> fulfilledBy
) {

    /**
     * Compact constructor; normalizes an absent list to an empty one.
     *
     * @throws NullPointerException if a list holds a {@code null} element
     */
    public Workflow {
        actors = BriefLists.copy(actors, "actors");
        criteria = BriefLists.copy(criteria, "criteria");
        fulfilledBy = BriefLists.copy(fulfilledBy, "fulfilledBy");
    }
}
