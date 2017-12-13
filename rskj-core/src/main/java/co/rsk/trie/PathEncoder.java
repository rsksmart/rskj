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
class PathEncoder {

    private static final String INVALID_ARITY = "Invalid arity";

    private PathEncoder() { }

    @Nonnull
    static byte[] encode(byte[] path, int arity) {
        if (path == null) {
            throw new IllegalArgumentException("path");
        }

        if (arity == 2) {
            return encodeBinaryPath(path);
        }

        if (arity == 16) {
            return encodeHexadecimalPath(path);
        }

        throw new IllegalArgumentException(INVALID_ARITY);
    }

    @Nonnull
    static byte[] decode(byte[] encoded, int arity, int length) {
        if (encoded == null) {
            throw new IllegalArgumentException("encoded");
        }

        if (arity == 2) {
            return decodeBinaryPath(encoded, length);
        }

        if (arity == 16) {
            return decodeHexadecimalPath(encoded, length);
        }

        throw new IllegalArgumentException(INVALID_ARITY);
    }

    @Nonnull
    private static byte[] encodeBinaryPath(byte[] path) {
        int lpath = path.length;
        int lencoded = lpath / 8 + (lpath % 8 == 0 ? 0 : 1);

        byte[] encoded = new byte[lencoded];
        int nbyte = 0;

        for (int k = 0; k < lpath; k++) {
            int offset = k % 8;
            if (k > 0 && offset == 0) {
                nbyte++;
            }

            if (path[k] == 0) {
                continue;
            }

            encoded[nbyte] |= 0x80 >> offset;
        }

        return encoded;
    }

    @Nonnull
    private static byte[] decodeBinaryPath(byte[] encoded, int length) {
        byte[] path = new byte[length];

        for (int k = 0; k < length; k++) {
            int nbyte = k / 8;
            int offset = k % 8;

            if (((encoded[nbyte] >> (7 - offset)) & 0x01) != 0) {
                path[k] = 1;
            }
        }

        return path;
    }

    private static byte[] encodeHexadecimalPath(byte[] path) {
        int lpath = path.length;
        int lencoded = lpath / 2 + (lpath % 2 == 0 ? 0 : 1);

        byte[] encoded = new byte[lencoded];
        int nbyte = 0;

        for (int k = 0; k < lpath; k++) {
            if (k > 0 && k % 2 == 0) {
                nbyte++;
            }

            if (path[k] == 0) {
                continue;
            }
            
            int offset = k % 2;
            encoded[nbyte] |= (path[k] & 0x0f) << ((1 - offset) * 4);
        }

        return encoded;
    }

    private static byte[] decodeHexadecimalPath(byte[] encoded, int length) {
        byte[] path = new byte[length];

        for (int k = 0; k < length; k++) {
            int nbyte = k / 2;
            int offset = k % 2;

            int value = (encoded[nbyte] >> ((1 - offset) * 4)) & 0x0f;
            path[k] = (byte)value;
        }

        return path;
    }
}
