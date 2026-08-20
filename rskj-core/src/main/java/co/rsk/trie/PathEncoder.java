/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
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

import javax.annotation.Nonnull;

/**
 * Created by martin.medina on 5/04/17.
 */
public class PathEncoder {
    private PathEncoder() { }

    @Nonnull
    public static byte[] encode(byte[] path) {
        if (path == null) {
            throw new IllegalArgumentException("path");
        }

        return encodeBinaryPath(path, 0, path.length);
    }

    /**
     * Same as encode(byte[]), but reads directly from a [offset, limit) sub-range of a
     * shared array instead of requiring the caller to pass an already-copied sub-array.
     * Callers holding a slice of a larger array (e.g. TrieKeySlice) can encode without an
     * intermediate Arrays.copyOfRange -- this is called once per trie node during
     * serialization, so avoiding that allocation+copy matters.
     */
    @Nonnull
    public static byte[] encode(byte[] path, int offset, int limit) {
        if (path == null) {
            throw new IllegalArgumentException("path");
        }

        return encodeBinaryPath(path, offset, limit);
    }

    @Nonnull
    public static byte[] decode(byte[] encoded, int length) {
        if (encoded == null) {
            throw new IllegalArgumentException("encoded");
        }

        return decodeBinaryPath(encoded, length);
    }

    @Nonnull
    // First bit is MOST SIGNIFICANT
    private static byte[] encodeBinaryPath(byte[] path, int start, int limit) {
        int lpath = limit - start;
        int lencoded = calculateEncodedLength(lpath);

        byte[] encoded = new byte[lencoded];
        int nbyte = 0;

        for (int k = 0; k < lpath; k++) {
            int offset = k % 8;
            if (k > 0 && offset == 0) {
                nbyte++;
            }

            if (path[start + k] == 0) {
                continue;
            }

            encoded[nbyte] |= 0x80 >> offset;
        }

        return encoded;
    }

    @Nonnull
    // length is the length in bits. For example ({1},8) is fine
    // First bit is MOST SIGNIFICANT
    private static byte[] decodeBinaryPath(byte[] encoded, int bitlength) {
        byte[] path = new byte[bitlength];

        for (int k = 0; k < bitlength; k++) {
            int nbyte = k / 8;
            int offset = k % 8;

            if (((encoded[nbyte] >> (7 - offset)) & 0x01) != 0) {
                path[k] = 1;
            }
        }

        return path;
    }

    public static int calculateEncodedLength(int keyLength) {
        return keyLength / 8 + (keyLength % 8 == 0 ? 0 : 1);
    }
}
