package org.ethereum.core.transaction.parser.util;

import co.rsk.util.HexUtils;
import org.ethereum.core.transaction.TransactionType;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;

import java.math.BigInteger;

public final class TransactionTypeRpcParser {

    static final String ERR_INVALID_TX_TYPE = "Invalid transaction type: ";

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
