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
 * An external system the area exchanges data with.
 *
 * @param name the external system's name
 * @param direction which way data flows
 * @param purpose what the exchange is for
 * @since 0.13
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Integration(
        @Required @NonBlank String name,
        Direction direction,
        @NonBlank String purpose
) {

    /** Which way data flows across an integration, written in lower case on the wire. */
    public enum Direction {
        /** The external system sends data to the area. */
        @JsonProperty("inbound") INBOUND,
        /** The area sends data to the external system. */
        @JsonProperty("outbound") OUTBOUND,
        /** Data flows both ways. */
        @JsonProperty("bidirectional") BIDIRECTIONAL
    }
}
