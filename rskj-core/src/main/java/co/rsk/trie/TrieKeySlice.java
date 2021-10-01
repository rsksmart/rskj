/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
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
package co.rsk.trie;

import com.google.common.annotations.VisibleForTesting;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.ByteUtil;

import java.util.Arrays;

/**
 * An immutable slice of a trie key.
 * Sub-slices share array references, so external sources are copied and the internal array is not exposed.
 */
public class TrieKeySlice {
    private final byte[] expandedKey;
    private final int offset;
    private final int limit;

    static private final int maxLength = Short.MAX_VALUE;

    private  TrieKeySlice(byte[] expandedKey, int offset, int limit) {
        this.expandedKey = expandedKey;
        this.offset = offset;
        this.limit = limit;
    }

    public TrieKeySlice clone() {
        return new TrieKeySlice(Arrays.copyOfRange(expandedKey, offset, limit),0,length());
    }

    public int length() {
        return limit - offset;
    }

    public byte get(int i) {
        return expandedKey[offset + i];
    }

    public byte[] encode() {
        // TODO(mc) avoid copying by passing the indices to PathEncoder.encode
        return PathEncoder.encode(Arrays.copyOfRange(expandedKey, offset, limit));
    }

    public TrieKeySlice slice(int from, int to) {

       // Return empty if bounds match. This is a huge performance gain
        if (from==to) {
            return empty();
        }
        if (from < 0) {
            throw new IllegalArgumentException("The start position must not be lower than 0");
        }

        if (from > to) {
            throw new IllegalArgumentException("The start position must not be greater than the end position");
        }

        int newOffset = (offset + from);
        if (newOffset > limit) {
            throw new IllegalArgumentException("The start position must not exceed the key length");
        }

        int newLimit = (offset + to);
        if (newLimit > limit) {
            throw new IllegalArgumentException("The end position must not exceed the key length");
        }

        return new TrieKeySlice(expandedKey, newOffset, newLimit);
    }

    // This operation is frequent when traversing the trie. It must be very fast.
    public TrieKeySlice commonPath(TrieKeySlice other) {
        int maxCommonLengthPossible = Math.min(length(), other.length());
        for (int i = 0; i < maxCommonLengthPossible; i++) {
            if (get(i) != other.get(i)) {
                return slice(0, i);
            }
        }

        return slice(0, maxCommonLengthPossible);
    }

    public TrieKeySlice appendBit(byte implicitByte) {
        int length = length();
        int newLength = length + 1;
        byte[] newExpandedKey = new byte[newLength];
        System.arraycopy(expandedKey, offset, newExpandedKey, 0, length);
        newExpandedKey[length] = implicitByte;
        return new TrieKeySlice(newExpandedKey,0,newLength);
    }

    public TrieKeySlice append(TrieKeySlice childSharedPath) {
        int childSharedPathLength = childSharedPath.length();
        if (childSharedPathLength==0) return this;
        int length = length();
        int newLength = length + childSharedPathLength;
        if (newLength>maxLength)
            throw new IllegalArgumentException("Concatenation leads to a too large key");

        byte[] newExpandedKey = new byte[newLength];
        System.arraycopy(expandedKey, offset, newExpandedKey, 0, length);

        for(int i=0;i<childSharedPathLength;i++)
            newExpandedKey[length+i]=childSharedPath.get(i);
        return new TrieKeySlice(newExpandedKey, 0, newLength);
    }

    public boolean equals(Object other) {
        if (!(other instanceof TrieKeySlice)) {
            return false;
        }
        TrieKeySlice otherKey = (TrieKeySlice) other;
        if (this.length()!=otherKey .length()) {
            return false;
        }
        for(int i=0;i<length();i++)
            if (get(i)!=otherKey.get(i))
                return false;
        return true;
    }

