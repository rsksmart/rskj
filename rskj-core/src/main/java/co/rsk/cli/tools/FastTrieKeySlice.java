package co.rsk.cli.tools;

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


import co.rsk.trie.PathEncoder;

import javax.swing.*;
import java.util.Arrays;

/**
 * An immutable BUT NOT THREAD-SAFE slice of a trie key.
 * The key object is shared between appends, so it's NOT suitable for storage, nor for
 *  multithreading. IT's ONLY for using while travesing a trie.
 * Sub-slices share array references, so external sources are copied and the internal array is not exposed.
 */
public class FastTrieKeySlice {
    // Always store in maximally expanded format
    private final byte[] expandedKey;
    private final short offset;
    private final short limit;

    public FastTrieKeySlice(byte[] expandedKey, int offset, int limit) {
        this.expandedKey = expandedKey;
        this.offset = (short) offset;
        this.limit = (short) limit;
    }

    public FastTrieKeySlice clone() {
        return new FastTrieKeySlice(Arrays.copyOfRange(expandedKey, offset, limit),0,length());
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

    public FastTrieKeySlice slice(int from, int to) {
        if (from==to) // SDL performance fix
            return empty();
        if (from < 0) {
            throw new IllegalArgumentException("The start position must not be lower than 0");
        }

        if (from > to) {
            throw new IllegalArgumentException("The start position must not be greater than the end position");
        }

        short newOffset = (short) (offset + from);
        if (newOffset > limit) {
            throw new IllegalArgumentException("The start position must not exceed the key length");
        }

        short newLimit = (short) (offset + to);
        if (newLimit > limit) {
            throw new IllegalArgumentException("The end position must not exceed the key length");
        }

        //
        return new FastTrieKeySlice(expandedKey, newOffset, newLimit);
    }

    public FastTrieKeySlice commonPath(FastTrieKeySlice other) {
        short maxCommonLengthPossible = (short) Math.min(length(), other.length());
        for (int i = 0; i < maxCommonLengthPossible; i++) {
            if (get(i) != other.get(i)) {
                return slice(0, i);
            }
        }


        return slice((short) 0, maxCommonLengthPossible);
    }
    public FastTrieKeySlice commonPath(co.rsk.trie.TrieKeySlice other) {
        int maxCommonLengthPossible = Math.min(length(), other.length());
        for (int i = 0; i < maxCommonLengthPossible; i++) {
            if (get(i) != other.get(i)) {
                return slice(0, i);
            }
        }


        return slice(0, maxCommonLengthPossible);
    }

    public FastTrieKeySlice appendBit(byte implicitByte) {
        int length = length();
        byte[] newExpandedKey = expandedKey;
        newExpandedKey[length] = implicitByte;
        return new FastTrieKeySlice(newExpandedKey,offset,limit+1);
    }

    public FastTrieKeySlice append(FastTrieKeySlice childSharedPath) {
        int childSharedPathLength = childSharedPath.length();
        if (childSharedPathLength==0) return this;
        int length = length();

        int newLength = length + childSharedPathLength;
        byte[] newExpandedKey = expandedKey;
        System.arraycopy(
                childSharedPath.expandedKey, childSharedPath.offset,
                newExpandedKey, length +offset, childSharedPathLength
        );
        return new FastTrieKeySlice(newExpandedKey, offset, offset+newLength);
    }
    public FastTrieKeySlice append(co.rsk.trie.TrieKeySlice childSharedPath) {
        int childSharedPathLength = childSharedPath.length();
        if (childSharedPathLength==0) return this;
        int length = length();
        int newLength = length + childSharedPathLength;
        byte[] newExpandedKey = expandedKey;
        for(int i=0;i<childSharedPathLength;i++)
            newExpandedKey[offset+length+i]=childSharedPath.get(i);
        return new FastTrieKeySlice(newExpandedKey, offset, offset+newLength);
    }

    /**
         * Rebuild a shared path as [...this, implicitByte, ...childSharedPath]
         */
    public FastTrieKeySlice rebuildSharedPath(byte implicitByte, FastTrieKeySlice childSharedPath) {
        int length = length();
        int childSharedPathLength = childSharedPath.length();
        int newLength = length + 1 + childSharedPathLength;
        byte[] newExpandedKey = Arrays.copyOfRange(expandedKey, offset, offset + newLength);
        newExpandedKey[length] = implicitByte;
        System.arraycopy(
                childSharedPath.expandedKey, childSharedPath.offset,
                newExpandedKey, length + 1, childSharedPathLength
        );
        return new FastTrieKeySlice(newExpandedKey, 0, newExpandedKey.length);
    }

    public FastTrieKeySlice leftPad(int paddingLength) {
        if (paddingLength == 0) {
            return this;
        }
        int currentLength = length();
        byte[] paddedExpandedKey = new byte[currentLength + paddingLength];
        System.arraycopy(expandedKey, offset, paddedExpandedKey, paddingLength, currentLength);
        return new FastTrieKeySlice(paddedExpandedKey, 0, paddedExpandedKey.length);
    }

    public static FastTrieKeySlice fromKey(byte[] key) {
        byte[] expandedKey = PathEncoder.decode(key, key.length * 8);
        return new FastTrieKeySlice(expandedKey, 0, expandedKey.length);
    }

    public static FastTrieKeySlice fromEncoded(byte[] src, int offset, int keyLength, int encodedLength) {
        // TODO(mc) avoid copying by passing the indices to PathEncoder.decode
        byte[] encodedKey = Arrays.copyOfRange(src, offset, offset + encodedLength);
        byte[] expandedKey = PathEncoder.decode(encodedKey, keyLength);
        return new FastTrieKeySlice(expandedKey, 0, expandedKey.length);
    }

    static FastTrieKeySlice emptyTrie = new FastTrieKeySlice(new byte[0], 0, 0);
    public static FastTrieKeySlice empty() {
        return emptyTrie;
    }

    // Start aways with a vector with enough capacity to append keys
    public static FastTrieKeySlice emptyWithCapacity() {
        int maxSize = (1+30+1+42)*8;
        return new FastTrieKeySlice(new byte[maxSize], 0, 0);
    }
}
