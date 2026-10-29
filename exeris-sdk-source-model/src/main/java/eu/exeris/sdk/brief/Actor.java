/*
 * Copyright (C) 2026 Exeris Systems.
 * SPDX-License-Identifier: Apache-2.0
 */
package eu.exeris.sdk.brief;

import com.fasterxml.jackson.annotation.JsonInclude;
import eu.exeris.sdk.brief.SchemaRules.NonBlank;
import eu.exeris.sdk.brief.SchemaRules.Required;

import java.util.List;

/**
 * Someone or something that uses the area: a person in a role, or another system.
 *
 * @param name the actor's name, which a {@link Workflow} uses to refer to it
 * @param description who the actor is and what they need from the area
 * @param roles the roles the actor acts in, by name
 * @since 0.13
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Actor(
        @Required @NonBlank String name,
        String description,
        @NonBlank List<String> roles
) {

    /**
     * Compact constructor; normalizes an absent list to an empty one.
     *
     * @throws NullPointerException if {@code roles} holds a {@code null} element
     */
    public Actor {
        roles = BriefLists.copy(roles, "roles");
    }
}
