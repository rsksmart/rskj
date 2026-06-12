/*
 * This file is part of RskJ
 * Copyright (C) 2026 RSK Labs Ltd.
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
package org.ethereum.core.transaction.parser.util;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.util.HexUtils;
import org.ethereum.util.RLPList;

import java.math.BigInteger;
import java.util.Optional;
import java.util.function.Supplier;

public final class CommonParsingUtils {

    private CommonParsingUtils() {}

    public static void requireFieldCount(RLPList txFields, int expected, String typeName) {
        if (txFields.size() != expected) {
            throw new IllegalArgumentException(typeName + " transaction must have exactly " + expected + " elements");
        }
    }

    public static byte[] nullToEmpty(byte[] value) {
        return value == null ? new byte[0] : value;
    }

    public static  byte[] parseHexData(String data) {
        if (data != null) {
            String normalized = data.startsWith("0x") ? data.substring(2) : data;
            return HexUtils.stringHexToByteArray(normalized);
        }
        return new byte[0];
    }

    /**
     * Null address means contract creation transaction.
     */
    public static  RskAddress parseAddress(String address) {
        if(address != null){
            return new RskAddress(HexUtils.stringHexToByteArray(address));
        }
        return RskAddress.nullAddress();
    }

    public static  RskAddress defaultAddress(RskAddress address) {
        return address == null ? RskAddress.nullAddress() : address;
    }

    public static  BigInteger parseBigInteger(String value, Supplier<BigInteger> getDefaultValue) {
        return Optional.ofNullable(value).map(HexUtils::strHexOrStrNumberToBigInteger).orElseGet(getDefaultValue);
    }

    public static  Coin parseCoin(String value) {
        if (value == null || value.isEmpty()) {
            return Coin.ZERO;
        }
        return new Coin(HexUtils.strHexOrStrNumberToBigInteger(value));

    }

    public static  Coin defaultValue(Coin value) {
        return value == null ? Coin.ZERO : value;
    }
}
