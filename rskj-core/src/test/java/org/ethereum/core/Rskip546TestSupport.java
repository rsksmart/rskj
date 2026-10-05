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
package org.ethereum.core;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;

import java.math.BigInteger;

/**
 * Shared fixtures for RSKIP-546 (Type 1 / Type 2) unit tests via parser ingress.
 */
public final class Rskip546TestSupport {

    public static final byte REGTEST_CHAIN_ID = 33;
    public static final byte[] EMPTY_ACCESS_LIST = new byte[]{(byte) 0xc0};
    public static final RskAddress DEFAULT_RECEIVER =
            new RskAddress("0x1234567890123456789012345678901234567890");
    public static final Coin DEFAULT_GAS_PRICE = Coin.valueOf(1_000_000_000);
    public static final Coin DEFAULT_MAX_PRIORITY = Coin.valueOf(1_000_000_000);
    public static final Coin DEFAULT_MAX_FEE = Coin.valueOf(2_000_000_000);

    private Rskip546TestSupport() {
    }

    public static Transaction unsignedType1() {
        return unsignedType1(REGTEST_CHAIN_ID, DEFAULT_RECEIVER, DEFAULT_GAS_PRICE, new byte[]{0x01}, new byte[0],
                EMPTY_ACCESS_LIST);
    }

    public static Transaction unsignedType1(
            byte chainId,
            RskAddress to,
            Coin gasPrice,
            byte[] data,
            byte[] accessListBytes
    ) {
        return unsignedType1(chainId, to, gasPrice, Coin.ZERO, BigInteger.valueOf(21_000), new byte[]{0x01}, data,
                accessListBytes);
    }

    public static Transaction unsignedType1(
            byte chainId,
            RskAddress to,
            Coin gasPrice,
            byte[] nonce,
            byte[] data,
            byte[] accessListBytes
    ) {
        return unsignedType1(chainId, to, gasPrice, Coin.ZERO, BigInteger.valueOf(21_000), nonce, data, accessListBytes);
    }

    public static Transaction unsignedType1(
            byte chainId,
            RskAddress to,
            Coin gasPrice,
            Coin value,
            BigInteger gasLimit,
            byte[] nonce,
            byte[] data,
            byte[] accessListBytes
    ) {
        byte[][] fields = type1Fields(chainId, to, gasPrice, value, gasLimit, nonce, data, accessListBytes);
        return Transaction.fromRaw(buildRawType1Bytes(fields));
    }

    public static Transaction unsignedType2() {
        return unsignedType2(
                REGTEST_CHAIN_ID, DEFAULT_RECEIVER, DEFAULT_MAX_PRIORITY, DEFAULT_MAX_FEE, new byte[]{0x01},
                new byte[0], EMPTY_ACCESS_LIST);
    }

    public static Transaction unsignedType2(
            byte chainId,
            RskAddress to,
            Coin maxPriorityFeePerGas,
            Coin maxFeePerGas,
            byte[] data,
            byte[] accessListBytes
    ) {
        return unsignedType2(chainId, to, maxPriorityFeePerGas, maxFeePerGas, Coin.ZERO, BigInteger.valueOf(21_000),
                new byte[]{0x01}, data, accessListBytes);
    }

    public static Transaction unsignedType2(
            byte chainId,
            RskAddress to,
            Coin maxPriorityFeePerGas,
            Coin maxFeePerGas,
            byte[] nonce,
            byte[] data,
            byte[] accessListBytes
    ) {
        return unsignedType2(chainId, to, maxPriorityFeePerGas, maxFeePerGas, Coin.ZERO, BigInteger.valueOf(21_000),
                nonce, data, accessListBytes);
    }

    public static Transaction unsignedType2(
            byte chainId,
            RskAddress to,
            Coin maxPriorityFeePerGas,
            Coin maxFeePerGas,
            Coin value,
            BigInteger gasLimit,
            byte[] nonce,
            byte[] data,
            byte[] accessListBytes
    ) {
        byte[][] fields = type2Fields(chainId, to, maxPriorityFeePerGas, maxFeePerGas, value, gasLimit, nonce, data,
                accessListBytes);
        return Transaction.fromRaw(buildRawType2Bytes(fields));
    }

    public static byte[] buildRawType1Bytes(byte[]... encodedFields) {
        return ByteUtil.merge(new byte[]{TransactionType.TYPE_1.getByteCode()}, RLP.encodeList(encodedFields));
    }

    public static byte[] buildRawType2Bytes(byte[]... encodedFields) {
        return ByteUtil.merge(new byte[]{TransactionType.TYPE_2.getByteCode()}, RLP.encodeList(encodedFields));
    }

    private static byte[][] type1Fields(
            byte chainId,
            RskAddress to,
            Coin gasPrice,
            Coin value,
            BigInteger gasLimit,
            byte[] nonce,
            byte[] data,
            byte[] accessListBytes
    ) {
        return new byte[][]{
                RLP.encodeByte(chainId),
                RLP.encodeElement(nonce == null ? new byte[]{0x01} : nonce),
                RLP.encodeCoinNonNullZero(gasPrice),
                RLP.encodeElement(gasLimit.toByteArray()),
                RLP.encodeRskAddress(to),
                RLP.encodeBigInteger(value.asBigInteger()),
                RLP.encodeElement(data == null ? new byte[0] : data),
                accessListBytes == null ? EMPTY_ACCESS_LIST : accessListBytes,
                RLP.encodeElement(null),
                RLP.encodeElement(null),
                RLP.encodeElement(null)
        };
    }

    private static byte[][] type2Fields(
            byte chainId,
            RskAddress to,
            Coin maxPriorityFeePerGas,
            Coin maxFeePerGas,
            Coin value,
            BigInteger gasLimit,
            byte[] nonce,
            byte[] data,
            byte[] accessListBytes
    ) {
        return new byte[][]{
                RLP.encodeByte(chainId),
                RLP.encodeElement(nonce == null ? new byte[]{0x01} : nonce),
                RLP.encodeCoinNonNullZero(maxPriorityFeePerGas),
                RLP.encodeCoinNonNullZero(maxFeePerGas),
                RLP.encodeElement(gasLimit.toByteArray()),
                RLP.encodeRskAddress(to),
                RLP.encodeBigInteger(value.asBigInteger()),
                RLP.encodeElement(data == null ? new byte[0] : data),
                accessListBytes == null ? EMPTY_ACCESS_LIST : accessListBytes,
                RLP.encodeElement(null),
                RLP.encodeElement(null),
                RLP.encodeElement(null)
        };
    }
}
