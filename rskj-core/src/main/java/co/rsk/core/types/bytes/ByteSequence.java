/*
 * This file is part of RskJ
 * Copyright (C) 2024 RSK Labs Ltd.
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

package co.rsk.core.types.bytes;

/**
 * {@link ByteSequence} is the most basic interface that represents read-only sequence of <code>byte</code> values.
 */
public interface ByteSequence {

    /**
     * Returns the length of this byte sequence.
     *
     * @return  the length of the sequence of bytes represented by this
     *          object.
     */
    int length();

    /**
     * Returns the {@code byte} value at the
     * specified index. An index ranges from {@code 0} to
     * {@code length() - 1}. The first {@code byte} value of the sequence
     * is at index {@code 0}, the next at index {@code 1},
     * and so on, as for array indexing.
     *
     * @param      index   the index of the {@code byte} value.
     * @return     the {@code byte} value at the specified index of this array.
     *             The first {@code byte} value is at index {@code 0}.
     * @exception  IndexOutOfBoundsException  if the {@code index}
     *             argument is negative or not less than the length of this
     *             array.
     */
    byte byteAt(int index);
}
