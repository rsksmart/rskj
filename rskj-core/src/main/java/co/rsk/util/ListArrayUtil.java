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
     * @return true if the array is not empty and not null
     */
    public static boolean isNotEmpty(@Nullable byte[] array) {
        return !isEmpty(array);
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
}
