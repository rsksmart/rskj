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
 * A {@link BytesSlice} is a subsequence of bytes backed by another broader byte sequence.
 */
public interface BytesSlice extends HexPrintableBytes {

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
     * @param from the initial index of the range to be copied, inclusive
     * @param to the final index of the range to be copied, exclusive.
     *     (This index may lie outside the array.)
     * @return a new array containing the specified range from the original array,
     *     truncated or padded with zeros to obtain the required length
     * @throws ArrayIndexOutOfBoundsException if {@code from < 0}
     *     or {@code from > original.length}
     * @throws IllegalArgumentException if <tt>from &gt; to</tt>
     * @throws NullPointerException if <tt>original</tt> is null
     */
    byte[] copyArrayOfRange(int from, int to);

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
        if (index < 0 || index >= length()) {
            throw new IndexOutOfBoundsException("invalid index: " + index);
        }
        return originBytes.byteAt(from + index);
    }

    @Override
    public byte[] copyArrayOfRange(int from, int to) {
        if (from < 0 || from > to || to > length()) {
            throw new IndexOutOfBoundsException("invalid 'from' and/or 'to': [" + from + ";" + to + ")");
        }
        return originBytes.copyArrayOfRange(this.from + from, this.from + to);
    }

    @Override
    public String toHexString(int off, int length) {
        if (off < 0 || length < 0 || off + length > length()) {
            throw new IndexOutOfBoundsException("invalid 'off' and/or 'length': " + off + "; " + length);
        }
        return originBytes.toHexString(from + off, length);
    }

    @Override
    public String toHexString() {
        return toHexString(0, length());
    }

    @Override
    public String toString() {
        return toPrintableString();
    }
}
