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

import co.rsk.util.HexUtils;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;

import java.math.BigInteger;

public final class TransactionTypeRpcParser {

    private static final String ERR_INVALID_TX_TYPE = "Invalid transaction type: ";

    private TransactionTypeRpcParser() {}

    public static TransactionType fromHex(String hex) {
        try {
            if (hex == null) {
                //check
                //throw RskJsonRpcRequestException.invalidParamError(ERR_INVALID_TX_TYPE + hex);
                return TransactionType.LEGACY;
            }
            BigInteger value = HexUtils.strHexOrStrNumberToBigInteger(hex);
            if (value.signum() < 0 || value.compareTo(BigInteger.valueOf(TransactionType.MAX_TYPE_VALUE)) > 0) {
                throw RskJsonRpcRequestException.invalidParamError(ERR_INVALID_TX_TYPE + hex);
            }

            TransactionType txType = TransactionType.fromByte(value.byteValue());
            if (txType == null) {
                throw RskJsonRpcRequestException.invalidParamError(ERR_INVALID_TX_TYPE + hex);
            }
            if (txType == TransactionType.LEGACY) {
                throw RskJsonRpcRequestException.invalidParamError(
                        ERR_INVALID_TX_TYPE + hex +
                                "; explicit type 0x00 is not allowed, omit the type field for legacy transactions");
            }

            return txType;
        } catch (RskJsonRpcRequestException e) {
            throw e;
        } catch (Exception ex) {
            throw RskJsonRpcRequestException.invalidParamError(ERR_INVALID_TX_TYPE + hex, ex);
        }
    }
}
