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

package org.ethereum.jsontestsuite;

import org.bouncycastle.util.encoders.Hex;
import org.ethereum.util.ByteUtil;
import org.json.simple.JSONObject;

/**
 * @author Roman Mandeleil
 * @since 28.06.2014
 */
public class Exec {

    private final byte[] address;
    private final byte[] caller;
    private final byte[] data;
    private final byte[] code;

    private final byte[] gas;
    private final byte[] gasPrice;

    private final byte[] origin;
    private final byte[] value;

    /*
     e.g:
            "address" : "0f572e5295c57f15886f9b263e2f6d2d6c7b5ec6",
            "caller" : "cd1722f3947def4cf144679da39c4c32bdc35681",
            "data" : [
            ],

            "code" : [ 96,0,96,0,96,0,96,0,96,74,51,96,200,92,3,241 ],

            "gas" : 10000,
            "gasPrice" : 100000000000000,
            "origin" : "cd1722f3947def4cf144679da39c4c32bdc35681",
            "value" : 1000000000000000000
   */
    public Exec(JSONObject exec) {

        String address = exec.get("address").toString();
        String caller = exec.get("caller").toString();

        String code = exec.get("code").toString();
        String data = exec.get("data").toString();

        String gas = exec.get("gas").toString();
        String gasPrice = exec.get("gasPrice").toString();
        String origin = exec.get("origin").toString();

        String value = exec.get("value").toString();

        this.address = Hex.decode(address);
        this.caller = Hex.decode(caller);

        if (code != null && code.length() > 2)
            this.code = Hex.decode(code.substring(2));
        else
            this.code = ByteUtil.EMPTY_BYTE_ARRAY;

        if (data != null && data.length() > 2)
            this.data = Hex.decode(data.substring(2));
        else
            this.data = ByteUtil.EMPTY_BYTE_ARRAY;

        this.gas = ByteUtil.bigIntegerToBytes(TestCase.toBigInt(gas));
        this.gasPrice = ByteUtil.bigIntegerToBytes(TestCase.toBigInt(gasPrice));

        this.origin = Hex.decode(origin);
        this.value = ByteUtil.bigIntegerToBytes(TestCase.toBigInt(value));
    }


    public byte[] getAddress() {
        return address;
    }

    public byte[] getCaller() {
        return caller;
    }

    public byte[] getData() {
        return data;
    }

    public byte[] getCode() {
        return code;
    }

    public byte[] getGas() {
        return gas;
    }

    public byte[] getGasPrice() {
        return gasPrice;
    }

    public byte[] getOrigin() {
        return origin;
    }

    public byte[] getValue() {
        return value;
    }


    @Override
    public String toString() {
        return "Exec{" +
                "address=" + ByteUtil.toHexString(address) +
                ", caller=" + ByteUtil.toHexString(caller) +
                ", data=" + ByteUtil.toHexString(data) +
                ", code=" + ByteUtil.toHexString(data) +
                ", gas=" + ByteUtil.toHexString(gas) +
                ", gasPrice=" + ByteUtil.toHexString(gasPrice) +
                ", origin=" + ByteUtil.toHexString(origin) +
                ", value=" + ByteUtil.toHexString(value) +
                '}';
    }
}
