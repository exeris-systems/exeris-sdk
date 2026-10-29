/*
 * Copyright (C) 2026 Exeris Systems.
 * SPDX-License-Identifier: Apache-2.0
 */
package eu.exeris.sdk.brief;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import eu.exeris.sdk.brief.SchemaRules.NonBlank;
import eu.exeris.sdk.brief.SchemaRules.Required;

/**
 * A quality the area must have, as opposed to something it must do.
 *
 * @param category what kind of quality the requirement constrains
 * @param requirement the requirement, stated measurably where it can be
 * @since 0.13
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NonFunctionalRequirement(
        @Required Category category,
        @Required @NonBlank String requirement
) {

    /** The kind of quality a requirement constrains, written in lower case on the wire. */
    public enum Category {
        /** Volume and rate: users, requests, records. */
        @JsonProperty("load") LOAD,
        /** Uptime and recovery. */
        @JsonProperty("availability") AVAILABILITY,
        /** How long data is kept, and when it must be gone. */
        @JsonProperty("retention") RETENTION,
        /** Where data may be stored and processed. */
        @JsonProperty("residency") RESIDENCY,
        /** A law, standard or contract the area must satisfy. */
        @JsonProperty("compliance") COMPLIANCE,
        /** A quality none of the other categories names. */
        @JsonProperty("other") OTHER
    }
}
