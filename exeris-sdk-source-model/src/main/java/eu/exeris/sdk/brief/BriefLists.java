/*
 * Copyright (C) 2026 Exeris Systems.
 * SPDX-License-Identifier: Apache-2.0
 */
package eu.exeris.sdk.brief;

import java.util.List;

/**
 * Normalization for the list components of the brief records.
 *
 * <p>Package-private: not part of the published surface.
 */
final class BriefLists {

    private BriefLists() {
    }

    /**
     * Returns an immutable copy of a list component, {@link List#of()} for {@code null}.
     *
     * <p>An absent list and an empty one are the same value on a constructed record, so a
     * {@code required} list and a {@code minItems} list fail alike, in the schema and in
     * {@link BriefReadiness}. A {@code null} element is refused by name: the schema rejects it
     * too, and dropping it would shorten a list its author believed complete.
     *
     * @param <T>       the element type
     * @param values    the component's value, possibly {@code null}
     * @param component the component's name, used in the failure message
     * @return an immutable copy, or {@link List#of()} when {@code values} is {@code null}
     * @throws NullPointerException if any element of {@code values} is {@code null}
     */
    static <T> List<T> copy(List<T> values, String component) {
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
