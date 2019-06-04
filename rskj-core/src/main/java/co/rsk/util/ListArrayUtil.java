package co.rsk.util;
/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

import javax.annotation.Nonnull;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;

/**
 * Utility Class
 * basic operations with arrays and lists
 * @since 23/04/2019
 * @author alexbraz
 */
public class ListArrayUtil {

    /**
     * Converts primitive byte[] to List<Byte><br/>
     * @param primitiveByteArray
     * @return
     */
    public static List<Byte> asByteList(final byte[] primitiveByteArray) {
        Byte[] arrayObj = convertTo(primitiveByteArray);
        return Arrays.asList(arrayObj);
    }

    /**
     * @param primitiveByteArray
     * @return
     */
    public static Byte[] convertTo(@Nonnull final byte[] primitiveByteArray){
        Byte[] byteObjectArray = new Byte[primitiveByteArray.length];

        Arrays.setAll(byteObjectArray, n -> primitiveByteArray[n]);

        return byteObjectArray;
    }

    /**
     *  Checks if an array of Object is not empty and not null.
     * @param o
     * @return
     */
    public static boolean isEmpty(final byte[] o){
        return (o == null || o.length == 0);
    }

    /**
     * Checks if an array of primitive bytes is not empty and not null.
     * @param array
     * @return
     *     true if the array is not empty and not null
     */
    public static boolean isNotEmpty(byte[] array){
        return !(array == null || array.length == 0);
    }

    public static int getLength(byte [] array){
        if (array == null) {
            return 0;
        }
        return Array.getLength(array);
    }

    /**
     *
     * @param ops
     * @return
     */
    public static byte[] nullToEmpty(byte[] ops) {
        if(ops == null) {
            return new byte[0];
        }

        return ops;
    }
}
