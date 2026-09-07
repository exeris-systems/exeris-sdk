/*
 * Copyright (C) 2025-2026 Exeris Systems.
 * SPDX-License-Identifier: Apache-2.0
 */
package eu.exeris.sdk.sourcemodel.ast;

import java.util.List;

/**
 * Normalization for the list components of the AST records.
 *
 * <p>Package-private on purpose. The records in this package are the SDK's wire-format contract and
 * are frozen at 1.0.0; this class is not part of that surface and may change at any time.
 *
 * @since 0.12
 */
final class AstLists {

    private AstLists() {
    }

    /**
     * Returns an immutable copy of a list component, rejecting a {@code null} element by name.
     *
     * <p>An absent list and an empty one are the same thing here: both yield {@link List#of()}, which
     * is what keeps a component from ever being {@code null} on a constructed record.
     *
     * <p>A {@code null} <em>element</em> is different, and is refused. It cannot be preserved —
     * {@link List#copyOf} rejects it and the JSON form has no place to put it back — and dropping it
     * would silently shorten a list a caller believed it had supplied. What this method adds over
     * {@code List.copyOf} alone is the diagnosis: a document arriving over the wire as
     * {@code {"aggregateTypes":["Order",null]}} otherwise fails with a bare
     * {@code NullPointerException} raised inside the deserializer, naming neither the component nor
     * the position, on a contract whose every other rejection names what was wrong.
     *
     * @param <T>       the element type
     * @param values    the component's value, possibly {@code null}
     * @param component the component's name, used in the failure message
     * @return an immutable copy, or {@link List#of()} when {@code values} is {@code null}
     * @throws NullPointerException if any element of {@code values} is {@code null}
     * @since 0.12
     */
    static <T> List<T> copyOfNoNulls(List<T> values, String component) {
        if (values == null) {
            return List.of();
        }
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i) == null) {
                throw new NullPointerException(
                        component + "[" + i + "] is null; " + component + " must not contain null elements");
            }
        }
        return List.copyOf(values);
    }
}
