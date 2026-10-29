/*
 * Copyright (C) 2026 Exeris Systems.
 * SPDX-License-Identifier: Apache-2.0
 */
package eu.exeris.sdk.examples.graph;

import eu.exeris.sdk.annotation.ExerisDomain;
import eu.exeris.sdk.annotation.Field;
import eu.exeris.sdk.annotation.system.PrimaryKey;

import java.util.UUID;

/**
 * A product grouping; the target of the {@code BELONGS_TO} edge on {@link Product}.
 * {@code @PrimaryKey} is PARTIAL: the reader carries the marker and the build-time processor does
 * not read it yet.
 */
@ExerisDomain(module = "catalog", path = "/categories", description = "A product grouping")
public class Category {

    @PrimaryKey
    private UUID id;

    @Field(label = "Name", required = true)
    private String name;
}
