/*
 * Copyright (C) 2026 Exeris Systems.
 * SPDX-License-Identifier: Apache-2.0
 */
package eu.exeris.sdk.examples.presentation;

import eu.exeris.sdk.annotation.Bind;
import eu.exeris.sdk.annotation.Block;
import eu.exeris.sdk.annotation.Region;
import eu.exeris.sdk.annotation.View;

/**
 * A page composed of regions, each holding blocks bound to data by name.
 *
 * <p>{@code @View}, {@code @Region}, {@code @Block} and {@code @Bind} are LIVE structurally: the
 * build-time processor walks this class into a {@code ViewMetadata}. The source model reader does
 * not read them, so the tests for this page check the declared structure through the AST instead.
 */
@View(name = "EmployeeDirectory", kind = View.Kind.PAGE, route = "/people", title = "Employees")
public final class EmployeeDirectory {

    @Region(slot = "header")
    HeaderRegion header;

    /** A blank slot is named after the member: {@code body}. */
    @Region
    BodyRegion body;

    static final class HeaderRegion {

        @Block(type = Block.BlockType.HERO)
        @Bind(source = Bind.Source.STATIC)
        String banner;
    }

    static final class BodyRegion {

        @Block(type = Block.BlockType.LIST)
        @Bind(source = Bind.Source.ENTITY, ref = "Employee", path = "lastName")
        String employees;

        @Block(type = Block.BlockType.CARD)
        @Bind(source = Bind.Source.ACTION, ref = "invite")
        String inviteCard;
    }
}
