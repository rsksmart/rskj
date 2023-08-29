package org.ethereum.rpc.validation;

import co.rsk.util.HexUtils;
import org.ethereum.rpc.exception.RskJsonRpcRequestException;

public class TransactionHashValidator {
    private final static int TRANSACTION_HASH_BYTE_LENGTH = 32;
    public final static String INVALID_HEX_MESSAGE = "Invalid transaction hash format. ";
    public final static String INVALID_LENGTH_MESSAGE = "Invalid transaction hash: incorrect length.";
    private TransactionHashValidator() {

    }

    public static void isValid(String transactionHash) {
        byte[] transactionHashBytes;

        try {
            transactionHashBytes = HexUtils.stringHexToByteArray(transactionHash);
        } catch (Exception e) {
            throw RskJsonRpcRequestException.invalidParamError(INVALID_HEX_MESSAGE + e.getMessage());
        }

        if (TRANSACTION_HASH_BYTE_LENGTH != transactionHashBytes.length) {
            throw RskJsonRpcRequestException.invalidParamError(INVALID_LENGTH_MESSAGE);
        }
    }
}
