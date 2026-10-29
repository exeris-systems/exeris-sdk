/*
 * Copyright (C) 2026 Exeris Systems.
 * SPDX-License-Identifier: Apache-2.0
 */
package eu.exeris.sdk.examples.presentation;

import eu.exeris.sdk.annotation.ExerisDomain;
import eu.exeris.sdk.annotation.Field;
import eu.exeris.sdk.annotation.NavMenu;
import eu.exeris.sdk.annotation.Tab;
import eu.exeris.sdk.annotation.UI;
import eu.exeris.sdk.annotation.UIGroup;
import eu.exeris.sdk.annotation.system.PrimaryKey;

import java.time.LocalDate;
import java.util.UUID;

/**
 * An employee record whose generated screens are grouped into sections and tabs and listed in the
 * navigation menu.
 *
 * <p>{@code @UI} (entity level), {@code @UIGroup}, {@code @Tab} and {@code @NavMenu} are PARTIAL:
 * the source model reader carries them, and the build-time processor does not extract them yet.
 * {@code @PrimaryKey} is PARTIAL too: the reader carries the marker and the processor does not read
 * it yet. The page that lists employees is declared separately, in {@link EmployeeDirectory}.
 */
@ExerisDomain(module = "people", path = "/employees", description = "A person employed by the company")
@UI(filterable = false, exportable = true)
@NavMenu(group = "People", label = "Employees", icon = "badge", order = 10, groupOrder = 20)
public class Employee {

    @PrimaryKey
    private UUID id;

    @Field(label = "First name", required = true)
    @UIGroup(name = "identity", label = "Identity", order = 1, icon = "person")
    @Tab(name = "profile", label = "Profile", order = 1)
    private String firstName;

    @Field(label = "Last name", required = true)
    @UIGroup(name = "identity")
    @Tab(name = "profile")
    private String lastName;

    @Field(label = "Email")
    @UIGroup(name = "contact", label = "Contact", order = 2, collapsed = true)
    @Tab(name = "profile")
    private String email;

    @Field(label = "Hired on")
    @UIGroup(name = "employment", label = "Employment", order = 3)
    @Tab(name = "employment", label = "Employment", order = 2, lazy = true)
    private LocalDate hiredOn;

    @Field(label = "Position")
    @UIGroup(name = "employment")
    @Tab(name = "employment")
    private String position;
}
