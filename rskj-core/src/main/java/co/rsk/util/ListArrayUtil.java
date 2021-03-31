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

package co.rsk.util;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

/**
 * Utility Class
 * basic operations with arrays and lists
 * @since 23/04/2019
 * @author alexbraz
 */
public class ListArrayUtil {
    private ListArrayUtil() {
        // utility class
    }

    /**
     * Converts primitive byte[] to List<Byte>
     *
     * @param primitiveByteArray The primitive array to convert, cannot be null.
     * @return The input byte array as a List<Byte>, never null but can be empty.
     */
    public static List<Byte> asByteList(byte[] primitiveByteArray) {
        Byte[] arrayObj = convertTo(primitiveByteArray);
        return Arrays.asList(arrayObj);
    }

    private static Byte[] convertTo(byte[] primitiveByteArray) {
        Byte[] byteObjectArray = new Byte[primitiveByteArray.length];

        Arrays.setAll(byteObjectArray, n -> primitiveByteArray[n]);

        return byteObjectArray;
    }

    /**
     * @return true if the array is empty or null
     */
    public static boolean isEmpty(@Nullable byte[] array) {
        return array == null || array.length == 0;
    }

    /**
     * @return the length of the array, or zero if null
     */
    public static int getLength(@Nullable byte[] array) {
        if (array == null) {
            return 0;
        }

        return array.length;
    }

    /**
     * @return the same array if not null, or else an empty array
     */
    public static byte[] nullToEmpty(@Nullable byte[] array) {
        if (array == null) {
            return new byte[0];
        }

        return array;
    }

    public static int lastIndexOfSubList(byte[] source, byte[] target) {
        if (source == null || target == null) {
            return -1;
        }
        if (target.length == 0) {
            return source.length;
        }
        if (source.length < target.length) {
            return -1;
        }

        final int max = source.length - target.length;

        for (int i = max; i >= 0; i--) {
            boolean found = true;

            for (int j = 0; j < target.length; j++) {
                if (source[i + j] != target[j]) {
                    found = false;
                    break;
                }
            }

            if (found) {
                return i;
            }
        }

        return -1;
    }
}
