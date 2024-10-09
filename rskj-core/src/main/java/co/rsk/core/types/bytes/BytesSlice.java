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

import java.util.Arrays;

/**
 * A {@link BytesSlice} is a subsequence of bytes backed by another broader byte sequence.
 */
public interface BytesSlice extends HexPrintableBytes {

    /**
     * Copies an array from the {@link BytesSlice} source, beginning at the
     * specified position, to the specified position of the destination array.
     * A subsequence of array components are copied from this instance to the
     * destination array referenced by {@code dest}. The number of components
     * copied is equal to the {@code length} argument. The components at
     * positions {@code srcPos} through  {@code srcPos+length-1} in the source
     * array are copied into positions {@code destPos} through
     * {@code destPos+length-1}, respectively, of the destination
     * array.
     * <p>
     * If the underlying byte array and {@code dest} argument refer to the
     * same array object, then the copying is performed as if the
     * components at positions {@code srcPos} through
     * {@code srcPos+length-1} were first copied to a temporary
     * array with {@code length} components and then the contents of
     * the temporary array were copied into positions
     * {@code destPos} through {@code destPos+length-1} of the
     * destination array.
     * <p>
     * If {@code dest} is {@code null}, then a
     * {@code NullPointerException} is thrown.
     * <p>
     * Otherwise, if any of the following is true, an
     * {@code IndexOutOfBoundsException} is
     * thrown and the destination is not modified:
     * <ul>
     * <li>The {@code srcPos} argument is negative.
     * <li>The {@code destPos} argument is negative.
     * <li>The {@code length} argument is negative.
     * <li>{@code srcPos+length} is greater than
     *     {@code src.length}, the length of the source array.
     * <li>{@code destPos+length} is greater than
     *     {@code dest.length}, the length of the destination array.
     * </ul>
     *
     * <p>
     * Note: this method mimics behaviour of {@link System#arraycopy(Object, int, Object, int, int)}
     *
     * @param      srcPos   starting position in the source array.
     * @param      dest     the destination array.
     * @param      destPos  starting position in the destination data.
     * @param      length   the number of array elements to be copied.
     * @throws     IndexOutOfBoundsException  if copying would cause
     *             access of data outside array bounds.
     * @throws     NullPointerException if {@code dest} is {@code null}.
     */
    void arraycopy(int srcPos, byte[] dest, int destPos, int length);

    /**
     * Copies the specified range of the specified array into a new array.
     * The initial index of the range (<tt>from</tt>) must lie between zero
     * and <tt>original.length</tt>, inclusive.  The value at
     * <tt>original[from]</tt> is placed into the initial element of the copy
     * (unless <tt>from == original.length</tt> or <tt>from == to</tt>).
     * Values from subsequent elements in the original array are placed into
     * subsequent elements in the copy.  The final index of the range
     * (<tt>to</tt>), which must be greater than or equal to <tt>from</tt>,
     * may be greater than <tt>original.length</tt>, in which case
     * <tt>(byte)0</tt> is placed in all elements of the copy whose index is
     * greater than or equal to <tt>original.length - from</tt>.  The length
     * of the returned array will be <tt>to - from</tt>.
     *
     * <p>
     * Note: this method mimics behaviour of {@link Arrays#copyOfRange(Object[], int, int)}
     *
     * @param from the initial index of the range to be copied, inclusive
     * @param to the final index of the range to be copied, exclusive.
     *     (This index may lie outside the array.)
     * @return a new array containing the specified range from the original array,
     *     truncated or padded with zeros to obtain the required length
     * @throws IndexOutOfBoundsException if {@code from < 0}
     *     or {@code from > original.length}
     * @throws IllegalArgumentException if <tt>from &gt; to</tt>
     */
    default byte[] copyArrayOfRange(int from, int to) {
        if (from < 0 || from > length()) {
            throw new IndexOutOfBoundsException("invalid 'from': " + from);
        }
        if (to < from) {
            throw new IllegalArgumentException(from + " > " + to);
        }
        int newLength = to - from;
        byte[] copy = new byte[newLength];
        arraycopy(from, copy, 0, Math.min(length() - from, newLength));
        return copy;
    }

    default byte[] copyArray() {
        return copyArrayOfRange(0, length());
    }

    default Bytes copyBytesOfRange(int from, int to) {
        return Bytes.of(copyArrayOfRange(from, to));
    }

    default Bytes copyBytes() {
        return Bytes.of(copyArrayOfRange(0, length()));
    }

    default BytesSlice slice(int from, int to) {
        return new BytesSliceImpl(this, from, to);
    }

    static boolean equals(BytesSlice a, BytesSlice b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }

        int aLen = a.length();
        if (b.length() != aLen) {
            return false;
        }

        for (int i = 0; i < aLen; i++) {
            if (a.byteAt(i) != b.byteAt(i)) {
                return false;
            }
        }

        return true;
    }

    static int hashCode(BytesSlice bytesSlice) {
        if (bytesSlice == null) {
            return 0;
        }

        int result = 1;
        int len = bytesSlice.length();
        for (int i = 0; i < len; i++) {
            result = 31 * result + bytesSlice.byteAt(i);
        }

        return result;
    }
}

class BytesSliceImpl implements BytesSlice {

    private final BytesSlice originBytes;

    private final int from;
    private final int to;

    BytesSliceImpl(BytesSlice originBytes, int from, int to) {
        this.originBytes = originBytes;

        if (from < 0) {
            throw new IndexOutOfBoundsException(from + " < " + 0);
        }
        if (from > to) {
            throw new IndexOutOfBoundsException(from + " > " + to);
        }
        if (to > originBytes.length()) {
            throw new IndexOutOfBoundsException(to + " > " + "length");
        }

        this.from = from;
        this.to = to;
    }

    @Override
    public int length() {
        return to - from;
    }

    @Override
    public byte byteAt(int index) {
        BoundaryUtils.checkArrayIndexParam(length(), index);
        return originBytes.byteAt(from + index);
    }

    @Override
    public void arraycopy(int srcPos, byte[] dest, int destPos, int length) {
        BoundaryUtils.checkArraycopyParams(length(), srcPos, dest, destPos, length);
        originBytes.arraycopy(this.from + srcPos, dest, destPos, length);
    }

    @Override
    public String toString() {
        return toPrintableString();
    }
}
