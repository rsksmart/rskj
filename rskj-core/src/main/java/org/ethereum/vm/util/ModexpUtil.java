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


package org.ethereum.vm.util;

import org.ethereum.core.CallTransaction;

import java.math.BigInteger;
import java.util.Arrays;

public class ModexpUtil {

    private static final String FUNC_JSON = "{\n" +
            "   \"constant\":false, \n" +
            "   \"inputs\":[{\"name\":\"b\",\"type\":\"bytes\"}, \n" +
            "               {\"name\":\"e\",\"type\":\"bytes\"}, \n" +
            "               {\"name\":\"m\",\"type\":\"bytes\"}], \n" +
            "    \"name\":\"modexp\", \n" +
            "   \"outputs\":[{\"name\":\"ret\",\"type\":\"bytes\"}], \n" +
            "    \"type\":\"function\" \n" +
            "}\n";

    private static final int BASE = 0;
    private static final int EXPONENT = 1;
    private static final int MODULUS = 2;

    private static CallTransaction.Function params = CallTransaction.Function.fromJsonInterface(FUNC_JSON);

    private Object[] decoded;

    public ModexpUtil(byte[] data) {
        this.decoded = params.decode(data);
    }

    public BigInteger base() {
        return new BigInteger(1, getValue(BASE));
    }

    public BigInteger exp() {
        return new BigInteger(1, getValue(EXPONENT));
    }

    public BigInteger mod() {
        return new BigInteger(1, getValue(MODULUS));
    }

    public int magnitude() {
        return getValue(MODULUS).length;
    }

    public byte[] encodeResult(BigInteger result) {
        byte[] ret;
        byte[] resArr = result.toByteArray();

        int length = this.magnitude();
        if (resArr.length == length + 1) {
            ret = Arrays.copyOfRange(resArr, 1, resArr.length);
        } else {
            ret = new byte[length];
            System.arraycopy(resArr, 0, ret, ret.length - resArr.length, resArr.length);
        }
        return params.encodeOutputs(ret);
    }

    private byte[] getValue(int position) {
        return (byte[]) decoded[position];
    }
}