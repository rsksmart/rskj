/*
 * This file is part of RskJ
 * Copyright (C) 2025 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.util;

public class Trio<R, M, L> {
    private final R first;
    private final M middle;
    private final L last;

    public static <R, M, L> Trio<R, M, L> of(R first, M middle, L last) {
        return new Trio<>(first, middle, last);
    }

    private Trio(R first, M middle, L last) {
        this.first = first;
        this.middle = middle;
        this.last = last;
    }

    public R getFirst() {
        return first;
    }

    public M getMiddle() {
        return middle;
    }

    public L getLast() {
        return last;
    }
}