    /**
     * Rebuild a shared path as [...this, implicitByte, ...childSharedPath]
     * This operation is common when modifying the trie. It must not be slow.
     */
    public TrieKeySlice rebuildSharedPath(byte implicitByte, TrieKeySlice childSharedPath) {
        int length = length();
        int childSharedPathLength = childSharedPath.length();
        int newLength = length + 1 + childSharedPathLength;
        if (newLength>maxLength)
            throw new IllegalArgumentException("Concatenation leads to a too large key");

        byte[] newExpandedKey = Arrays.copyOfRange(expandedKey, offset, offset + newLength);
        newExpandedKey[length] = implicitByte;
        System.arraycopy(
                childSharedPath.expandedKey, childSharedPath.offset,
                newExpandedKey, length + 1, childSharedPathLength
        );
        return new TrieKeySlice(newExpandedKey, 0, newExpandedKey.length);
    }

    public TrieKeySlice leftPad(int paddingLength) {
        if (paddingLength == 0) {
            return this;
        }
        int currentLength = length();
        int newLength = currentLength + paddingLength;
        if (newLength>maxLength)
            throw new IllegalArgumentException("Concatenation leads to a too large key");

        byte[] paddedExpandedKey = new byte[newLength];
        System.arraycopy(expandedKey, offset, paddedExpandedKey, paddingLength, currentLength);
        return new TrieKeySlice(paddedExpandedKey, 0, paddedExpandedKey.length);
    }

    // Creates a TrieKeySlice from a key (a bit-compressed array, also called encoded key).
    // The number of bits in this key is always multiple of 8.
    public static TrieKeySlice fromKey(byte[] key) {
        byte[] expandedKey = PathEncoder.decode(key, key.length * 8);
        return new TrieKeySlice(expandedKey, 0, expandedKey.length);
    }

    // Creates a TrieKeySlice from a an encoded key (src).
    // It receives a certain byte offset to start from the encoded key src,
    // but the resulting TrieKeySlice can have any bit length, because keyLength bit granularity.
    // encodedLength is a byte length of the encoded src.
    // Precondition: keyLength <= (encodedLength-offset)*8
    public static TrieKeySlice fromEncoded(byte[] src, int offset, int keyLength, int encodedLength) {
        if (keyLength <= (encodedLength-offset)*8)
            throw new IllegalArgumentException("Illegal keyLength");

        if (keyLength>TrieKeySlice.maxLength)
            throw new IllegalArgumentException("Key too long");

        // TODO(mc) avoid copying by passing the indices to PathEncoder.decode
        byte[] encodedKey = Arrays.copyOfRange(src, offset, offset + encodedLength);
        byte[] expandedKey = PathEncoder.decode(encodedKey, keyLength);
        return new TrieKeySlice(expandedKey, 0, expandedKey.length);
    }

    @VisibleForTesting
    // This constructor is ONLY for testing the object
    // size in memory. It should never be used in production code.
    public static TrieKeySlice nullFilledObject() {
        return new TrieKeySlice(null,0,0);
    }

        @VisibleForTesting
    // This method is marked as public because any application that uses the rskj jar
    // as a library to access the RSK state needs to instantiate TrieKeySlices.
    public static TrieKeySlice fromExpanded(byte[] expandedKey, int offset, int limit) {
        if (limit > expandedKey.length)
            throw new IllegalArgumentException("Invalid limit");

        if ((offset > expandedKey.length) || (offset>limit))
            throw new IllegalArgumentException("Invalid offset");

        int keyLength = limit-offset;
        if (keyLength>TrieKeySlice.maxLength)
            throw new IllegalArgumentException("Key too long");

        return new TrieKeySlice(expandedKey, offset, limit);
    }

    static TrieKeySlice emptyTrie = new TrieKeySlice(new byte[0], 0, 0);

    public static TrieKeySlice empty() {
        return emptyTrie;
    }

    // Start aways with a vector with enough capacity to append keys
    public static TrieKeySlice emptyWithCapacity() {
        // This is the maximum size of a trie key in the current RSK Unitrie
        int maxSize = (1+30+1+42)*8;
        return new TrieKeySlice(new byte[maxSize], 0, 0);
    }
}
