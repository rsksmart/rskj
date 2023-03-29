package org.ethereum.vm.aa;

import co.rsk.util.HexUtils;
import org.ethereum.core.Transaction;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.DynamicStruct;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;

import static org.apache.commons.lang3.ArrayUtils.nullToEmpty;

public class AATransactionABI extends DynamicStruct {

    public static final String MAGIC_SUCCESS = "0x0aee9f17";

    public AATransactionABI(byte txType, String sender, String receiver, byte[] gasLimit, BigInteger gasPrice, byte[] nonce,
                            BigInteger value, byte[] data, byte[] rawsignature) {
        super(
                new org.web3j.abi.datatypes.generated.Uint256(new BigInteger(new byte[]{txType})),
                new org.web3j.abi.datatypes.Address(sender),
                new org.web3j.abi.datatypes.Address(receiver),
                new org.web3j.abi.datatypes.generated.Uint256(HexUtils.stringHexToBigInteger(HexUtils.toJsonHex(gasLimit))),
                new org.web3j.abi.datatypes.generated.Uint256(gasPrice),
                new org.web3j.abi.datatypes.generated.Uint256(new BigInteger(nonce)),
                new org.web3j.abi.datatypes.generated.Uint256(value),
                new org.web3j.abi.datatypes.DynamicBytes(data),
                new org.web3j.abi.datatypes.DynamicBytes(rawsignature)
        );
    }


    public static byte[] getValidateTxData(Transaction tx) {
        final AATransactionABI aaTransaction = new AATransactionABI(tx.getType(),
                tx.getSender().toHexString(),
                tx.getReceiveAddress().toHexString(),
                tx.getGasLimit(),
                tx.getGasPrice().asBigInteger(),
                tx.getNonce(),
                tx.getValue().asBigInteger(),
                tx.getData(),
                tx.getRawsignature());

        Bytes32 hash = new Bytes32(tx.getRawHash().getBytes());

        final String encoded = FunctionEncoder.encode(
                new Function("validateTransaction",
                        Arrays.<Type>asList(hash, aaTransaction),
                        Collections.emptyList()
                )
        );
        return HexUtils.stringHexToByteArray(encoded);
    }

    // AA - Depends on the type of tx it transform the data
    public static byte[] getExecutionTxData(Transaction tx) {
        if (tx.getType() == Transaction.AA_TYPE) {
            final String encoded = FunctionEncoder.encode(
                    new Function("executeTransaction",
                            Arrays.<Type>asList(new AATransactionABI(tx.getType(),
                                    tx.getSender().toHexString(),
                                    tx.getReceiveAddress().toHexString(),
                                    tx.getGasLimit(),
                                    tx.getGasPrice().asBigInteger(),
                                    tx.getNonce(),
                                    tx.getValue().asBigInteger(),
                                    tx.getData(),
                                    tx.getRawsignature())),
                            Collections.emptyList()
                    )
            );
            return HexUtils.stringHexToByteArray(encoded);
        }
        return nullToEmpty(tx.getData());
    }
}
