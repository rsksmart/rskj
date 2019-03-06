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

import java.util.Arrays;

/**
 * An immutable slice of a trie key.
 * Sub-slices share array references, so external sources are copied and the internal array is not exposed.
 */
public class TrieKeySlice {
    private final byte[] expandedKey;
    private final int offset;
    private final int limit;

    private TrieKeySlice(byte[] expandedKey, int offset, int limit) {
        this.expandedKey = expandedKey;
        this.offset = offset;
        this.limit = limit;
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
        if (from < 0) {
            throw new IllegalArgumentException("The start position must not be lower than 0");
        }

        if (from > to) {
            throw new IllegalArgumentException("The start position must not be greater than the end position");
        }

        int newOffset = offset + from;
        if (newOffset > limit) {
            throw new IllegalArgumentException("The start position must not exceed the key length");
        }

        int newLimit = offset + to;
        if (newLimit > limit) {
            throw new IllegalArgumentException("The end position must not exceed the key length");
        }

        return new TrieKeySlice(expandedKey, newOffset, newLimit);
    }

    public int lengthOfCommonPath(int position, byte[] sharedPath) {
        int maxCommonLengthPossible = Math.min(length() - position, sharedPath.length);
        for (int i = 0; i < maxCommonLengthPossible; i++) {
            if (sharedPath[i] != get(position + i)) {
                return i;
            }
        }

        return maxCommonLengthPossible;
    }

    public static TrieKeySlice fromKey(byte[] key) {
        byte[] expandedKey = PathEncoder.decode(key, key.length * 8);
        return new TrieKeySlice(expandedKey, 0, expandedKey.length);
    }
}
