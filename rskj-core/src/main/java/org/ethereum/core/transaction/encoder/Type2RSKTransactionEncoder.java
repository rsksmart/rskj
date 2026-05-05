package org.ethereum.core.transaction.encoder;

import org.bouncycastle.util.BigIntegers;
import org.ethereum.core.Transaction;
import org.ethereum.core.transaction.encoder.util.TransactionEncodingUtils;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.RLP;

import static org.ethereum.core.Transaction.CHAIN_ID_INC;
import static org.ethereum.core.Transaction.LOWER_REAL_V;
import static org.ethereum.util.ByteUtil.EMPTY_BYTE_ARRAY;

//TODO this class is incomplete or not valid yet
public class Type2RSKTransactionEncoder  implements TransactionEncoder {


    @Override
    public byte[] encodeSigned(Transaction transaction) {
        byte[] v;
        byte[] r;
        byte[] s;

        if (transaction.getSignature() != null) {
            v = RLP.encodeByte((byte) (transaction.getChainId() == 0 ? transaction.getSignature().getV() : (transaction.getSignature().getV() - LOWER_REAL_V) + (transaction.getChainId() * 2 + CHAIN_ID_INC)));
            r = RLP.encodeElement(BigIntegers.asUnsignedByteArray(transaction.getSignature().getR()));
            s = RLP.encodeElement(BigIntegers.asUnsignedByteArray(transaction.getSignature().getS()));
        } else {
            v = transaction.getChainId() == 0
                    ? RLP.encodeElement(EMPTY_BYTE_ARRAY)
                    : RLP.encodeByte(transaction.getChainId());
            r = RLP.encodeElement(EMPTY_BYTE_ARRAY);
            s = RLP.encodeElement(EMPTY_BYTE_ARRAY);
        }

        return ByteUtil.merge(transaction.getTypePrefix().toBytes(), encodeType0Fields(transaction, v, r, s));
    }


    @Override
    public byte[] encodeForSigning(Transaction transaction) {
        if (transaction.getMaxPriorityFeePerGas() != null || transaction.getMaxFeePerGas() != null) {
            throw new IllegalStateException("Type 2 RSL transaction requires maxPriorityFeePerGas and maxFeePerGas to be null");
        }

        if (transaction.getChainId() == 0) {
            return encodeType0Fields(transaction, null, null, null);
        }
        return  ByteUtil.merge(transaction.getTypePrefix().toBytes(),
                encodeType0Fields(
                        transaction,
                        RLP.encodeByte(transaction.getChainId()),
                        RLP.encodeElement(EMPTY_BYTE_ARRAY),
                        RLP.encodeElement(EMPTY_BYTE_ARRAY)));
    }

    public byte[] encodeType0Fields(Transaction transaction, byte[] v, byte[] r, byte[] s) {
        byte[] nonce = TransactionEncodingUtils.encodeNonce(transaction.getNonce());
        byte[] gasPrice = RLP.encodeCoinNonNullZero(transaction.getGasPrice());
        byte[] gasLimit = RLP.encodeElement(transaction.getGasLimit());
        byte[] receiveAddress = RLP.encodeRskAddress(transaction.getReceiveAddress());
        byte[] value = RLP.encodeCoinNullZero(transaction.getValue());
        byte[] data = RLP.encodeElement(transaction.getData());

        if (v == null && r == null && s == null) {
            return RLP.encodeList(nonce, gasPrice, gasLimit, receiveAddress, value, data);
        }
        return RLP.encodeList(nonce, gasPrice, gasLimit, receiveAddress, value, data, v, r, s);
    }
}
