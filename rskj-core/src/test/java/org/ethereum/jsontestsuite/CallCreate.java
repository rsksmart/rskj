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

import com.fasterxml.jackson.databind.JsonNode;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.util.ByteUtil;

/**
 * @author Roman Mandeleil
 * @since 28.06.2014
 */
public class CallCreate {

    private final byte[] data;
    private final byte[] destination;
    private final long  gasLimit;
    private final byte[] value;

/* e.g.
        "data" : [
                ],
        "destination" : "cd1722f3947def4cf144679da39c4c32bdc35681",
        "gasLimit" : 9792,
        "value" : 74
*/

    public CallCreate(JsonNode callCreateJSON) {

        String data = callCreateJSON.get("data").asText();
        String destination = callCreateJSON.get("destination").asText();
        String gasLimit = callCreateJSON.get("gasLimit").asText();
        String value = callCreateJSON.get("value").asText();

        if (data != null && data.length() > 2)
            this.data = Hex.decode(data.substring(2));
        else
            this.data = ByteUtil.EMPTY_BYTE_ARRAY;

        this.destination = Hex.decode(destination);
        this.gasLimit = TestingCase.toBigInt(gasLimit).longValueExact();
        this.value = ByteUtil.bigIntegerToBytes(TestingCase.toBigInt(value));
    }

    public byte[] getData() {
        return data;
    }

    public byte[] getDestination() {
        return destination;
    }

    public long  getGasLimit() {
        return gasLimit;
    }

    public byte[] getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "CallCreate{" +
                "data=" + ByteUtil.toHexString(data) +
                ", destination=" + ByteUtil.toHexString(destination) +
                ", gasLimit=" + Long.toHexString(gasLimit) +
                ", value=" + ByteUtil.toHexString(value) +
                '}';
    }
}
