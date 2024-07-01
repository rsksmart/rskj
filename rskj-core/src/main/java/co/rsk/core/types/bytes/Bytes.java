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

import org.ethereum.util.ByteUtil;
import org.ethereum.util.FastByteComparisons;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Objects;

/**
 * A {@link Bytes} is a readable sequence of <code>byte</code> values. This
 * interface provides uniform, read-only access to many different kinds of
 * <code>byte</code> sequences.
 *
 * <p> This interface does not refine the general contracts of the {@link
 * java.lang.Object#equals(java.lang.Object) equals} and {@link
 * java.lang.Object#hashCode() hashCode} methods.  The result of comparing two
 * objects that implement <tt>Bytes</tt> is therefore, in general,
 * undefined. Each object may be implemented by a different class, and there
 * is no guarantee that each class will be capable of testing its instances
 * for equality with those of the other. It is therefore inappropriate to use
 * arbitrary <tt>Bytes</tt> instances as elements in a set or as keys in
 * a map. </p>
 */
public interface Bytes extends BytesSlice {

    /**
     * Returns an instance of the {@link Bytes} interface, which represents {@code unsafeByteArray}.
     *
     * @return the instance of the {@link Bytes} interface that wraps a provided byte array.
     */
    static Bytes of(@Nullable byte[] unsafeByteArray) {
        if (unsafeByteArray == null) {
            return null;
        }
        return new BytesImpl(unsafeByteArray);
    }

    /**
     * A helper method for printing "nullable" byte arrays.
     *
     * @return {@code valueIfNull}, if {@code byteArray} is {@code null}. Otherwise - {@code Bytes.of(byteArray).toPrintableString()}.
     */
    static String toPrintableString(@Nullable byte[] byteArray, @Nullable String valueIfNull) {
        if (byteArray == null) {
            return valueIfNull;
        }
        return of(byteArray).toPrintableString();
    }

    /**
     * A helper method for printing "nullable" byte arrays.
     *
     * @return {@code "<null>"}, if {@code byteArray} is {@code null}. Otherwise - {@code Bytes.of(byteArray).toPrintableString()}.
     */
    @Nonnull
    static String toPrintableString(@Nullable byte[] byteArray) {
        return toPrintableString(byteArray, "<null>");
    }

    /**
     * A helper method for extracting "unsafe" underlying byte array from the {@code bytes} instance.
     *
     * @return {@code null}, if {@code bytes} is {@code null}. Otherwise - {@code bytes.asUnsafeByteArray()}.
     */
    @Nullable
    static byte[] asUnsafeByteArray(@Nullable Bytes bytes) {
        if (bytes == null) {
            return null;
        }
        return bytes.asUnsafeByteArray();
    }

    static boolean equalBytes(Bytes b1, Bytes b2) {
        if (b1 == null && b2 == null) {
            return true;
        }
        if (b1 == null || b2 == null) {
            return false;
        }
        return FastByteComparisons.equalBytes(b1.asUnsafeByteArray(), b2.asUnsafeByteArray());
    }

    /**
     * Returns an underlying byte array, which is backing this instance. Any mutations that are being done with the bytes
     * of returned array will have direct impact on the byte array that is being wrapped by this instance.
     *
     * @return the wrapped by this instance byte array.
     */
    byte[] asUnsafeByteArray();
}

/**
 * The {@link BytesImpl} class represents a read-only sequence of <code>byte</code> values.
 * <p>
 * Instances of the {@link BytesImpl} class are constant; their values cannot be changed after they
 * are created via the methods that this class provides. But a {@code byteArray} instance itself provided in the constructor
 * is mutable and can be modified outside the class. It's generally a bad idea to mutate a byte array that's being wrapped
 * by an instance of this class, as the idea is to make a byte sequence immutable, which is not the case with the Java
 * built-in {@code byte[]} type.
 * <p>
 * Because {@link BytesImpl} objects are immutable they can be safely used by multiple Java threads, if the wrapped array
 * is not being referenced and modified outside.
 */
class BytesImpl implements Bytes {

    private final byte[] byteArray;

    BytesImpl(@Nonnull byte[] unsafeByteArray) {
        this.byteArray = Objects.requireNonNull(unsafeByteArray);
    }

    @Override
    public int length() {
        return byteArray.length;
    }

    @Override
    public byte byteAt(int index) {
        return byteArray[index];
    }

    @Override
    public byte[] copyArrayOfRange(int from, int to) {
        return Arrays.copyOfRange(byteArray, from, to);
    }

    @Override
    public String toHexString() {
        return ByteUtil.toHexString(byteArray);
    }

    @Override
    public String toHexString(int off, int length) {
        return ByteUtil.toHexString(byteArray, off, length);
    }

    @Nonnull
    @Override
    public byte[] asUnsafeByteArray() {
        return byteArray;
    }

    @Override
    public String toString() {
        return toPrintableString();
    }
}
